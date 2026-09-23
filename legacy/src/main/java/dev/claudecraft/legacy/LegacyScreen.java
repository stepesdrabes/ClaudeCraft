package dev.claudecraft.legacy;

import dev.claudecraft.core.ui.KeyPress;
import dev.claudecraft.core.view.Panel;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

final class LegacyScreen extends GuiScreen {
    private final Panel panel;
    private final KeyBinding openKey;

    LegacyScreen(Panel panel, KeyBinding openKey) {
        this.panel = panel;
        this.openKey = openKey;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        panel.resize(width, height);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int wheel = Mouse.getDWheel();
        if (wheel != 0) panel.mouseScrolled(mouseX, mouseY, Math.signum(wheel));
        panel.render(new LegacyCanvas(LegacyClient.font(mc)), mouseX, mouseY);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        KeyPress press = LegacyKeys.press(keyCode, openKey);
        if (!panel.keyPressed(press) && typedChar >= ' ') panel.charTyped(typedChar);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        panel.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        panel.mouseReleased();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        panel.mouseDragged(mouseX, mouseY);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        panel.removed();
    }
}
