package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.json.Json;

import java.util.concurrent.CompletableFuture;

abstract class PendingRequest {
    private final String id;
    private final Json request;
    private final CompletableFuture<Json> response = new CompletableFuture<>();

    PendingRequest(String id, Json request) {
        this.id = id;
        this.request = request;
    }

    public String id() {
        return id;
    }

    public String toolUseId() {
        return request.get("tool_use_id").asString("");
    }

    Json request() {
        return request;
    }

    public Json input() {
        return request.get("input");
    }

    CompletableFuture<Json> response() {
        return response;
    }

    void respond(Json payload) {
        response.complete(payload);
    }

    void allowWith(Json updatedInput) {
        respond(Json.object().put("behavior", "allow").put("updatedInput", updatedInput));
    }

    public void deny(String message) {
        respond(Json.object().put("behavior", "deny").put("message", message));
    }
}
