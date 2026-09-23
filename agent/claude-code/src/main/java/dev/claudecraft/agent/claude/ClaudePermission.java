package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.PermissionRequest;
import dev.claudecraft.agent.json.Json;

final class ClaudePermission extends PendingRequest implements PermissionRequest {
    ClaudePermission(String id, Json request) {
        super(id, request);
    }

    @Override
    public String toolName() {
        return request().get("tool_name").asString("");
    }

    @Override
    public String title() {
        return request().get("display_name").asString(toolName());
    }

    @Override
    public boolean canRemember() {
        return request().get("permission_suggestions").size() > 0;
    }

    @Override
    public void allow(boolean remember) {
        Json decision = Json.object().put("behavior", "allow").put("updatedInput", input());
        if (remember) decision.put("updatedPermissions", request().get("permission_suggestions"));
        respond(decision);
    }
}
