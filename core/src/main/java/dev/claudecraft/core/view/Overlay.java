package dev.claudecraft.core.view;

import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.KeyPress;

interface Overlay {
    void render(Canvas canvas, int screenWidth, int screenHeight, double mouseX, double mouseY, long now);

    boolean click(double mouseX, double mouseY);

    boolean keyPressed(KeyPress press);

    default boolean scroll(double mouseX, double mouseY, double amount) {
        return false;
    }
}
