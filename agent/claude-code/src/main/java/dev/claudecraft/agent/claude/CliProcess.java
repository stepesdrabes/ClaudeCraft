package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.json.Json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class CliProcess {
    private static final int STDERR_LINES = 20;

    interface Handler {
        void onMessage(Json message);

        CompletableFuture<Json> onRequest(String requestId, Json request);

        void onCancel(String requestId);

        void onExit(int exitCode, String stderr);
    }

    private final Process process;
    private final Writer stdin;
    private final Handler handler;
    private final Map<String, CompletableFuture<Json>> pending = new ConcurrentHashMap<>();
    private final AtomicInteger nextRequestId = new AtomicInteger();
    private final Deque<String> stderr = new ArrayDeque<>();

    private CliProcess(Process process, Handler handler) {
        this.process = process;
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.handler = handler;
    }

    static CliProcess start(ClaudeCli cli, List<String> args, Path workingDirectory, Handler handler) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(cli.command(args)).directory(workingDirectory.toFile());
        builder.environment().clear();
        builder.environment().putAll(cli.environment());
        CliProcess cliProcess = new CliProcess(builder.start(), handler);
        daemon("claudecraft-cli-out", cliProcess::readStdout);
        daemon("claudecraft-cli-err", cliProcess::readStderr);
        return cliProcess;
    }

    synchronized void send(Json message) {
        try {
            stdin.write(message.toString());
            stdin.write('\n');
            stdin.flush();
        } catch (IOException e) {
            process.destroy();
        }
    }

    CompletableFuture<Json> request(Json request) {
        String id = "req_" + nextRequestId.incrementAndGet();
        CompletableFuture<Json> response = new CompletableFuture<>();
        pending.put(id, response);
        send(Json.object().put("type", "control_request").put("request_id", id).put("request", request));
        return response;
    }

    boolean isAlive() {
        return process.isAlive();
    }

    void close() {
        try {
            stdin.close();
        } catch (IOException e) {
            process.destroy();
            return;
        }
        daemon("claudecraft-cli-close", () -> {
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroy();
            } catch (InterruptedException e) {
                process.destroy();
            }
        });
    }

    private void readStdout() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (!line.trim().isEmpty()) route(line);
            }
        } catch (IOException ignored) {
        }
        int exitCode = awaitExit();
        IllegalStateException closed = new IllegalStateException("Claude Code exited");
        pending.values().forEach(future -> future.completeExceptionally(closed));
        handler.onExit(exitCode, stderrTail());
    }

    private void route(String line) {
        Json message;
        try {
            message = Json.parse(line);
        } catch (IllegalArgumentException e) {
            return;
        }
        switch (message.get("type").asString("")) {
            case "control_response": complete(message.get("response")); break;
            case "control_request": answer(message.get("request_id").asString(""), message.get("request")); break;
            case "control_cancel_request": handler.onCancel(message.get("request_id").asString("")); break;
            case "keep_alive": break;
            default: handler.onMessage(message);
        }
    }

    private void complete(Json response) {
        CompletableFuture<Json> future = pending.remove(response.get("request_id").asString(""));
        if (future == null) return;
        if ("error".equals(response.get("subtype").asString())) {
            future.completeExceptionally(new IllegalStateException(response.get("error").asString("Request failed")));
        } else {
            future.complete(response.get("response"));
        }
    }

    private void answer(String requestId, Json request) {
        CompletableFuture<Json> response;
        try {
            response = handler.onRequest(requestId, request);
        } catch (RuntimeException e) {
            response = new CompletableFuture<>();
            response.completeExceptionally(e);
        }
        response.whenComplete((payload, failure) -> {
            if (failure instanceof CancellationException) return;
            send(Json.object().put("type", "control_response").put("response", failure == null
                ? Json.object().put("subtype", "success").put("request_id", requestId).put("response", payload)
                : Json.object().put("subtype", "error").put("request_id", requestId).put("error", String.valueOf(failure.getMessage()))));
        });
    }

    private void readStderr() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                synchronized (stderr) {
                    stderr.addLast(line);
                    if (stderr.size() > STDERR_LINES) stderr.removeFirst();
                }
            }
        } catch (IOException ignored) {
        }
    }

    private String stderrTail() {
        synchronized (stderr) {
            return String.join("\n", stderr);
        }
    }

    private int awaitExit() {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private static void daemon(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        thread.start();
    }
}
