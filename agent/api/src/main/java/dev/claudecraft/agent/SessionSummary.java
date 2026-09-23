package dev.claudecraft.agent;

public final class SessionSummary {
    private final String id;
    private final String title;
    private final long updatedAt;

    public SessionSummary(String id, String title, long updatedAt) {
        this.id = id;
        this.title = title;
        this.updatedAt = updatedAt;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public long updatedAt() {
        return updatedAt;
    }
}
