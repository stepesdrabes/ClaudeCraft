package dev.claudecraft.core.ui;

public final class Rect {
    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public Rect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    public Rect inset(int amount) {
        return new Rect(x + amount, y + amount, width - 2 * amount, height - 2 * amount);
    }

    public Rect inset(int horizontal, int vertical) {
        return new Rect(x + horizontal, y + vertical, width - 2 * horizontal, height - 2 * vertical);
    }

    public Rect top(int size) {
        return new Rect(x, y, width, size);
    }

    public Rect bottom(int size) {
        return new Rect(x, bottom() - size, width, size);
    }

    public Rect left(int size) {
        return new Rect(x, y, size, height);
    }

    public Rect right(int size) {
        return new Rect(right() - size, y, size, height);
    }

    public Rect shrinkTop(int amount) {
        return new Rect(x, y + amount, width, height - amount);
    }

    public Rect shrinkBottom(int amount) {
        return new Rect(x, y, width, height - amount);
    }

    public Rect shrinkLeft(int amount) {
        return new Rect(x + amount, y, width - amount, height);
    }

    public Rect shrinkRight(int amount) {
        return new Rect(x, y, width - amount, height);
    }

    public void fill(Canvas canvas, int argb) {
        canvas.fill(x, y, right(), bottom(), argb);
    }
}
