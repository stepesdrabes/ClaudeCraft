package dev.claudecraft.agent.mcp;

import dev.claudecraft.agent.json.Json;
import dev.claudecraft.agent.tool.Tool;
import dev.claudecraft.agent.tool.ToolResult;

import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class McpServer {
    static final String STATELESS_VERSION = "2026-07-28";
    static final String SESSION_VERSION = "2025-11-25";
    private static final List<String> SUPPORTED_VERSIONS = Arrays.asList(STATELESS_VERSION, SESSION_VERSION, "2025-06-18", "2025-03-26");
    private static final String PROTOCOL_META = "io.modelcontextprotocol/protocolVersion";

    private final String name;
    private final String version;
    private final String instructions;
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public McpServer(String name, String version, String instructions, List<Tool> tools) {
        this.name = name;
        this.version = version;
        this.instructions = instructions;
        for (Tool tool : tools) this.tools.put(tool.name(), tool);
    }

    public String name() {
        return name;
    }

    public CompletableFuture<Json> handle(Json message) {
        Json id = message.get("id");
        String method = message.get("method").asString();
        if (method == null || !message.has("id")) return CompletableFuture.completedFuture(null);
        boolean stateless = STATELESS_VERSION.equals(message.get("params").get("_meta").get(PROTOCOL_META).asString());
        switch (method) {
            case "server/discover": return done(id, discover());
            case "initialize": return done(id, initialize(message.get("params").get("protocolVersion").asString()));
            case "ping": return done(id, Json.object());
            case "tools/list": return done(id, stamp(listTools(), stateless, true));
            case "tools/call": return callTool(message.get("params")).thenApply(result -> success(id, stamp(result, stateless, false)));
            default: return CompletableFuture.completedFuture(error(id, -32601, "Method not found: " + method));
        }
    }

    private Json discover() {
        return withInstructions(Json.object()
            .put("resultType", "complete")
            .put("supportedVersions", SUPPORTED_VERSIONS)
            .put("capabilities", capabilities())
            .put("_meta", Json.object().put("io.modelcontextprotocol/serverInfo", serverInfo()))
            .put("ttlMs", 60_000)
            .put("cacheScope", "private"));
    }

    private Json initialize(String requestedVersion) {
        String negotiated = SUPPORTED_VERSIONS.contains(requestedVersion) && !STATELESS_VERSION.equals(requestedVersion)
            ? requestedVersion : SESSION_VERSION;
        return withInstructions(Json.object()
            .put("protocolVersion", negotiated)
            .put("capabilities", capabilities())
            .put("serverInfo", serverInfo()));
    }

    private Json withInstructions(Json result) {
        return instructions == null ? result : result.put("instructions", instructions);
    }

    private Json listTools() {
        Json list = Json.array();
        for (Tool tool : tools.values()) {
            list.add(Json.object()
                .put("name", tool.name())
                .put("description", tool.description())
                .put("inputSchema", tool.inputSchema()));
        }
        return Json.object().put("tools", list);
    }

    private CompletableFuture<Json> callTool(Json params) {
        Tool tool = tools.get(params.get("name").asString(""));
        if (tool == null) return CompletableFuture.completedFuture(toContent(ToolResult.error("Unknown tool: " + params.get("name"))));
        return tool.call(params.get("arguments"))
            .exceptionally(failure -> ToolResult.error(rootMessage(failure)))
            .thenApply(McpServer::toContent);
    }

    static Json toContent(ToolResult result) {
        Json content = Json.array();
        if (result.image() != null) {
            content.add(Json.object()
                .put("type", "image")
                .put("data", Base64.getEncoder().encodeToString(result.image()))
                .put("mimeType", result.mediaType()));
        }
        if (result.text() != null) content.add(Json.object().put("type", "text").put("text", result.text()));
        return Json.object().put("content", content).put("isError", result.isError());
    }

    private static Json stamp(Json result, boolean stateless, boolean cacheable) {
        if (!stateless) return result;
        result.put("resultType", "complete");
        if (cacheable) result.put("ttlMs", 60_000).put("cacheScope", "private");
        return result;
    }

    private Json capabilities() {
        return Json.object().put("tools", Json.object());
    }

    private Json serverInfo() {
        return Json.object().put("name", name).put("version", version);
    }

    private static CompletableFuture<Json> done(Json id, Json result) {
        return CompletableFuture.completedFuture(success(id, result));
    }

    static Json success(Json id, Json result) {
        return Json.object().put("jsonrpc", "2.0").put("id", id).put("result", result);
    }

    static Json error(Json id, int code, String message) {
        return Json.object().put("jsonrpc", "2.0").put("id", id)
            .put("error", Json.object().put("code", code).put("message", message));
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }
}
