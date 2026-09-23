package dev.claudecraft.core.ui;

import java.util.ArrayList;
import java.util.List;

final class FakeCanvas implements Canvas {
    static final int CHAR_WIDTH = 6;
    final List<String> texts = new ArrayList<>();

    @Override
    public int width(String text, int style) {
        return text.length() * (CHAR_WIDTH + ((style & BOLD) != 0 ? 1 : 0));
    }

    @Override
    public int lineHeight() {
        return 9;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
    }

    @Override
    public void text(String text, int x, int y, int argb, int style) {
        texts.add(text);
    }

    @Override
    public void pushClip(int x0, int y0, int x1, int y1) {
    }

    @Override
    public void popClip() {
    }
}
