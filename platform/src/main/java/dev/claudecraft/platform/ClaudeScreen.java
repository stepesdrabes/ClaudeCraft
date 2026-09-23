package dev.claudecraft.platform;

import dev.claudecraft.core.view.Panel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
//import com.mojang.blaze3d.vertex.PoseStack;

final class ClaudeScreen extends Screen {
    private final Panel panel;
    private final KeyMapping openKey;

    ClaudeScreen(Panel panel, KeyMapping openKey) {
        super(Text.literal("Claude", 0));
        this.panel = panel;
        this.openKey = openKey;
    }

    @Override
    protected void init() {
        panel.resize(width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        panel.render(new GuiCanvas(graphics, font), mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }
    //?} elif >=1.20.2 {
    /*@Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        panel.render(new GuiCanvas(graphics, font), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }
    *///?} elif >=1.20 {
    /*@Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        panel.render(new GuiCanvas(graphics, font), mouseX, mouseY);
    }
    *///?} else {
    /*@Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        panel.render(new GuiCanvas(pose, font), mouseX, mouseY);
    }
    *///?}

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(KeyEvent event) {
        return panel.keyPressed(Keys.press(event, openKey)) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return typed(event.codepoint());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return panel.mouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return panel.mouseReleased();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return panel.mouseDragged(event.x(), event.y());
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return panel.keyPressed(Keys.press(key, scanCode, modifiers, openKey)) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return typed(codePoint);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return panel.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return panel.mouseReleased();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return panel.mouseDragged(mouseX, mouseY);
    }
    *///?}

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return panel.mouseScrolled(mouseX, mouseY, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return panel.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    @Override
    public void removed() {
        panel.removed();
    }

    private boolean typed(int codePoint) {
        boolean handled = false;
        for (char c : Character.toChars(codePoint)) handled |= panel.charTyped(c);
        return handled;
    }
}
