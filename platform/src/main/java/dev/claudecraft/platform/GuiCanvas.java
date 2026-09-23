package dev.claudecraft.platform;

import dev.claudecraft.core.ui.Canvas;
import net.minecraft.client.gui.Font;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;

import java.util.ArrayDeque;
import java.util.Deque;
*///?}

final class GuiCanvas implements Canvas {
    //? if >=1.20 {
    private final GuiGraphicsExtractor graphics;
    //?} else {
    /*private final PoseStack graphics;
    private final Deque<int[]> clips = new ArrayDeque<>();
    *///?}
    private final Font font;

    //? if >=1.20 {
    GuiCanvas(GuiGraphicsExtractor graphics, Font font) {
    //?} else
    //GuiCanvas(PoseStack graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }

    @Override
    public int width(String text, int style) {
        return Text.width(font, text, style);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        //? if >=1.20 {
        graphics.fill(x0, y0, x1, y1, argb);
        //?} else
        //GuiComponent.fill(graphics, x0, y0, x1, y1, argb);
    }

    @Override
    public void text(String text, int x, int y, int argb, int style) {
        boolean shadow = (style & SHADOW) != 0;
        //? if >=26.1 {
        graphics.text(font, Text.literal(text, style), x, y, argb, shadow);
        //?} elif >=1.20 {
        /*graphics.drawString(font, Text.literal(text, style), x, y, argb, shadow);
        *///?} else {
        /*if (shadow) font.drawShadow(graphics, Text.literal(text, style), x, y, argb);
        else font.draw(graphics, Text.literal(text, style), x, y, argb);
        *///?}
    }

    @Override
    public void pushClip(int x0, int y0, int x1, int y1) {
        //? if >=1.20 {
        graphics.enableScissor(x0, y0, x1, y1);
        //?} else {
        /*clips.push(new int[]{x0, y0, x1, y1});
        scissor(x0, y0, x1, y1);
        *///?}
    }

    @Override
    public void popClip() {
        //? if >=1.20 {
        graphics.disableScissor();
        //?} else {
        /*clips.pop();
        if (clips.isEmpty()) RenderSystem.disableScissor();
        else scissor(clips.peek()[0], clips.peek()[1], clips.peek()[2], clips.peek()[3]);
        *///?}
    }
    //? if <1.20 {

    /*private static void scissor(int x0, int y0, int x1, int y1) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int bottom = (int) (window.getHeight() - y1 * scale);
        RenderSystem.enableScissor((int) (x0 * scale), bottom, (int) ((x1 - x0) * scale), (int) ((y1 - y0) * scale));
    }
    *///?}
}
