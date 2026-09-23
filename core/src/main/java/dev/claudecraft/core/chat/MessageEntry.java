package dev.claudecraft.core.chat;

public final class MessageEntry extends Entry {
    public enum Role { USER, ASSISTANT }

    private final Role role;
    private final StringBuilder text = new StringBuilder();
    private boolean streaming;

    public MessageEntry(Role role, String text, boolean streaming) {
        this.role = role;
        this.text.append(text);
        this.streaming = streaming;
    }

    public Role role() {
        return role;
    }

    public String text() {
        return text.toString();
    }

    public boolean streaming() {
        return streaming;
    }

    void append(String delta) {
        text.append(delta);
        changed();
    }

    void finish() {
        if (!streaming) return;
        streaming = false;
        changed();
    }
}
