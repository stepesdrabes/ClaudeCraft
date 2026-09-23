package dev.claudecraft.core.game;

import dev.claudecraft.agent.json.Json;
import dev.claudecraft.agent.tool.Schema;
import dev.claudecraft.agent.tool.Tool;
import dev.claudecraft.agent.tool.ToolResult;

import dev.claudecraft.core.ui.Image;
import dev.claudecraft.core.ui.Images;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MinecraftTools {
    public static final String NAMESPACE = "minecraft";
    private static final int MAX_SCAN_VOLUME = 32 * 32 * 32;
    private static final int MAX_LAYERED_VOLUME = 16 * 16 * 16;
    private static final String PALETTE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SCREENSHOT_SIZE = 1568;
    private static final ScheduledExecutorService TIMEOUTS = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "claudecraft-timeouts");
        thread.setDaemon(true);
        return thread;
    });

    private final Game game;
    private final Executor mainThread;
    private final Consumer<Consumer<Image>> capture;

    public MinecraftTools(Game game, Executor mainThread, Consumer<Consumer<Image>> capture) {
        this.game = game;
        this.mainThread = mainThread;
        this.capture = capture;
    }

    public List<Tool> all() {
        return Arrays.asList(
            Tool.of("status",
                "The player's live state: name, position, facing, dimension, game mode, health, food, held item, "
                    + "the block or entity they are looking at, time of day and whether the world is singleplayer. "
                    + "Call this first to orient yourself before building.",
                Schema.object(),
                args -> onMain(() -> ToolResult.text(game.status().toString()))),
            Tool.of("run_command",
                "Run a Minecraft command as the player, e.g. \"fill 10 64 10 14 68 14 minecraft:stone\". "
                    + "Use absolute coordinates from status. Returns the game's feedback. The player sees every command in chat.",
                Schema.object().required("command", "string", "The command, without the leading slash"),
                this::runCommand),
            Tool.of("read_blocks",
                "Read the blocks in a box between two corners (inclusive, at most 32x32x32). Small boxes return a "
                    + "layer-by-layer map with a legend; large boxes return block counts.",
                Schema.object()
                    .required("x1", "integer", "First corner X").required("y1", "integer", "First corner Y").required("z1", "integer", "First corner Z")
                    .required("x2", "integer", "Second corner X").required("y2", "integer", "Second corner Y").required("z2", "integer", "Second corner Z"),
                args -> onMain(() -> readBlocks(args))),
            Tool.of("nearby_entities",
                "List entities around the player: type, name, position and distance.",
                Schema.object().optional("radius", "number", "Search radius in blocks, 1-64 (default 16)"),
                args -> onMain(() -> ToolResult.text(game.entities(clamp(args.get("radius").asDouble(16), 1, 64)).toString()))),
            Tool.of("screenshot",
                "See what the player sees right now: a screenshot of the game view without the ClaudeCraft panel. "
                    + "Use it to check how a build looks.",
                Schema.object(),
                args -> screenshot()),
            Tool.of("say",
                "Show a message in the player's chat. Only the player sees it. Use it to announce when a long task is done.",
                Schema.object().required("message", "string", "The message to show"),
                args -> onMain(() -> {
                    game.message(args.get("message").asString(""), false);
                    return ToolResult.text("Shown to the player.");
                })));
    }

    private CompletableFuture<ToolResult> screenshot() {
        CompletableFuture<ToolResult> result = new CompletableFuture<>();
        mainThread.execute(() -> {
            if (!game.inWorld()) {
                result.complete(ToolResult.error("The player is not in a world right now."));
                return;
            }
            capture.accept(image -> {
                try {
                    result.complete(ToolResult.image(Images.jpeg(Images.fit(image, SCREENSHOT_SIZE)), "image/jpeg",
                        "The player's view (" + image.width() + "x" + image.height() + ")"));
                } catch (IOException e) {
                    result.complete(ToolResult.error("Could not encode the screenshot: " + e.getMessage()));
                }
            });
        });
        TIMEOUTS.schedule(() -> result.complete(ToolResult.error("Minecraft is not rendering right now (is the window minimized?)")), 10, TimeUnit.SECONDS);
        return result;
    }

    private CompletableFuture<ToolResult> runCommand(Json args) {
        String command = args.get("command").asString("").trim().replaceFirst("^/", "");
        if (command.isEmpty()) return CompletableFuture.completedFuture(ToolResult.error("Empty command"));
        return CompletableFuture.supplyAsync(() -> {
            requireWorld();
            game.message("/" + command, true);
            return game.runCommand(command);
        }, mainThread).thenCompose(result -> result).thenApply(ToolResult::text);
    }

    private ToolResult readBlocks(Json args) {
        int x1 = args.get("x1").asInt(0), y1 = args.get("y1").asInt(0), z1 = args.get("z1").asInt(0);
        int x2 = args.get("x2").asInt(0), y2 = args.get("y2").asInt(0), z2 = args.get("z2").asInt(0);
        int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
        int sizeX = Math.abs(x2 - x1) + 1, sizeY = Math.abs(y2 - y1) + 1, sizeZ = Math.abs(z2 - z1) + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > MAX_SCAN_VOLUME) return ToolResult.error("Box too large: " + volume + " blocks (max " + MAX_SCAN_VOLUME + ")");
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Character> legend = new LinkedHashMap<>();
        StringBuilder layers = new StringBuilder();
        for (int y = minY; y < minY + sizeY; y++) {
            layers.append("y=").append(y).append('\n');
            for (int z = minZ; z < minZ + sizeZ; z++) {
                layers.append("  z=").append(z).append(": ");
                for (int x = minX; x < minX + sizeX; x++) {
                    String block = game.block(x, y, z);
                    counts.merge(block, 1, Integer::sum);
                    layers.append(symbol(block, legend));
                }
                layers.append('\n');
            }
        }
        StringBuilder out = new StringBuilder()
            .append("Box from ").append(minX).append(' ').append(minY).append(' ').append(minZ)
            .append(" to ").append(minX + sizeX - 1).append(' ').append(minY + sizeY - 1).append(' ').append(minZ + sizeZ - 1)
            .append(" (").append(sizeX).append('x').append(sizeY).append('x').append(sizeZ).append(")\nCounts: ")
            .append(Json.of(counts)).append('\n');
        if (volume <= MAX_LAYERED_VOLUME) {
            out.append("Each row runs from x=").append(minX).append(" to x=").append(minX + sizeX - 1).append(". Legend: ");
            legend.forEach((block, symbol) -> out.append(symbol).append('=').append(block).append(' '));
            out.append('\n').append(layers);
        }
        return ToolResult.text(out.toString());
    }

    private static char symbol(String block, Map<String, Character> legend) {
        if (block.endsWith(":air") || block.endsWith(":cave_air") || block.endsWith(":void_air")) return '.';
        Character known = legend.get(block);
        if (known != null) return known;
        char next = legend.size() < PALETTE.length() ? PALETTE.charAt(legend.size()) : '?';
        legend.put(block, next);
        return next;
    }

    private CompletableFuture<ToolResult> onMain(Supplier<ToolResult> work) {
        return CompletableFuture.supplyAsync(() -> {
            requireWorld();
            return work.get();
        }, mainThread);
    }

    private void requireWorld() {
        if (!game.inWorld()) throw new IllegalStateException("The player is not in a world right now.");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
