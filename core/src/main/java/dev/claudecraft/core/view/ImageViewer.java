package dev.claudecraft.core.view;

import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Image;
import dev.claudecraft.core.ui.KeyPress;
import dev.claudecraft.core.ui.Picture;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.Theme;

final class ImageViewer implements Overlay {
    private final Picture picture;

    ImageViewer(Picture picture) {
        this.picture = picture;
    }

    @Override
    public void render(Canvas canvas, int screenWidth, int screenHeight, double mouseX, double mouseY, long now) {
        canvas.fill(0, 0, screenWidth, screenHeight, 0xC0000000);
        Image image = picture.preview();
        if (image == null) return;
        int maxWidth = screenWidth * 9 / 10, maxHeight = screenHeight * 9 / 10;
        float scale = Math.min(maxWidth / (float) image.width(), maxHeight / (float) image.height());
        int width = Math.round(image.width() * scale), height = Math.round(image.height() * scale);
        Rect frame = new Rect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
        canvas.outline(frame.x - 1, frame.y - 1, frame.right() + 1, frame.bottom() + 1, Theme.INPUT_BORDER);
        canvas.image(image, frame.x, frame.y, frame.width, frame.height);
    }

    @Override
    public boolean click(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean keyPressed(KeyPress press) {
        return false;
    }
}
