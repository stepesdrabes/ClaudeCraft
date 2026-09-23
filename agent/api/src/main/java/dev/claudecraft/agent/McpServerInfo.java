package dev.claudecraft.agent;

public final class McpServerInfo {
    public enum State { CONNECTED, PENDING, NEEDS_AUTH, FAILED, DISABLED }

    private final String name;
    private final State state;
    private final String scope;

    public McpServerInfo(String name, State state, String scope) {
        this.name = name;
        this.state = state;
        this.scope = scope;
    }

    public String name() {
        return name;
    }

    public State state() {
        return state;
    }

    public String scope() {
        return scope;
    }
}
