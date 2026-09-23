package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.ConnectorInfo;
import dev.claudecraft.agent.ImageData;
import dev.claudecraft.agent.PermissionRequest;
import dev.claudecraft.agent.Session;
import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.SessionSpec;
import dev.claudecraft.agent.ToolOutput;
import dev.claudecraft.agent.ToolUse;
import dev.claudecraft.agent.TurnResult;
import dev.claudecraft.agent.tool.Schema;
import dev.claudecraft.agent.tool.Tool;
import dev.claudecraft.agent.tool.ToolResult;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "claudecraft.live", matches = "true")
class LiveSessionTest {
    @Test
    void runsATurnWithAHostTool() throws Exception {
        Path workspace = Files.createTempDirectory("claudecraft-live");
        ClaudeCodeConnector connector = new ClaudeCodeConnector(null);

        ConnectorInfo info = connector.info(workspace).get(60, TimeUnit.SECONDS);
        System.out.println("account: " + info.account() + ", models: " + info.models().size() + ", commands: " + info.commands().size());
        assertFalse(info.models().isEmpty());

        Tool secret = Tool.of("get_secret", "Returns the secret number", Schema.object(),
            arguments -> CompletableFuture.completedFuture(ToolResult.text("The secret is 42")));
        List<String> events = new CopyOnWriteArrayList<>();
        CompletableFuture<TurnResult> turn = new CompletableFuture<>();
        StringBuilder text = new StringBuilder();
        SessionSpec spec = new SessionSpec(workspace)
            .model("haiku")
            .title("Live test")
            .tools("minecraft", Collections.singletonList(secret));
        Session session = connector.open(spec, new SessionListener() {
            @Override
            public void onStarted(String sessionId, String model, String cwd) {
                events.add("started " + model);
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
            public void onToolResult(ToolOutput output) {
                events.add("result " + output.text());
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                events.add("permission " + request.toolName());
                request.allow(false);
            }

            @Override
            public void onTurnEnd(TurnResult result) {
                turn.complete(result);
            }
        });
        session.send("Call the get_secret tool, then reply with just the number.", Collections.<ImageData>emptyList());
        TurnResult result = turn.get(120, TimeUnit.SECONDS);
        session.close();

        System.out.println("events: " + events + "\ntext: " + text + "\nsummary: " + result.summary() + " cost: " + result.costUsd());
        assertEquals(TurnResult.Outcome.COMPLETED, result.outcome());
        assertTrue(events.contains("tool mcp__minecraft__get_secret"));
        assertTrue(text.toString().contains("42"));
        assertFalse(connector.sessions(workspace, 10).isEmpty());
    }
}
