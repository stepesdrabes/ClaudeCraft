package dev.claudecraft.agent;

import java.nio.file.Path;

public final class SessionSummary {
    private final String id;
    private final String title;
    private final long updatedAt;
    private final Path cwd;

    public SessionSummary(String id, String title, long updatedAt, Path cwd) {
        this.id = id;
        this.title = title;
        this.updatedAt = updatedAt;
        this.cwd = cwd;
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

    public Path cwd() {
        return cwd;
    }
}
