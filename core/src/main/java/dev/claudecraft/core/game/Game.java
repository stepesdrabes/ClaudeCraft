package dev.claudecraft.core.game;

import dev.claudecraft.agent.json.Json;

import java.util.concurrent.CompletableFuture;

public interface Game {
    boolean inWorld();

    Json status();

    String block(int x, int y, int z);

    Json entities(double radius);

    CompletableFuture<String> runCommand(String command);

    void message(String text, boolean muted);
}
