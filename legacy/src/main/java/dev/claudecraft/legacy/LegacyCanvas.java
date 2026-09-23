package dev.claudecraft.legacy;

import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

final class LegacyCanvas implements Canvas {
    private final FontRenderer font;
    private final Deque<int[]> clips = new ArrayDeque<>();

    LegacyCanvas(FontRenderer font) {
        this.font = font;
    }

    static String styled(String text, int style) {
        StringBuilder out = new StringBuilder();
        if ((style & BOLD) != 0) out.append("§l");
        if ((style & ITALIC) != 0) out.append("§o");
        if ((style & UNDERLINE) != 0) out.append("§n");
        if ((style & STRIKETHROUGH) != 0) out.append("§m");
        return out.append(text.replace('§', '?')).toString();
    }

    @Override
    public int width(String text, int style) {
        return font.getStringWidth(styled(text, style));
    }

    @Override
    public int lineHeight() {
        return font.FONT_HEIGHT;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        Gui.drawRect(x0, y0, x1, y1, argb);
    }

    @Override
    public void text(String text, int x, int y, int argb, int style) {
        font.drawString(styled(text, style), x, y, argb, (style & SHADOW) != 0);
    }

    @Override
    public void image(Image image, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(LegacyTextures.get(image));
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawScaledCustomSizeModalRect(x, y, u, v, regionWidth, regionHeight, width, height, image.width(), image.height());
        GlStateManager.disableBlend();
    }

    @Override
    public void pushClip(int x0, int y0, int x1, int y1) {
        clips.push(new int[]{x0, y0, x1, y1});
        scissor(x0, y0, x1, y1);
    }

    @Override
    public void popClip() {
        clips.pop();
        if (clips.isEmpty()) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        else scissor(clips.peek()[0], clips.peek()[1], clips.peek()[2], clips.peek()[3]);
    }

    private static void scissor(int x0, int y0, int x1, int y1) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int scale = new ScaledResolution(minecraft).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x0 * scale, minecraft.displayHeight - y1 * scale, Math.max(0, x1 - x0) * scale, Math.max(0, y1 - y0) * scale);
    }
}
