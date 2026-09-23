package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.Connector;
import dev.claudecraft.agent.ConnectorInfo;
import dev.claudecraft.agent.Session;
import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.SessionSpec;
import dev.claudecraft.agent.SessionSummary;
import dev.claudecraft.agent.json.Json;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaudeCodeConnector implements Connector {
    private static final List<ConnectorInfo.Mode> MODES = Collections.unmodifiableList(Arrays.asList(
        new ConnectorInfo.Mode("default", "Manual"),
        new ConnectorInfo.Mode("acceptEdits", "Accept edits"),
        new ConnectorInfo.Mode("plan", "Plan"),
        new ConnectorInfo.Mode("auto", "Auto")));

    private final String executablePath;
    private final Transcripts transcripts = Transcripts.forCurrentUser();
    private final Map<Path, CompletableFuture<ConnectorInfo>> info = new ConcurrentHashMap<>();
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
        return info.computeIfAbsent(workspace, this::probe);
    }

    @Override
    public Session open(SessionSpec spec, SessionListener listener) {
        ClaudeSession session = ClaudeSession.start(cli(), spec, listener);
        session.initialization().thenAccept(response -> info.put(spec.workspace(), CompletableFuture.completedFuture(parse(response))));
        return session;
    }

    @Override
    public List<SessionSummary> sessions(Path workspace, int limit) {
        return transcripts.list(workspace, limit);
    }

    @Override
    public void replay(Path workspace, String sessionId, SessionListener listener) {
        transcripts.replay(workspace, sessionId, listener);
    }

    @Override
    public List<Path> recentWorkspaces(int limit) {
        return transcripts.recentWorkspaces(limit);
    }

    private synchronized ClaudeCli cli() {
        if (cli == null) cli = ClaudeCli.locate(executablePath);
        return cli;
    }

    private CompletableFuture<ConnectorInfo> probe(Path workspace) {
        return CompletableFuture
            .supplyAsync(() -> ClaudeSession.start(cli(), new SessionSpec(workspace), new SessionListener() {
            }))
            .thenCompose(session -> session.initialization().whenComplete((response, failure) -> session.close()))
            .thenApply(ClaudeCodeConnector::parse)
            .whenComplete((result, failure) -> {
                if (failure != null) info.remove(workspace);
            });
    }

    private static ConnectorInfo parse(Json response) {
        List<ConnectorInfo.Model> models = new ArrayList<>();
        for (Json model : response.get("models").items()) {
            if (model.get("disabled").asBoolean(false)) continue;
            models.add(new ConnectorInfo.Model(
                model.get("value").asString(""),
                model.get("displayName").asString(model.get("value").asString("")),
                model.get("description").asString("")));
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
        return new ConnectorInfo(models, MODES, commands, accountLabel);
    }
}
