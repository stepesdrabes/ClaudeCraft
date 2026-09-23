package dev.claudecraft.core.ui;

public final class Span {
    public final String text;
    public final int color;
    public final int style;

    public Span(String text, int color, int style) {
        this.text = text;
        this.color = color;
        this.style = style;
    }

    public Span withText(String text) {
        return new Span(text, color, style);
    }
}
