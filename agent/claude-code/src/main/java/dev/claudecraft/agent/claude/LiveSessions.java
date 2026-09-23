package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.LiveSession;
import dev.claudecraft.agent.SessionSpec;
import dev.claudecraft.agent.json.Json;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LiveSessions {
    private static final Pattern BACKGROUNDED = Pattern.compile("backgrounded\\W+([0-9a-f]{6,})");

    private LiveSessions() {
    }

    static List<LiveSession> list(ClaudeCli cli) throws IOException {
        String output = cli.run(Arrays.asList("agents", "--json", "--all"), 15);
        int start = output.indexOf('[');
        List<LiveSession> sessions = new ArrayList<>();
        if (start < 0) return sessions;
        for (Json session : Json.parse(output.substring(start)).items()) {
            String sessionId = session.get("sessionId").asString();
            String cwd = session.get("cwd").asString();
            if (sessionId == null || cwd == null || !session.get("pid").isNumber()) continue;
            boolean background = "background".equals(session.get("kind").asString());
            sessions.add(new LiveSession(session.get("id").asString(sessionId), sessionId, Paths.get(cwd), background, state(session)));
        }
        return sessions;
    }

    private static LiveSession.State state(Json session) {
        switch (session.get("state").asString("")) {
            case "blocked": return LiveSession.State.NEEDS_YOU;
            case "failed": return LiveSession.State.FAILED;
            case "working": return LiveSession.State.WORKING;
            default: return "busy".equals(session.get("status").asString()) ? LiveSession.State.WORKING : LiveSession.State.IDLE;
        }
    }

    static String start(ClaudeCli cli, SessionSpec spec, String prompt) throws IOException {
        List<String> args = new ArrayList<>(Arrays.asList("--bg"));
        args.addAll(ClaudeSession.options(spec));
        if (spec.toolsUrl() != null) {
            String namespace = spec.toolNamespace();
            Json server = Json.object().put("type", "http").put("url", spec.toolsUrl());
            args.add("--mcp-config=" + Json.object().put("mcpServers", Json.object().put(namespace, server)));
            args.add("--allowedTools=mcp__" + namespace);
        }
        args.add(prompt);
        String output = cli.run(args, spec.workspace(), 60);
        Matcher matcher = BACKGROUNDED.matcher(output);
        if (!matcher.find()) throw new IOException(output.trim().isEmpty() ? "Claude Code did not start a background session" : output.trim());
        return matcher.group(1);
    }

    static void stop(ClaudeCli cli, String id) throws IOException {
        cli.run(Arrays.asList("stop", id), 30);
    }

    static void remove(ClaudeCli cli, String id) throws IOException {
        cli.run(Arrays.asList("rm", id), 30);
    }
}
