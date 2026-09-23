package dev.claudecraft.core.chat;

public final class NoticeEntry extends Entry {
    private final String text;
    private final int color;

    public NoticeEntry(String text, int color) {
        this.text = text;
        this.color = color;
    }

    public String text() {
        return text;
    }

    public int color() {
        return color;
    }
}
