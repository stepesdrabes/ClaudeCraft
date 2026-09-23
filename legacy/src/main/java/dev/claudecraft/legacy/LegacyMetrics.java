package dev.claudecraft.legacy;

import dev.claudecraft.core.ui.TextMetrics;
import net.minecraft.client.Minecraft;

final class LegacyMetrics implements TextMetrics {
    @Override
    public int width(String text, int style) {
        return LegacyClient.font(Minecraft.getMinecraft()).getStringWidth(LegacyCanvas.styled(text, style));
    }

    @Override
    public int lineHeight() {
        return LegacyClient.font(Minecraft.getMinecraft()).FONT_HEIGHT;
    }
}
