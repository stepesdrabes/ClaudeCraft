package dev.claudecraft.platform;

import dev.claudecraft.core.ClaudeCraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
//import com.mojang.blaze3d.vertex.PoseStack;
//? if >=1.21.9
import net.minecraft.resources.Identifier;

import java.nio.file.Path;

public final class ClaudeCraftClient {
    public static final String MOD_ID = "claudecraft";
    private static final String OPEN_KEY = "key.claudecraft.open";

    private static KeyMapping openKey;
    private static ClaudeCraft app;

    private ClaudeCraftClient() {
    }

    public static KeyMapping openKey() {
        if (openKey == null) {
            //? if >=1.21.9 {
            KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
            openKey = new KeyMapping(OPEN_KEY, Keys.code("grave.accent"), category);
            //?} else
            //openKey = new KeyMapping(OPEN_KEY, Keys.code("grave.accent"), "key.categories." + MOD_ID);
        }
        return openKey;
    }

    public static void start(String loader, Path gameDirectory, Path configDirectory) {
        app = ClaudeCraft.start(new MinecraftPlatform(loader, gameDirectory, configDirectory, openKey()));
    }

    public static void tick() {
        if (app == null) return;
        while (openKey.consumeClick()) {
            if (MinecraftPlatform.currentScreen() == null) app.openKeyPressed();
        }
    }

    //? if >=1.20 {
    public static void renderHud(GuiGraphicsExtractor graphics) {
    //?} else
    //public static void renderHud(PoseStack graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (app == null || hudHidden(minecraft)) return;
        app.renderHud(new GuiCanvas(graphics, minecraft.font), minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    }

    private static boolean hudHidden(Minecraft minecraft) {
        //? if >=26.2 {
        return minecraft.gui.hud.isHidden();
        //?} else
        //return minecraft.options.hideGui;
    }
}
