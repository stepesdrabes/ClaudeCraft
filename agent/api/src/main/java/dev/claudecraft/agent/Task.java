package dev.claudecraft.agent;

public final class Task {
    public enum Kind { AGENT, SHELL, OTHER }

    public enum Status {
        RUNNING, DONE, FAILED, STOPPED;

        public boolean isActive() {
            return this == RUNNING;
        }
    }

    private final String id;
    private final String toolUseId;
    private final Kind kind;
    private final String description;
    private final String agentType;
    private final Status status;
    private final boolean background;
    private final String activity;
    private final int toolUses;
    private final long tokens;
    private final long startedAt;
    private final long durationMillis;

    public Task(String id, String toolUseId, Kind kind, String description, String agentType, Status status, boolean background,
                String activity, int toolUses, long tokens, long startedAt, long durationMillis) {
        this.id = id;
        this.toolUseId = toolUseId;
        this.kind = kind;
        this.description = description;
        this.agentType = agentType;
        this.status = status;
        this.background = background;
        this.activity = activity;
        this.toolUses = toolUses;
        this.tokens = tokens;
        this.startedAt = startedAt;
        this.durationMillis = durationMillis;
    }

    public String id() {
        return id;
    }

    public String toolUseId() {
        return toolUseId;
    }

    public Kind kind() {
        return kind;
    }

    public String description() {
        return description;
    }

    public String agentType() {
        return agentType;
    }

    public Status status() {
        return status;
    }

    public boolean background() {
        return background;
    }

    public String activity() {
        return activity;
    }

    public int toolUses() {
        return toolUses;
    }

    public long tokens() {
        return tokens;
    }

    public long startedAt() {
        return startedAt;
    }

    public long durationMillis() {
        return durationMillis;
    }
}
