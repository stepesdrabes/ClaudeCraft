package dev.claudecraft.agent.tool;

public final class ToolResult {
    private final String text;
    private final byte[] png;
    private final boolean error;

    private ToolResult(String text, byte[] png, boolean error) {
        this.text = text;
        this.png = png;
        this.error = error;
    }

    public static ToolResult text(String text) {
        return new ToolResult(text, null, false);
    }

    public static ToolResult error(String message) {
        return new ToolResult(message == null ? "Unknown error" : message, null, true);
    }

    public static ToolResult image(byte[] png, String caption) {
        return new ToolResult(caption, png, false);
    }

    public String text() {
        return text;
    }

    public byte[] png() {
        return png;
    }

    public boolean isError() {
        return error;
    }
}
