package dev.claudecraft.core.ui;

import dev.claudecraft.agent.ImageData;

import java.io.IOException;

public final class Picture {
    private static final int PREVIEW_SIZE = 512;

    private final ImageData data;
    private volatile Image preview;
    private volatile boolean failed;
    private boolean requested;

    public Picture(ImageData data) {
        this.data = data;
    }

    public Picture(ImageData data, Image image) {
        this.data = data;
        this.preview = Images.fit(image, PREVIEW_SIZE);
        this.requested = true;
    }

    public ImageData data() {
        return data;
    }

    public Image preview() {
        if (!requested) {
            requested = true;
            Images.DECODER.execute(() -> {
                try {
                    preview = Images.decode(data.data(), PREVIEW_SIZE);
                } catch (IOException | RuntimeException e) {
                    failed = true;
                }
            });
        }
        return preview;
    }

    public boolean failed() {
        return failed;
    }
}
