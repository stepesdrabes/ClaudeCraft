package dev.claudecraft.legacy;

import dev.claudecraft.core.Platform;
import dev.claudecraft.core.game.Game;
import dev.claudecraft.core.ui.Image;
import dev.claudecraft.core.ui.TextField;
import dev.claudecraft.core.ui.TextMetrics;
import dev.claudecraft.core.view.Panel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
//? if >=1.12 {
/*import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundEvent;
*///?} else
import net.minecraft.util.ResourceLocation;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

final class LegacyPlatform implements Platform {
    private static final String MINECRAFT = /*$ minecraft*/ "1.8.9";

    private final String loader;
    private final Path gameDirectory;
    private final Path configDirectory;
    private final KeyBinding openKey;
    private final Game game = new LegacyGame();
    private final TextMetrics metrics = new LegacyMetrics();

    LegacyPlatform(String loader, Path gameDirectory, Path configDirectory, KeyBinding openKey) {
        this.loader = loader;
        this.gameDirectory = gameDirectory;
        this.configDirectory = configDirectory;
        this.openKey = openKey;
    }

    @Override
    public String minecraftVersion() {
        return MINECRAFT;
    }

    @Override
    public String loader() {
        return loader;
    }

    @Override
    public Path gameDirectory() {
        return gameDirectory;
    }

    @Override
    public Path configDirectory() {
        return configDirectory;
    }

    @Override
    public Executor mainThread() {
        return task -> Minecraft.getMinecraft().addScheduledTask(task);
    }

    @Override
    public Game game() {
        return game;
    }

    @Override
    public TextMetrics textMetrics() {
        return metrics;
    }

    @Override
    public TextField.Clipboard clipboard() {
        return new TextField.Clipboard() {
            @Override
            public String get() {
                return GuiScreen.getClipboardString();
            }

            @Override
            public void set(String text) {
                GuiScreen.setClipboardString(text);
            }
        };
    }

    @Override
    public String openKeyName() {
        return GameSettings.getKeyDisplayString(openKey.getKeyCode());
    }

    @Override
    public void showPanel(Panel panel) {
        Minecraft.getMinecraft().displayGuiScreen(new LegacyScreen(panel, openKey));
    }

    @Override
    public void closePanel() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen instanceof LegacyScreen) minecraft.displayGuiScreen(null);
    }

    @Override
    public void screenshot(Consumer<Image> done) {
        done.accept(LegacyTextures.capture());
    }

    @Override
    public void playSound(Sound sound) {
        //? if >=1.12 {
        /*SoundEvent note = sound == Sound.DONE ? SoundEvents.BLOCK_NOTE_BELL : sound == Sound.NEEDS_YOU ? SoundEvents.BLOCK_NOTE_CHIME : SoundEvents.BLOCK_NOTE_BASS;
        Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(note, pitch(sound)));
        *///?} else {
        String note = sound == Sound.DONE ? "note.pling" : sound == Sound.NEEDS_YOU ? "note.harp" : "note.bass";
        Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation(note), pitch(sound)));
        //?}
    }

    private static float pitch(Sound sound) {
        return sound == Sound.DONE ? 1.2f : sound == Sound.NEEDS_YOU ? 1.0f : 0.8f;
    }
}
