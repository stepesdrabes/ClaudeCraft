package dev.claudecraft.core.chat;

public final class ToolEntry extends Entry {
    public enum State { RUNNING, SUCCEEDED, FAILED }

    private final String id;
    private final String label;
    private final String detail;
    private State state = State.RUNNING;
    private String output = "";
    private boolean expanded;

    public ToolEntry(String id, String label, String detail) {
        this.id = id;
        this.label = label;
        this.detail = detail;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String detail() {
        return detail;
    }

    public State state() {
        return state;
    }

    public String output() {
        return output;
    }

    public boolean expanded() {
        return expanded;
    }

    public void toggle() {
        expanded = !expanded;
        changed();
    }

    void finish(String output, boolean failed) {
        this.output = output;
        this.state = failed ? State.FAILED : State.SUCCEEDED;
        changed();
    }

    void abandon() {
        if (state != State.RUNNING) return;
        state = State.FAILED;
        changed();
    }
}
