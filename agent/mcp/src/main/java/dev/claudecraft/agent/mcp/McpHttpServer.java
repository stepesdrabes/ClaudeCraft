package dev.claudecraft.agent.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.claudecraft.agent.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class McpHttpServer implements AutoCloseable {
    public static final String PATH = "/mcp";
    private static final long CALL_TIMEOUT_SECONDS = 120;
    private static final int PORT_ATTEMPTS = 10;

    private final HttpServer http;
    private final ExecutorService executor;
    private final McpServer mcp;

    private McpHttpServer(HttpServer http, ExecutorService executor, McpServer mcp) {
        this.http = http;
        this.executor = executor;
        this.mcp = mcp;
    }

    public static McpHttpServer start(McpServer mcp, int preferredPort) throws IOException {
        HttpServer http = bind(preferredPort);
        ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "claudecraft-mcp-http");
            thread.setDaemon(true);
            return thread;
        });
        McpHttpServer server = new McpHttpServer(http, executor, mcp);
        http.createContext(PATH, server::serve);
        http.setExecutor(executor);
        http.start();
        return server;
    }

    private static HttpServer bind(int preferredPort) throws IOException {
        BindException last = null;
        for (int port = preferredPort; port < preferredPort + PORT_ATTEMPTS; port++) {
            try {
                return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
            } catch (BindException e) {
                last = e;
            }
        }
        throw last;
    }

    public int port() {
        return http.getAddress().getPort();
    }

    public String url() {
        return "http://127.0.0.1:" + port() + PATH;
    }

    @Override
    public void close() {
        http.stop(0);
        executor.shutdownNow();
    }

    private void serve(HttpExchange exchange) throws IOException {
        try {
            if (!isLocalOrigin(exchange.getRequestHeaders().getFirst("Origin"))) {
                respond(exchange, 403, null);
            } else if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, null);
            } else {
                Json response = dispatch(Streams.readUtf8(exchange.getRequestBody()));
                respond(exchange, response == null ? 202 : 200, response);
            }
        } finally {
            exchange.close();
        }
    }

    private Json dispatch(String body) {
        Json request;
        try {
            request = Json.parse(body);
        } catch (IllegalArgumentException e) {
            return McpServer.error(Json.NULL, -32700, "Parse error: " + e.getMessage());
        }
        if (!request.isArray()) return await(mcp.handle(request));
        List<CompletableFuture<Json>> pending = new ArrayList<>();
        for (Json message : request.items()) pending.add(mcp.handle(message));
        Json responses = Json.array();
        for (CompletableFuture<Json> future : pending) {
            Json response = await(future);
            if (response != null) responses.add(response);
        }
        return responses.size() == 0 ? null : responses;
    }

    private static Json await(CompletableFuture<Json> future) {
        try {
            return future.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            return McpServer.error(Json.NULL, -32603, "Internal error: " + e);
        }
    }

    private static boolean isLocalOrigin(String origin) {
        if (origin == null) return true;
        return origin.matches("https?://(127\\.0\\.0\\.1|localhost|\\[::1])(:\\d+)?/?");
    }


    private static void respond(HttpExchange exchange, int status, Json body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
