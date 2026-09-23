package dev.claudecraft.platform;

import dev.claudecraft.core.Platform;
import dev.claudecraft.core.game.Game;
import dev.claudecraft.core.ui.TextField;
import dev.claudecraft.core.ui.TextMetrics;
import dev.claudecraft.core.view.Panel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.nio.file.Path;
import java.util.concurrent.Executor;

final class MinecraftPlatform implements Platform {
    private static final String MINECRAFT = /*$ minecraft*/ "26.2";

    private final String loader;
    private final Path gameDirectory;
    private final Path configDirectory;
    private final KeyMapping openKey;
    private final Game game = new MinecraftGame();
    private final TextMetrics metrics = new FontMetrics();

    MinecraftPlatform(String loader, Path gameDirectory, Path configDirectory, KeyMapping openKey) {
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
        return task -> Minecraft.getInstance().execute(task);
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
                return Minecraft.getInstance().keyboardHandler.getClipboard();
            }

            @Override
            public void set(String text) {
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
            }
        };
    }

    @Override
    public String openKeyName() {
        return openKey.getTranslatedKeyMessage().getString();
    }

    @Override
    public void showPanel(Panel panel) {
        setScreen(new ClaudeScreen(panel, openKey));
    }

    @Override
    public void closePanel() {
        if (currentScreen() instanceof ClaudeScreen) setScreen(null);
    }

    static Screen currentScreen() {
        //? if >=26.2 {
        return Minecraft.getInstance().gui.screen();
        //?} else
        //return Minecraft.getInstance().screen;
    }

    private static void setScreen(Screen screen) {
        //? if >=26.2 {
        Minecraft.getInstance().gui.setScreen(screen);
        //?} else
        //Minecraft.getInstance().setScreen(screen);
    }

    @Override
    public void playSound(Sound sound) {
        switch (sound) {
            case DONE:
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.2f));
                break;
            case NEEDS_YOU:
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.0f));
                break;
            default:
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.8f));
        }
    }
}
