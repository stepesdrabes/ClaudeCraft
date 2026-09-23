package dev.claudecraft.core;

import dev.claudecraft.core.game.Game;
import dev.claudecraft.core.ui.TextField;
import dev.claudecraft.core.ui.TextMetrics;
import dev.claudecraft.core.view.Panel;

import java.nio.file.Path;
import java.util.concurrent.Executor;

public interface Platform {
    enum Sound { DONE, NEEDS_YOU, FAILED }

    String minecraftVersion();

    String loader();

    Path gameDirectory();

    Path configDirectory();

    Executor mainThread();

    Game game();

    TextMetrics textMetrics();

    TextField.Clipboard clipboard();

    String openKeyName();

    void showPanel(Panel panel);

    void closePanel();

    void playSound(Sound sound);
}
