package dev.claudecraft.agent;

import java.nio.file.Path;

public final class LiveSession {
    public enum State { WORKING, NEEDS_YOU, IDLE, FAILED }

    private final String id;
    private final String sessionId;
    private final Path cwd;
    private final boolean background;
    private final State state;

    public LiveSession(String id, String sessionId, Path cwd, boolean background, State state) {
        this.id = id;
        this.sessionId = sessionId;
        this.cwd = cwd;
        this.background = background;
        this.state = state;
    }

    public String id() {
        return id;
    }

    public String sessionId() {
        return sessionId;
    }

    public Path cwd() {
        return cwd;
    }

    public boolean background() {
        return background;
    }

    public State state() {
        return state;
    }
}
