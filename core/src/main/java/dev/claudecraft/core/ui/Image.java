package dev.claudecraft.core.ui;

import java.util.concurrent.atomic.AtomicInteger;

public final class Image {
    private static final AtomicInteger IDS = new AtomicInteger();

    private final int id = IDS.incrementAndGet();
    private final int width;
    private final int height;
    private final int[] argb;

    public Image(int width, int height, int[] argb) {
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public int id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int[] argb() {
        return argb;
    }
}
