package dev.claudecraft.platform;

import dev.claudecraft.core.ui.TextMetrics;
import net.minecraft.client.Minecraft;

final class FontMetrics implements TextMetrics {
    @Override
    public int width(String text, int style) {
        return Text.width(Minecraft.getInstance().font, text, style);
    }

    @Override
    public int lineHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }
}
