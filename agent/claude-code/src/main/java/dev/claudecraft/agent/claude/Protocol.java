package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.ImageData;
import dev.claudecraft.agent.McpServerInfo;
import dev.claudecraft.agent.ToolOutput;
import dev.claudecraft.agent.Usage;
import dev.claudecraft.agent.json.Json;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

final class Protocol {
    private Protocol() {
    }

    static Json userMessage(String text, List<ImageData> images) {
        Object content = text;
        if (!images.isEmpty()) {
            Json blocks = Json.array();
            for (ImageData image : images) {
                blocks.add(Json.object().put("type", "image").put("source", Json.object()
                    .put("type", "base64")
                    .put("media_type", image.mediaType())
                    .put("data", Base64.getEncoder().encodeToString(image.data()))));
            }
            if (!text.isEmpty()) blocks.add(Json.object().put("type", "text").put("text", text));
            content = blocks;
        }
        return Json.object()
            .put("type", "user")
            .put("session_id", "")
            .put("parent_tool_use_id", null)
            .put("message", Json.object().put("role", "user").put("content", content));
    }

    static String text(Json content) {
        if (content.isString()) return content.asString();
        List<String> parts = new ArrayList<>();
        for (Json block : content.items()) {
            if ("text".equals(block.get("type").asString())) parts.add(block.get("text").asString(""));
        }
        return String.join("\n", parts);
    }

    static List<ImageData> images(Json content) {
        List<ImageData> images = new ArrayList<>();
        for (Json block : content.items()) {
            Json source = block.get("source");
            if (!"image".equals(block.get("type").asString()) || !"base64".equals(source.get("type").asString())) continue;
            try {
                images.add(new ImageData(source.get("media_type").asString("image/png"), Base64.getDecoder().decode(source.get("data").asString(""))));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return images.isEmpty() ? Collections.<ImageData>emptyList() : images;
    }

    static ToolOutput toolOutput(Json block, Json details) {
        Json content = block.get("content");
        return new ToolOutput(block.get("tool_use_id").asString(""), text(content), images(content),
            block.get("is_error").asBoolean(false), details);
    }

    static List<McpServerInfo> mcpServers(Json response) {
        List<McpServerInfo> servers = new ArrayList<>();
        for (Json server : response.get("mcpServers").items()) {
            servers.add(new McpServerInfo(server.get("name").asString(""), mcpState(server.get("status").asString("")),
                server.get("scope").asString("")));
        }
        return servers;
    }

    private static McpServerInfo.State mcpState(String status) {
        switch (status) {
            case "connected": return McpServerInfo.State.CONNECTED;
            case "needs-auth": return McpServerInfo.State.NEEDS_AUTH;
            case "failed": return McpServerInfo.State.FAILED;
            case "disabled": return McpServerInfo.State.DISABLED;
            default: return McpServerInfo.State.PENDING;
        }
    }

    static Usage.Plan rateLimits(Json info) {
        Json windows = info.get("unifiedWindows");
        if (windows.size() == 0) return null;
        List<Usage.Limit> limits = new ArrayList<>();
        addWindow(limits, "Session", windows.get("five_hour"));
        addWindow(limits, "Week", windows.get("seven_day"));
        return limits.isEmpty() ? null : new Usage.Plan(limits);
    }

    private static void addWindow(List<Usage.Limit> limits, String label, Json window) {
        if (!window.isObject()) return;
        limits.add(new Usage.Limit(label, (int) Math.round(window.get("utilization").asDouble(0) * 100), window.get("resetsAt").asLong(0) * 1000));
    }

    static Usage.Plan planUsage(Json response) {
        if (!response.get("rate_limits_available").asBoolean(false)) return null;
        List<Usage.Limit> limits = new ArrayList<>();
        for (Json limit : response.get("rate_limits").get("limits").items()) {
            String model = limit.get("scope").get("model").get("display_name").asString();
            String label = "session".equals(limit.get("group").asString()) ? "Session" : model != null ? "Week · " + model : "Week";
            limits.add(new Usage.Limit(label, limit.get("percent").asInt(0), epochMillis(limit.get("resets_at").asString())));
        }
        return new Usage.Plan(limits);
    }

    private static long epochMillis(String iso) {
        if (iso == null) return 0;
        try {
            return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    static Usage.Context contextUsage(Json response) {
        return new Usage.Context(response.get("totalTokens").asLong(0), response.get("maxTokens").asLong(0));
    }
}
