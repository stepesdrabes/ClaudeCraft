package dev.claudecraft.core.chat;

import dev.claudecraft.agent.LiveSession;
import dev.claudecraft.core.ui.Theme;

public enum Status {
    IDLE("Idle", Theme.IDLE),
    WORKING("Working", Theme.WORKING),
    NEEDS_YOU("Needs you", Theme.NEEDS_YOU),
    DONE("Done", Theme.DONE),
    FAILED("Failed", Theme.ERROR);

    private final String label;
    private final int color;

    Status(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public int color() {
        return color;
    }

    public boolean isActive() {
        return this == WORKING || this == NEEDS_YOU;
    }

    static Status of(LiveSession.State state) {
        switch (state) {
            case WORKING: return WORKING;
            case NEEDS_YOU: return NEEDS_YOU;
            case FAILED: return FAILED;
            default: return DONE;
        }
    }
}
