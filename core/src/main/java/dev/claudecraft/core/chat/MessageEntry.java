package dev.claudecraft.core.chat;

import dev.claudecraft.core.ui.Picture;

import java.util.List;

public final class MessageEntry extends Entry {
    public enum Role { USER, ASSISTANT }

    private final Role role;
    private final StringBuilder text = new StringBuilder();
    private final List<Picture> images;
    private boolean streaming;

    public MessageEntry(Role role, String text, List<Picture> images, boolean streaming) {
        this.role = role;
        this.text.append(text);
        this.images = images;
        this.streaming = streaming;
    }

    public Role role() {
        return role;
    }

    public String text() {
        return text.toString();
    }

    public List<Picture> images() {
        return images;
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
