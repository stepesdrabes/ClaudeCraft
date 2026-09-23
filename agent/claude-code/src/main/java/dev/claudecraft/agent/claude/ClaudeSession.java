package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.Session;
import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.SessionSpec;
import dev.claudecraft.agent.ToolUse;
import dev.claudecraft.agent.TurnResult;
import dev.claudecraft.agent.json.Json;
import dev.claudecraft.agent.mcp.McpServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class ClaudeSession implements Session, CliProcess.Handler {
    private static final Json NOTIFICATION_ACK = Json.object().put("jsonrpc", "2.0").put("id", 0).put("result", Json.object());

    private final SessionSpec spec;
    private final SessionListener listener;
    private final McpServer tools;
    private final Map<String, PendingRequest> prompts = new ConcurrentHashMap<>();
    private volatile CliProcess process;
    private volatile boolean closing;
    private CompletableFuture<Json> initialization;
    private CompletableFuture<?> outbox;
    private String turnSummary;

    private ClaudeSession(SessionSpec spec, SessionListener listener) {
        this.spec = spec;
        this.listener = listener;
        this.tools = new McpServer(spec.toolNamespace(), "1.0", null, spec.tools());
    }

    static ClaudeSession start(ClaudeCli cli, SessionSpec spec, SessionListener listener) {
        ClaudeSession session = new ClaudeSession(spec, listener);
        try {
            session.process = CliProcess.start(cli, arguments(spec), spec.workspace(), session);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start Claude Code: " + e.getMessage(), e);
        }
        Json initialize = Json.object().put("subtype", "initialize");
        if (spec.title() != null && spec.resumeId() == null) initialize.put("title", spec.title());
        session.initialization = session.process.request(initialize);
        session.outbox = session.initialization;
        return session;
    }

    CompletableFuture<Json> initialization() {
        return initialization;
    }

    private static List<String> arguments(SessionSpec spec) {
        List<String> args = new ArrayList<>(Arrays.asList(
            "-p", "--input-format=stream-json", "--output-format=stream-json", "--verbose",
            "--include-partial-messages", "--thinking-display=summarized", "--permission-prompt-tool=stdio"));
        if (spec.resumeId() != null) args.add("--resume=" + spec.resumeId());
        if (spec.model() != null) args.add("--model=" + spec.model());
        if (spec.permissionMode() != null) args.add("--permission-mode=" + spec.permissionMode());
        if (spec.instructions() != null) args.add("--append-system-prompt=" + spec.instructions());
        if (!spec.tools().isEmpty()) {
            String namespace = spec.toolNamespace();
            Json server = Json.object().put("type", "sdk").put("name", namespace).put("alwaysLoad", true);
            args.add("--mcp-config=" + Json.object().put("mcpServers", Json.object().put(namespace, server)));
            args.add("--allowedTools=mcp__" + namespace);
        }
        return args;
    }

    @Override
    public synchronized void send(String text) {
        Json message = Json.object()
            .put("type", "user")
            .put("session_id", "")
            .put("parent_tool_use_id", null)
            .put("message", Json.object().put("role", "user").put("content", text));
        outbox = outbox.handle((ignored, failure) -> null).thenRun(() -> process.send(message));
    }

    @Override
    public void interrupt() {
        control(Json.object().put("subtype", "interrupt"));
    }

    @Override
    public void setModel(String modelId) {
        control(Json.object().put("subtype", "set_model").put("model", modelId));
    }

    @Override
    public void setPermissionMode(String modeId) {
        control(Json.object().put("subtype", "set_permission_mode").put("mode", modeId));
    }

    @Override
    public void rename(String title) {
        control(Json.object().put("subtype", "rename_session").put("title", title));
    }

    @Override
    public boolean isOpen() {
        return process.isAlive();
    }

    @Override
    public void close() {
        closing = true;
        process.close();
    }

    private void control(Json request) {
        process.request(request).exceptionally(failure -> {
            emit(l -> l.onError(failure.getMessage()));
            return null;
        });
    }

    @Override
    public void onMessage(Json message) {
        if (!message.get("parent_tool_use_id").isNull()) return;
        switch (message.get("type").asString("")) {
            case "system": onSystem(message); break;
            case "stream_event": onStreamEvent(message.get("event")); break;
            case "assistant": onAssistant(message.get("message")); break;
            case "user": onToolResults(message.get("message").get("content")); break;
            case "result": onResult(message); break;
            default: break;
        }
    }

    private void onSystem(Json message) {
        switch (message.get("subtype").asString("")) {
            case "init":
                String sessionId = message.get("session_id").asString();
                String model = message.get("model").asString();
                emit(l -> l.onStarted(sessionId, model));
                break;
            case "status":
                String status = message.get("status").asString();
                emit(l -> l.onStatus(status));
                break;
            case "api_retry":
                emit(l -> l.onStatus("retrying"));
                break;
            case "post_turn_summary":
                turnSummary = message.get("status_detail").asString();
                break;
            case "local_command_output":
                String output = message.get("content").asString("");
                emit(l -> l.onText(output));
                break;
            default:
                break;
        }
    }

    private void onStreamEvent(Json event) {
        String type = event.get("type").asString("");
        if ("content_block_start".equals(type)) onBlockStart(event.get("content_block"));
        else if ("content_block_delta".equals(type)) onBlockDelta(event.get("delta"));
    }

    private void onBlockStart(Json block) {
        String type = block.get("type").asString("");
        if ("thinking".equals(type)) {
            emit(l -> l.onThinking(""));
        } else if ("tool_use".equals(type)) {
            String name = block.get("name").asString("");
            emit(l -> l.onToolStarting(name));
        }
    }

    private void onBlockDelta(Json delta) {
        String type = delta.get("type").asString("");
        if ("text_delta".equals(type)) {
            String text = delta.get("text").asString("");
            emit(l -> l.onText(text));
        } else if ("thinking_delta".equals(type)) {
            String thinking = delta.get("thinking").asString("");
            if (!thinking.isEmpty()) emit(l -> l.onThinking(thinking));
        }
    }

    private void onAssistant(Json message) {
        for (Json block : message.get("content").items()) {
            if (!"tool_use".equals(block.get("type").asString())) continue;
            ToolUse use = new ToolUse(block.get("id").asString(""), block.get("name").asString(""), block.get("input"));
            emit(l -> l.onToolUse(use));
        }
    }

    private void onToolResults(Json content) {
        for (Json block : content.items()) {
            if (!"tool_result".equals(block.get("type").asString())) continue;
            String id = block.get("tool_use_id").asString("");
            String output = Transcripts.text(block.get("content"));
            boolean error = block.get("is_error").asBoolean(false);
            emit(l -> l.onToolResult(id, output, error));
        }
    }

    private void onResult(Json message) {
        TurnResult.Outcome outcome = outcome(message);
        String error = outcome == TurnResult.Outcome.FAILED ? failureText(message) : null;
        TurnResult result = new TurnResult(outcome, turnSummary, error,
            message.get("duration_ms").asLong(0), message.get("total_cost_usd").asDouble(0));
        turnSummary = null;
        emit(l -> l.onTurnEnd(result));
    }

    private static TurnResult.Outcome outcome(Json result) {
        if (result.get("terminal_reason").asString("").startsWith("aborted")) return TurnResult.Outcome.INTERRUPTED;
        boolean success = "success".equals(result.get("subtype").asString()) && !result.get("is_error").asBoolean(false);
        return success ? TurnResult.Outcome.COMPLETED : TurnResult.Outcome.FAILED;
    }

    private static String failureText(Json result) {
        List<String> errors = new ArrayList<>();
        for (Json error : result.get("errors").items()) errors.add(error.asString(""));
        if (errors.isEmpty()) errors.add(result.get("result").asString(result.get("subtype").asString("error")));
        return String.join("\n", errors);
    }

    @Override
    public CompletableFuture<Json> onRequest(String requestId, Json request) {
        switch (request.get("subtype").asString("")) {
            case "can_use_tool":
                boolean question = "AskUserQuestion".equals(request.get("tool_name").asString());
                return track(question ? new ClaudeQuestion(requestId, request) : new ClaudePermission(requestId, request));
            case "mcp_message":
                return tools.handle(request.get("message"))
                    .thenApply(response -> Json.object().put("mcp_response", response != null ? response : NOTIFICATION_ACK));
            case "hook_callback":
                return CompletableFuture.completedFuture(Json.object());
            case "elicitation":
                return CompletableFuture.completedFuture(Json.object().put("action", "decline"));
            default:
                throw new IllegalStateException("Unsupported control request: " + request.get("subtype"));
        }
    }

    private CompletableFuture<Json> track(PendingRequest prompt) {
        prompts.put(prompt.id(), prompt);
        prompt.response().whenComplete((response, failure) -> prompts.remove(prompt.id()));
        if (prompt instanceof ClaudeQuestion) emit(l -> l.onQuestion((ClaudeQuestion) prompt));
        else emit(l -> l.onPermissionRequest((ClaudePermission) prompt));
        return prompt.response();
    }

    @Override
    public void onCancel(String requestId) {
        PendingRequest prompt = prompts.remove(requestId);
        if (prompt == null) return;
        prompt.response().cancel(false);
        emit(l -> l.onRequestCancelled(requestId));
    }

    @Override
    public void onExit(int exitCode, String stderr) {
        if (!closing && exitCode != 0) {
            String detail = stderr.isEmpty() ? "exit code " + exitCode : stderr.substring(stderr.lastIndexOf('\n') + 1);
            emit(l -> l.onError("Claude Code stopped: " + detail));
        }
        emit(SessionListener::onClosed);
    }

    private void emit(Consumer<SessionListener> event) {
        spec.callbacks().execute(() -> event.accept(listener));
    }
}
