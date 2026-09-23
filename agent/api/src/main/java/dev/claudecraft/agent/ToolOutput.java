package dev.claudecraft.agent;

import dev.claudecraft.agent.json.Json;

import java.util.List;

public final class ToolOutput {
    private final String toolUseId;
    private final String text;
    private final List<ImageData> images;
    private final boolean error;
    private final Json details;

    public ToolOutput(String toolUseId, String text, List<ImageData> images, boolean error, Json details) {
        this.toolUseId = toolUseId;
        this.text = text;
        this.images = images;
        this.error = error;
        this.details = details;
    }

    public String toolUseId() {
        return toolUseId;
    }

    public String text() {
        return text;
    }

    public List<ImageData> images() {
        return images;
    }

    public boolean error() {
        return error;
    }

    public Json details() {
        return details;
    }
}
