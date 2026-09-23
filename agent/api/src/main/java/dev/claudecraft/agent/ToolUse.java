package dev.claudecraft.agent;

import dev.claudecraft.agent.json.Json;

public final class ToolUse {
    private final String id;
    private final String name;
    private final Json input;

    public ToolUse(String id, String name, Json input) {
        this.id = id;
        this.name = name;
        this.input = input;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Json input() {
        return input;
    }
}
