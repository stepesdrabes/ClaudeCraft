package dev.claudecraft.agent.tool;

public final class ToolResult {
    private final String text;
    private final byte[] image;
    private final String mediaType;
    private final boolean error;

    private ToolResult(String text, byte[] image, String mediaType, boolean error) {
        this.text = text;
        this.image = image;
        this.mediaType = mediaType;
        this.error = error;
    }

    public static ToolResult text(String text) {
        return new ToolResult(text, null, null, false);
    }

    public static ToolResult error(String message) {
        return new ToolResult(message == null ? "Unknown error" : message, null, null, true);
    }

    public static ToolResult image(byte[] data, String mediaType, String caption) {
        return new ToolResult(caption, data, mediaType, false);
    }

    public String text() {
        return text;
    }

    public byte[] image() {
        return image;
    }

    public String mediaType() {
        return mediaType;
    }

    public boolean isError() {
        return error;
    }
}
