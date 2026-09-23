package dev.claudecraft.core;

import dev.claudecraft.agent.Connector;
import dev.claudecraft.agent.ConnectorInfo;
import dev.claudecraft.agent.Session;
import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.SessionSpec;
import dev.claudecraft.agent.TurnResult;
import dev.claudecraft.agent.claude.ClaudeCodeConnector;
import dev.claudecraft.agent.mcp.McpHttpServer;
import dev.claudecraft.agent.mcp.McpServer;
import dev.claudecraft.agent.tool.Tool;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.chat.Chats;
import dev.claudecraft.core.chat.Status;
import dev.claudecraft.core.game.MinecraftTools;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.view.Hud;
import dev.claudecraft.core.view.Panel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class ClaudeCraft implements Chat.Host {
    public static final String VERSION = "0.1.0";
    private static final long PING_WINDOW_MILLIS = 60_000;

    private final Platform platform;
    private final Config config;
    private final Connector connector;
    private final List<Tool> tools;
    private final Path gameWorkspace;
    private final Chats chats;
    private final Hud hud;
    private final ExecutorService background = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "claudecraft-worker");
        thread.setDaemon(true);
        return thread;
    });
    private volatile ConnectorInfo info;
    private volatile String connectorError;
    private volatile McpHttpServer mcp;
    private Panel panel;
    private Chat pinged;
    private long pingedAt;

    private ClaudeCraft(Platform platform) {
        this.platform = platform;
        this.config = Config.load(platform.configDirectory().resolve("claudecraft.json"));
        this.connector = new ClaudeCodeConnector(config.claudePath());
        this.tools = new MinecraftTools(platform.game(), platform.mainThread()).all();
        this.gameWorkspace = createGameWorkspace(platform.gameDirectory());
        this.chats = new Chats(this, connector, platform.mainThread(), background, config.workspace(gameWorkspace));
        this.hud = new Hud(this);
    }

    public static ClaudeCraft start(Platform platform) {
        ClaudeCraft app = new ClaudeCraft(platform);
        app.chats.refresh();
        app.refreshInfo();
        if (app.config.mcpServer()) app.background.execute(app::startMcpServer);
        Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown, "claudecraft-shutdown"));
        return app;
    }

    public void openKeyPressed() {
        if (panel != null) return;
        if (pinged != null && System.currentTimeMillis() - pingedAt < PING_WINDOW_MILLIS) chats.select(pinged);
        pinged = null;
        panel = new Panel(this);
        platform.showPanel(panel);
    }

    public void panelClosed() {
        panel = null;
    }

    public void renderHud(Canvas canvas, int width, int height) {
        if (panel == null) hud.render(canvas, width, height, System.currentTimeMillis());
    }

    private void shutdown() {
        chats.closeAll();
        if (mcp != null) mcp.close();
        background.shutdownNow();
    }

    public void switchWorkspace(Path workspace) {
        chats.setWorkspace(workspace);
        config.setWorkspace(workspace);
        refreshInfo();
    }

    public void refreshInfo() {
        Path workspace = chats.workspace();
        connector.info(workspace).whenComplete((result, failure) -> platform.mainThread().execute(() -> {
            if (result != null) info = result;
            connectorError = failure != null ? rootMessage(failure) : null;
        }));
    }

    public List<Path> recentWorkspaces() {
        return connector.recentWorkspaces(20).stream()
            .filter(path -> !isTemporary(path))
            .limit(12)
            .collect(Collectors.toList());
    }

    private static boolean isTemporary(Path path) {
        String normalized = path.toString();
        String temp = System.getProperty("java.io.tmpdir", "/tmp");
        return normalized.startsWith(temp) || normalized.matches("^(/private)?/(tmp|var/folders)/.*");
    }

    @Override
    public Session open(SessionSpec spec, SessionListener listener) {
        if (spec.model() == null) spec.model(config.model());
        if (spec.permissionMode() == null) spec.permissionMode(config.permissionMode());
        spec.tools(MinecraftTools.NAMESPACE, tools).instructions(instructions()).callbacks(platform.mainThread());
        Session session = connector.open(spec, listener);
        refreshInfo();
        return session;
    }

    @Override
    public void finished(Chat chat, TurnResult result) {
        Status status = chat.status();
        if (status == Status.DONE) ping(chat, Platform.Sound.DONE, result.summary());
        else if (status == Status.FAILED) ping(chat, Platform.Sound.FAILED, result.error());
    }

    @Override
    public void needsYou(Chat chat) {
        ping(chat, Platform.Sound.NEEDS_YOU, chat.permission() != null ? "Allow " + chat.permission().title() + "?" : "Claude has a question");
    }

    private void ping(Chat chat, Platform.Sound sound, String detail) {
        if (config.sounds()) platform.playSound(sound);
        boolean watching = panel != null && chats.selected() == chat;
        if (watching || !config.toasts()) return;
        pinged = chat;
        pingedAt = System.currentTimeMillis();
        hud.toast(chat, detail);
    }

    private String instructions() {
        return "You are Claude, running inside Minecraft " + platform.minecraftVersion() + " (" + platform.loader() + ") "
            + "through the ClaudeCraft mod. The player chats with you from an in-game panel while they play, so keep replies "
            + "short and skimmable. Use the mcp__minecraft__* tools to see and change their world: call status first to learn "
            + "where the player is and which way they face, build with run_command using absolute coordinates, check your work "
            + "with read_blocks, and use say to tell the player in chat when a longer task is finished.";
    }

    private void startMcpServer() {
        try {
            McpServer server = new McpServer(MinecraftTools.NAMESPACE, VERSION,
                "The player's live Minecraft game. Call status first, then act with run_command and verify with read_blocks.", tools);
            mcp = McpHttpServer.start(server, config.mcpPort());
        } catch (IOException e) {
            mcp = null;
        }
    }

    private static Path createGameWorkspace(Path gameDirectory) {
        Path workspace = gameDirectory.resolve("claudecraft");
        try {
            Files.createDirectories(workspace);
        } catch (IOException ignored) {
        }
        return workspace.toAbsolutePath().normalize();
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    public Platform platform() {
        return platform;
    }

    public Config config() {
        return config;
    }

    public Chats chats() {
        return chats;
    }

    public Hud hud() {
        return hud;
    }

    public Path gameWorkspace() {
        return gameWorkspace;
    }

    public ConnectorInfo info() {
        return info;
    }

    public String connectorError() {
        return connectorError;
    }

    public String mcpUrl() {
        McpHttpServer server = mcp;
        return server != null ? server.url() : null;
    }
}
