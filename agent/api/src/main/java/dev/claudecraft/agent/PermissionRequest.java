package dev.claudecraft.agent;

import dev.claudecraft.agent.json.Json;

public interface PermissionRequest {
    String id();

    String toolUseId();

    String toolName();

    String title();

    Json input();

    boolean canRemember();

    void allow(boolean remember);

    void allowAndSetMode(String mode);

    void deny(String message);
}
