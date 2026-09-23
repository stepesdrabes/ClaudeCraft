package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.ConnectorInfo;
import dev.claudecraft.agent.ImageData;
import dev.claudecraft.agent.Installation;
import dev.claudecraft.agent.LiveSession;
import dev.claudecraft.agent.McpServerInfo;
import dev.claudecraft.agent.PermissionRequest;
import dev.claudecraft.agent.Session;
import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.SessionSpec;
import dev.claudecraft.agent.SessionSummary;
import dev.claudecraft.agent.Task;
import dev.claudecraft.agent.ToolOutput;
import dev.claudecraft.agent.ToolUse;
import dev.claudecraft.agent.TurnResult;
import dev.claudecraft.agent.Usage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "claudecraft.live", matches = "true")
class LiveFeaturesTest {
    private final ClaudeCodeConnector connector = new ClaudeCodeConnector(null);

    @Test
    void reportsModelsVersionAndInstallations() throws Exception {
        Path workspace = Files.createTempDirectory("claudecraft-live");
        ConnectorInfo info = connector.info(workspace).get(60, TimeUnit.SECONDS);
        System.out.println("version " + info.version() + " at " + info.executable());
        for (ConnectorInfo.Model model : info.models()) {
            System.out.println("  " + model.id() + " | " + model.label() + " | " + model.available() + " | " + model.effortLevels() + " | " + model.description());
        }
        assertNotNull(info.version());
        assertTrue(info.models().stream().anyMatch(model -> !model.effortLevels().isEmpty()));
        List<Installation> installations = connector.installations();
        installations.forEach(i -> System.out.println("install " + i.version() + " " + i.path()));
        assertFalse(installations.isEmpty());
        List<McpServerInfo> servers = connector.mcpServers(workspace).get(60, TimeUnit.SECONDS);
        servers.forEach(s -> System.out.println("mcp " + s.name() + " " + s.state()));
        Usage.Plan plan = connector.planUsage(workspace).get(60, TimeUnit.SECONDS);
        if (plan != null) plan.limits().forEach(l -> System.out.println("limit " + l.label() + " " + l.percent() + "%"));
    }

    @Test
    void runsSubagentsTracksContextAndForks() throws Exception {
        Path workspace = Files.createTempDirectory("claudecraft-live");
        Recorder recorder = new Recorder();
        Session session = connector.open(new SessionSpec(workspace).model("haiku").effort("low").title("Live features"), recorder);
        session.setEffort("medium");
        session.send("Launch exactly one Agent subagent (general-purpose) with the prompt 'Run the bash command echo pong and report its output.' and wait for it. "
            + "Then reply with just the subagent's answer.", Collections.<ImageData>emptyList());
        TurnResult result = recorder.turn.get(180, TimeUnit.SECONDS);
        Usage.Context context = session.contextUsage().get(30, TimeUnit.SECONDS);
        List<McpServerInfo> servers = session.mcpServers().get(30, TimeUnit.SECONDS);
        session.close();
        System.out.println("events " + recorder.events + "\ncontext " + context.used() + "/" + context.max() + " servers " + servers.size());
        assertEquals(TurnResult.Outcome.COMPLETED, result.outcome());
        assertTrue(recorder.events.stream().anyMatch(event -> event.startsWith("task AGENT DONE")));
        assertTrue(recorder.events.stream().anyMatch(event -> event.startsWith("child ")));
        assertTrue(context.max() > 0 && context.used() > 0);

        String sessionId = recorder.sessionId;
        connector.rename(workspace, sessionId, "Renamed live test");
        assertEquals("Renamed live test", title(workspace, sessionId));

        Recorder forked = new Recorder();
        Session fork = connector.open(new SessionSpec(workspace).model("haiku").resume(sessionId).fork(true), forked);
        fork.send("What did the subagent answer? One word.", Collections.<ImageData>emptyList());
        forked.turn.get(120, TimeUnit.SECONDS);
        fork.close();
        System.out.println("fork " + forked.sessionId + " text " + forked.text);
        assertFalse(sessionId.equals(forked.sessionId));
        assertTrue(forked.text.toString().toLowerCase().contains("pong"));

        connector.delete(workspace, forked.sessionId);
        assertTrue(connector.sessions(workspace, 10).stream().noneMatch(summary -> summary.id().equals(forked.sessionId)));
    }

    @Test
    void startsListsAndStopsBackgroundSessions() throws Exception {
        Path workspace = Files.createTempDirectory("claudecraft-live");
        Process git = new ProcessBuilder("sh", "-c", "git init -q && git commit -q --allow-empty -m init").directory(workspace.toFile()).start();
        assertEquals(0, git.waitFor());
        String id = connector.startBackground(new SessionSpec(workspace).model("haiku").worktree(""), "Reply with the word ready.").get(60, TimeUnit.SECONDS);
        System.out.println("background " + id);
        LiveSession live = null;
        for (int i = 0; i < 20 && live == null; i++) {
            for (LiveSession session : connector.liveSessions()) if (session.id().equals(id)) live = session;
            if (live == null) Thread.sleep(500);
        }
        assertNotNull(live);
        System.out.println("live " + live.sessionId() + " " + live.state() + " " + live.cwd() + " background " + live.background());
        String sessionId = live.sessionId();
        try {
            assertTrue(live.background());
            SessionSummary summary = null;
            for (int i = 0; i < 30 && summary == null; i++) {
                for (SessionSummary candidate : connector.sessions(workspace, 10)) if (candidate.id().equals(sessionId)) summary = candidate;
                if (summary == null) Thread.sleep(1000);
            }
            assertNotNull(summary);
            System.out.println("transcript in " + summary.cwd());
            assertTrue(summary.cwd().toString().contains("worktrees"));
        } finally {
            connector.stopBackground(id);
        }
        Thread.sleep(1000);
        assertTrue(connector.liveSessions().stream().noneMatch(session -> session.sessionId().equals(sessionId)));
    }

    private String title(Path workspace, String sessionId) {
        for (SessionSummary summary : connector.sessions(workspace, 10)) if (summary.id().equals(sessionId)) return summary.title();
        return null;
    }

    private static final class Recorder implements SessionListener {
        final List<String> events = new CopyOnWriteArrayList<>();
        final CompletableFuture<TurnResult> turn = new CompletableFuture<>();
        final StringBuilder text = new StringBuilder();
        volatile String sessionId;

        @Override
        public void onStarted(String sessionId, String model, String cwd) {
            this.sessionId = sessionId;
        }

        @Override
        public void onText(String delta) {
            text.append(delta);
        }

        @Override
        public void onToolUse(ToolUse use) {
            events.add("tool " + use.name());
        }

        @Override
        public void onSubagentToolUse(String parentToolUseId, ToolUse use) {
            events.add("child " + use.name());
        }

        @Override
        public void onToolResult(ToolOutput output) {
            events.add("result " + output.text().replace('\n', ' '));
        }

        @Override
        public void onTask(Task task) {
            events.add("task " + task.kind() + " " + task.status() + " " + task.toolUses());
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.allow(false);
        }

        @Override
        public void onTurnEnd(TurnResult result) {
            turn.complete(result);
        }
    }
}
