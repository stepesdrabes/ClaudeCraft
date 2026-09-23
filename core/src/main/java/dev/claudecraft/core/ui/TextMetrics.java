package dev.claudecraft.core.ui;

public interface TextMetrics {
    int width(String text, int style);

    int lineHeight();

    default int width(String text) {
        return width(text, 0);
    }
}
