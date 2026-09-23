package dev.claudecraft.core.ui;

public final class EmojiCanvas implements Canvas {
    private final Canvas canvas;
    private final Emoji emoji = Emoji.get();

    public EmojiCanvas(Canvas canvas) {
        this.canvas = canvas;
    }

    @Override
    public int width(String text, int style) {
        return emoji.width(canvas, text, style);
    }

    @Override
    public int lineHeight() {
        return canvas.lineHeight();
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        canvas.fill(x0, y0, x1, y1, argb);
    }

    @Override
    public void text(String text, int x, int y, int argb, int style) {
        emoji.draw(canvas, text, x, y, argb, style);
    }

    @Override
    public void pushClip(int x0, int y0, int x1, int y1) {
        canvas.pushClip(x0, y0, x1, y1);
    }

    @Override
    public void popClip() {
        canvas.popClip();
    }

    @Override
    public void image(Image image, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
        canvas.image(image, x, y, width, height, u, v, regionWidth, regionHeight);
    }
}
