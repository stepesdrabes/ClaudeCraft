package dev.claudecraft.core;

import dev.claudecraft.agent.Connector;
import dev.claudecraft.agent.ConnectorInfo;
import dev.claudecraft.agent.Installation;
import dev.claudecraft.agent.McpServerInfo;
import dev.claudecraft.agent.Session;
import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.SessionSpec;
import dev.claudecraft.agent.TurnResult;
import dev.claudecraft.agent.Usage;
import dev.claudecraft.agent.claude.ClaudeCodeConnector;
import dev.claudecraft.agent.mcp.McpHttpServer;
import dev.claudecraft.agent.mcp.McpServer;
import dev.claudecraft.agent.tool.Tool;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.chat.Chats;
import dev.claudecraft.core.chat.Status;
import dev.claudecraft.core.game.MinecraftTools;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Emoji;
import dev.claudecraft.core.ui.EmojiCanvas;
import dev.claudecraft.core.ui.Image;
import dev.claudecraft.core.view.Hud;
import dev.claudecraft.core.view.Panel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ClaudeCraft implements Chat.Host {
    public static final String VERSION = "0.2.0";
    private static final long PING_WINDOW_MILLIS = 60_000;
    private static final long USAGE_REFRESH_MILLIS = 60_000;

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
    private final Map<Path, List<McpServerInfo>> workspaceServers = new ConcurrentHashMap<>();
    private final List<Consumer<Image>> captures = new ArrayList<>();
    private int captureFrames;
    private volatile ConnectorInfo info;
    private volatile String connectorError;
    private volatile McpHttpServer mcp;
    private volatile Usage.Plan planUsage;
    private volatile List<Installation> installations;
    private volatile boolean searchingInstallations;
    private boolean updating;
    private String updateResult;
    private long planUsageAt;
    private Panel panel;
    private Chat pinged;
    private long pingedAt;

    private ClaudeCraft(Platform platform) {
        this.platform = platform;
        this.config = Config.load(platform.configDirectory().resolve("claudecraft.json"));
        this.connector = new ClaudeCodeConnector(config.claudePath());
        this.tools = new MinecraftTools(platform.game(), platform.mainThread(), this::captureView).all();
        this.gameWorkspace = createGameWorkspace(platform.gameDirectory());
        this.chats = new Chats(this, connector, platform.mainThread(), background, config.workspace(gameWorkspace),
            config.archived(), config::setArchived);
        this.hud = new Hud(this);
    }

    public static ClaudeCraft start(Platform platform) {
        ClaudeCraft app = new ClaudeCraft(platform);
        Emoji.get().load(app.background);
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
        refreshPlanUsage(false);
        platform.showPanel(panel);
    }

    public void panelClosed() {
        panel = null;
    }

    public void renderHud(Canvas canvas, int width, int height) {
        long now = System.currentTimeMillis();
        chats.tick(now, panel != null);
        if (panel == null && !beginFrame()) hud.render(new EmojiCanvas(canvas), width, height, now);
    }

    public boolean beginFrame() {
        if (captureFrames == 0) return false;
        if (--captureFrames > 0) return true;
        List<Consumer<Image>> pending = new ArrayList<>(captures);
        captures.clear();
        platform.screenshot(image -> pending.forEach(callback -> callback.accept(image)));
        return false;
    }

    public void tick(long now) {
        chats.tick(now, true);
    }

    public void captureView(Consumer<Image> callback) {
        captures.add(callback);
        if (captureFrames == 0) captureFrames = 2;
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

    public void useExecutable(String path) {
        config.setClaudePath(path);
        connector.useExecutable(path);
        info = null;
        installations = null;
        refreshInfo();
    }

    public void updateClaude() {
        if (updating) return;
        updating = true;
        updateResult = null;
        connector.update().whenComplete((output, failure) -> platform.mainThread().execute(() -> {
            updating = false;
            updateResult = failure != null ? rootMessage(failure) : output;
            info = null;
            installations = null;
            refreshInfo();
        }));
    }

    public boolean updating() {
        return updating;
    }

    public String updateResult() {
        return updateResult;
    }

    public List<Installation> installations() {
        if (!searchingInstallations && installations == null) {
            searchingInstallations = true;
            background.execute(() -> {
                installations = connector.installations();
                searchingInstallations = false;
            });
        }
        return installations;
    }

    public void refreshPlanUsage(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - planUsageAt < USAGE_REFRESH_MILLIS) return;
        planUsageAt = now;
        connector.planUsage(chats.workspace()).thenAccept(usage -> {
            if (usage != null) planUsage = usage;
        });
    }

    public List<McpServerInfo> mcpServers(Chat chat) {
        if (chat.isOpen() && chat.mcpServers() != null) return chat.mcpServers();
        Path workspace = chat.cwd();
        if (!workspaceServers.containsKey(workspace)) {
            workspaceServers.put(workspace, Collections.<McpServerInfo>emptyList());
            connector.mcpServers(workspace).thenAccept(servers -> {
                List<McpServerInfo> all = new ArrayList<>();
                all.add(new McpServerInfo(MinecraftTools.NAMESPACE, McpServerInfo.State.CONNECTED, "built in"));
                all.addAll(servers);
                workspaceServers.put(workspace, all);
            });
        }
        return workspaceServers.get(workspace);
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
        Session session = connector.open(withDefaults(spec).tools(MinecraftTools.NAMESPACE, tools).callbacks(platform.mainThread()), listener);
        refreshInfo();
        return session;
    }

    public CompletableFuture<String> startBackground(SessionSpec spec, String prompt) {
        String url = mcpUrl();
        if (url != null) spec.tools(MinecraftTools.NAMESPACE, Collections.<Tool>emptyList()).toolsUrl(url);
        return connector.startBackground(withDefaults(spec), prompt);
    }

    private SessionSpec withDefaults(SessionSpec spec) {
        if (spec.model() == null) spec.model(config.model());
        if (spec.effort() == null) spec.effort(config.effort());
        if (spec.permissionMode() == null) spec.permissionMode(config.permissionMode());
        return spec.instructions(instructions());
    }

    @Override
    public void finished(Chat chat, TurnResult result) {
        Status status = chat.status();
        if (status == Status.DONE) ping(chat, Platform.Sound.DONE, result.summary());
        else if (status == Status.FAILED) ping(chat, Platform.Sound.FAILED, result.error());
    }

    @Override
    public void needsYou(Chat chat) {
        String detail;
        if (chat.inBackground()) detail = "Waiting in the background";
        else if (chat.permission() != null) detail = "Allow " + chat.permission().title() + "?";
        else detail = "Claude has a question";
        ping(chat, Platform.Sound.NEEDS_YOU, detail);
    }

    @Override
    public void planUsage(Usage.Plan usage) {
        Usage.Plan current = planUsage;
        if (current == null) {
            planUsage = usage;
            return;
        }
        List<Usage.Limit> merged = new ArrayList<>();
        for (Usage.Limit limit : current.limits()) {
            Usage.Limit update = usage.limits().stream().filter(l -> l.label().equals(limit.label())).findFirst().orElse(limit);
            merged.add(update);
        }
        planUsage = new Usage.Plan(merged);
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
            + "with read_blocks or screenshot, and use say to tell the player in chat when a longer task is finished.";
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

    public Usage.Plan planUsage() {
        return planUsage;
    }

    public String mcpUrl() {
        McpHttpServer server = mcp;
        return server != null ? server.url() : null;
    }
}
