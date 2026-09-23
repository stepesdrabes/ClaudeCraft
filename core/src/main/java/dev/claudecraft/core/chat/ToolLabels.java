package dev.claudecraft.core.chat;

import dev.claudecraft.agent.json.Json;

import java.util.Map;

public final class ToolLabels {
    private ToolLabels() {
    }

    public static String[] describe(String name, Json input) {
        switch (name) {
            case "Bash": return label("Bash", input.get("command").asString(""));
            case "Read": case "Write": case "Edit": case "MultiEdit": case "NotebookEdit":
                return label(name, shortPath(input.get("file_path").asString(input.get("notebook_path").asString(""))));
            case "Glob": case "Grep": return label(name, input.get("pattern").asString(""));
            case "WebFetch": return label("Fetch", input.get("url").asString(""));
            case "WebSearch": return label("Search", input.get("query").asString(""));
            case "Task": case "Agent": return label("Agent", input.get("description").asString(""));
            case "TodoWrite": return label("Todos", input.get("todos").size() + " items");
            case "Skill": return label("Skill", input.get("skill").asString(""));
            case "ExitPlanMode": return label("Plan", "ready for review");
            default: return mcp(name, input);
        }
    }

    private static String[] mcp(String name, Json input) {
        if (!name.startsWith("mcp__")) return label(name, firstString(input));
        int split = name.indexOf("__", 5);
        String server = split > 0 ? name.substring(5, split) : "";
        String tool = split > 0 ? name.substring(split + 2) : name;
        if ("minecraft".equals(server)) {
            switch (tool) {
                case "run_command": return label("Command", "/" + input.get("command").asString("").replaceFirst("^/", ""));
                case "say": return label("Chat", input.get("message").asString(""));
                case "status": return label("Look around", "");
                case "read_blocks": return label("Scan blocks", "");
                case "nearby_entities": return label("Scan entities", "");
                default: return label(tool, firstString(input));
            }
        }
        return label(server + " · " + tool, firstString(input));
    }

    private static String firstString(Json input) {
        for (Map.Entry<String, Json> field : input.entries()) {
            if (field.getValue().isString()) return field.getValue().asString();
        }
        return "";
    }

    private static String shortPath(String path) {
        String[] parts = path.split("/");
        if (parts.length <= 3) return path;
        return "…/" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

    private static String[] label(String label, String detail) {
        return new String[]{label, detail.replace('\n', ' ')};
    }
}
