package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.Task;
import dev.claudecraft.agent.json.Json;

import java.util.HashMap;
import java.util.Map;

final class Tasks {
    private final Map<String, Entry> entries = new HashMap<>();

    private static final class Entry {
        String id;
        String toolUseId;
        Task.Kind kind = Task.Kind.OTHER;
        String description = "";
        String agentType;
        Task.Status status = Task.Status.RUNNING;
        boolean background;
        String activity;
        int toolUses;
        long tokens;
        long startedAt = System.currentTimeMillis();
        long durationMillis;

        Task snapshot() {
            return new Task(id, toolUseId, kind, description, agentType, status, background, activity, toolUses, tokens, startedAt, durationMillis);
        }
    }

    synchronized Task update(Json message) {
        String id = message.get("task_id").asString();
        if (id == null) return null;
        Entry entry = entries.get(id);
        if (entry == null) {
            entry = new Entry();
            entry.id = id;
            entries.put(id, entry);
        }
        if (message.get("tool_use_id").isString()) entry.toolUseId = message.get("tool_use_id").asString();
        if (message.get("subagent_type").isString()) entry.agentType = message.get("subagent_type").asString();
        if (message.get("is_backgrounded").isBoolean()) entry.background = message.get("is_backgrounded").asBoolean(false);
        if (message.get("task_type").isString()) entry.kind = kind(message.get("task_type").asString());
        switch (message.get("subtype").asString("")) {
            case "task_started":
                entry.description = message.get("description").asString(entry.description);
                break;
            case "task_progress":
                entry.activity = message.get("summary").asString(message.get("description").asString(entry.activity));
                usage(entry, message.get("usage"));
                break;
            case "task_updated":
                Json patch = message.get("patch");
                if (patch.get("status").isString()) entry.status = status(patch.get("status").asString());
                if (patch.get("description").isString()) entry.description = patch.get("description").asString();
                if (patch.get("is_backgrounded").isBoolean()) entry.background = patch.get("is_backgrounded").asBoolean(false);
                if (patch.get("error").isString()) entry.activity = patch.get("error").asString();
                break;
            case "task_notification":
                entry.status = status(message.get("status").asString(""));
                entry.activity = firstLine(message.get("summary").asString(entry.activity));
                usage(entry, message.get("usage"));
                break;
            default:
                break;
        }
        if (entry.status.isActive()) entry.durationMillis = System.currentTimeMillis() - entry.startedAt;
        return entry.snapshot();
    }

    private static void usage(Entry entry, Json usage) {
        if (!usage.isObject()) return;
        entry.toolUses = usage.get("tool_uses").asInt(entry.toolUses);
        entry.tokens = usage.get("total_tokens").asLong(entry.tokens);
        entry.durationMillis = usage.get("duration_ms").asLong(entry.durationMillis);
    }

    private static Task.Kind kind(String type) {
        if ("local_agent".equals(type) || "remote_agent".equals(type)) return Task.Kind.AGENT;
        if ("local_bash".equals(type)) return Task.Kind.SHELL;
        return Task.Kind.OTHER;
    }

    private static Task.Status status(String status) {
        switch (status) {
            case "completed": return Task.Status.DONE;
            case "failed": return Task.Status.FAILED;
            case "stopped": case "killed": return Task.Status.STOPPED;
            default: return Task.Status.RUNNING;
        }
    }

    private static String firstLine(String text) {
        if (text == null) return null;
        String line = text.trim().split("\n", 2)[0].replaceAll("[*_`#]", "").trim();
        return line.isEmpty() ? null : line;
    }
}
