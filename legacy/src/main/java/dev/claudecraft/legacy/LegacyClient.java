package dev.claudecraft.legacy;

import dev.claudecraft.core.ClaudeCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.nio.file.Path;

public final class LegacyClient {
    public static final String MOD_ID = "claudecraft";

    private static KeyBinding openKey;
    private static ClaudeCraft app;

    private LegacyClient() {
    }

    public static KeyBinding openKey() {
        if (openKey == null) openKey = new KeyBinding("key.claudecraft.open", Keyboard.KEY_GRAVE, "key.categories.claudecraft");
        return openKey;
    }

    public static void start(String loader, Path gameDirectory, Path configDirectory) {
        app = ClaudeCraft.start(new LegacyPlatform(loader, gameDirectory, configDirectory, openKey()));
    }

    public static void tick() {
        if (app == null) return;
        while (openKey.isPressed()) {
            if (Minecraft.getMinecraft().currentScreen == null) app.openKeyPressed();
        }
    }

    public static void renderHud() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (app == null || minecraft.gameSettings.hideGUI) return;
        ScaledResolution resolution = new ScaledResolution(minecraft);
        app.renderHud(new LegacyCanvas(font(minecraft)), resolution.getScaledWidth(), resolution.getScaledHeight());
    }

    static FontRenderer font(Minecraft minecraft) {
        //? if >=1.12 {
        /*return minecraft.fontRenderer;
        *///?} else
        return minecraft.fontRendererObj;
    }

    static EntityPlayerSP player() {
        //? if >=1.12 {
        /*return Minecraft.getMinecraft().player;
        *///?} else
        return Minecraft.getMinecraft().thePlayer;
    }

    static WorldClient world() {
        //? if >=1.12 {
        /*return Minecraft.getMinecraft().world;
        *///?} else
        return Minecraft.getMinecraft().theWorld;
    }
}
