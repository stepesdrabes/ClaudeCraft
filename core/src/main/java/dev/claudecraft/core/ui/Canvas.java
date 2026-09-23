package dev.claudecraft.core.ui;

public interface Canvas extends TextMetrics {
    int BOLD = 1;
    int ITALIC = 2;
    int UNDERLINE = 4;
    int STRIKETHROUGH = 8;
    int SHADOW = 16;

    void fill(int x0, int y0, int x1, int y1, int argb);

    void text(String text, int x, int y, int argb, int style);

    void pushClip(int x0, int y0, int x1, int y1);

    void popClip();

    void image(Image image, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight);

    default void image(Image image, int x, int y, int width, int height) {
        image(image, x, y, width, height, 0, 0, image.width(), image.height());
    }

    default void text(String text, int x, int y, int argb) {
        text(text, x, y, argb, 0);
    }

    default void outline(int x0, int y0, int x1, int y1, int argb) {
        fill(x0, y0, x1, y0 + 1, argb);
        fill(x0, y1 - 1, x1, y1, argb);
        fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
        fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
    }
}
