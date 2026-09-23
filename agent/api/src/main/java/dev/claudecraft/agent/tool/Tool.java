package dev.claudecraft.agent.tool;

import dev.claudecraft.agent.json.Json;

import java.util.concurrent.CompletableFuture;

public final class Tool {
    private final String name;
    private final String description;
    private final Json inputSchema;
    private final Handler handler;

    private Tool(String name, String description, Json inputSchema, Handler handler) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.handler = handler;
    }

    public static Tool of(String name, String description, Schema input, Handler handler) {
        return new Tool(name, description, input.toJson(), handler);
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Json inputSchema() {
        return inputSchema;
    }

    public CompletableFuture<ToolResult> call(Json arguments) {
        try {
            return handler.call(arguments);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(ToolResult.error(e.getMessage()));
        }
    }

    public interface Handler {
        CompletableFuture<ToolResult> call(Json arguments);
    }
}
