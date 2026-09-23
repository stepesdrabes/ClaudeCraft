package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.Connector;
import dev.claudecraft.agent.ConnectorInfo;
import dev.claudecraft.agent.Installation;
import dev.claudecraft.agent.LiveSession;
import dev.claudecraft.agent.McpServerInfo;
import dev.claudecraft.agent.Session;
import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.SessionSpec;
import dev.claudecraft.agent.SessionSummary;
import dev.claudecraft.agent.Usage;
import dev.claudecraft.agent.json.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ClaudeCodeConnector implements Connector {
    private static final List<ConnectorInfo.Mode> MODES = Collections.unmodifiableList(Arrays.asList(
        new ConnectorInfo.Mode("default", "Manual"),
        new ConnectorInfo.Mode("acceptEdits", "Accept edits"),
        new ConnectorInfo.Mode("plan", "Plan"),
        new ConnectorInfo.Mode("auto", "Auto")));
    private static final Json LIST_MODELS = Json.object().put("subtype", "list_models");
    private static final long MCP_SETTLE_MILLIS = 10_000;

    private final Transcripts transcripts = Transcripts.forCurrentUser();
    private final Map<Path, CompletableFuture<ConnectorInfo>> info = new ConcurrentHashMap<>();
    private final Set<ClaudeSession> open = Collections.newSetFromMap(new ConcurrentHashMap<ClaudeSession, Boolean>());
    private volatile String executablePath;
    private ClaudeCli cli;

    public ClaudeCodeConnector(String executablePath) {
        this.executablePath = executablePath;
    }

    @Override
    public String name() {
        return "Claude Code";
    }

    @Override
    public CompletableFuture<ConnectorInfo> info(Path workspace) {
        return info.computeIfAbsent(workspace, key -> probe(key, session -> session.initialization()
            .thenCombine(session.request(LIST_MODELS), this::parse)));
    }

    @Override
    public List<Installation> installations() {
        return ClaudeCli.installations();
    }

    @Override
    public synchronized void useExecutable(String path) {
        executablePath = path;
        cli = null;
        info.clear();
    }

    @Override
    public CompletableFuture<String> update() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String output = cli().run(Collections.singletonList("update"), 300).trim();
                return output.substring(output.lastIndexOf('\n') + 1);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                useExecutable(executablePath);
            }
        });
    }

    @Override
    public Session open(SessionSpec spec, SessionListener listener) {
        ClaudeSession session = ClaudeSession.start(cli(), spec, listener);
        open.removeIf(existing -> !existing.isOpen());
        open.add(session);
        session.initialization()
            .thenCombine(session.request(LIST_MODELS), this::parse)
            .thenAccept(result -> info.put(spec.workspace(), CompletableFuture.completedFuture(result)));
        return session;
    }

    @Override
    public List<SessionSummary> sessions(Path workspace, int limit) {
        return transcripts.list(workspace, limit);
    }

    @Override
    public void replay(Path cwd, String sessionId, SessionListener listener) {
        transcripts.replay(cwd, sessionId, listener);
    }

    @Override
    public void rename(Path cwd, String sessionId, String title) {
        try {
            transcripts.rename(cwd, sessionId, title);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(Path cwd, String sessionId) {
        try {
            transcripts.delete(cwd, sessionId);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<Path> recentWorkspaces(int limit) {
        return transcripts.recentWorkspaces(limit);
    }

    @Override
    public CompletableFuture<Usage.Plan> planUsage(Path workspace) {
        Json request = Json.object().put("subtype", "get_usage").put("skip_behaviors", true);
        open.removeIf(session -> !session.isOpen());
        ClaudeSession any = open.stream().findFirst().orElse(null);
        CompletableFuture<Json> usage = any != null ? any.request(request) : probe(workspace, session -> session.request(request));
        return usage.thenApply(Protocol::planUsage);
    }

    @Override
    public CompletableFuture<List<McpServerInfo>> mcpServers(Path workspace) {
        long deadline = System.currentTimeMillis() + MCP_SETTLE_MILLIS;
        return probe(workspace, session -> settled(session, deadline));
    }

    private static CompletableFuture<List<McpServerInfo>> settled(ClaudeSession session, long deadline) {
        return session.mcpServers().thenCompose(servers -> {
            boolean pending = servers.stream().anyMatch(server -> server.state() == McpServerInfo.State.PENDING);
            if (!pending || System.currentTimeMillis() > deadline) return CompletableFuture.completedFuture(servers);
            return CompletableFuture.runAsync(ClaudeCodeConnector::pause).thenCompose(ignored -> settled(session, deadline));
        });
    }

    private static void pause() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<LiveSession> liveSessions() {
        try {
            return LiveSessions.list(cli());
        } catch (IOException | IllegalStateException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public CompletableFuture<String> startBackground(SessionSpec spec, String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return LiveSessions.start(cli(), spec, prompt);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public void stopBackground(String id) {
        try {
            LiveSessions.stop(cli(), id);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void removeBackground(String id) {
        try {
            LiveSessions.remove(cli(), id);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private synchronized ClaudeCli cli() {
        if (cli == null) cli = ClaudeCli.locate(executablePath);
        return cli;
    }

    private <T> CompletableFuture<T> probe(Path workspace, Function<ClaudeSession, CompletableFuture<T>> query) {
        return CompletableFuture
            .supplyAsync(() -> ClaudeSession.start(cli(), new SessionSpec(workspace), new SessionListener() {
            }))
            .thenCompose(session -> query.apply(session).whenComplete((result, failure) -> session.close()))
            .whenComplete((result, failure) -> {
                if (failure != null) info.remove(workspace);
            });
    }

    private ConnectorInfo parse(Json response, Json listed) {
        List<ConnectorInfo.Model> models = new ArrayList<>();
        Json source = listed.get("models").size() > 0 ? listed.get("models") : response.get("models");
        for (Json model : source.items()) {
            List<String> efforts = new ArrayList<>();
            for (Json level : model.get("supportedEffortLevels").items()) efforts.add(level.asString(""));
            models.add(new ConnectorInfo.Model(
                model.get("value").asString(""),
                model.get("displayName").asString(model.get("value").asString("")).replace(" (disabled)", ""),
                model.get("description").asString(""),
                efforts,
                !model.get("disabled").asBoolean(false)));
        }
        List<ConnectorInfo.Command> commands = new ArrayList<>();
        for (Json command : response.get("commands").items()) {
            commands.add(new ConnectorInfo.Command(
                command.get("name").asString(""),
                command.get("argumentHint").asString(""),
                command.get("description").asString("")));
        }
        Json account = response.get("account");
        String plan = account.get("subscriptionType").asString();
        String accountLabel = account.get("email").asString("") + (plan != null ? " · " + plan : "");
        ClaudeCli current = cli();
        return new ConnectorInfo(models, MODES, commands, accountLabel, current.version(), current.executable());
    }
}
