package dev.claudecraft.core.view;

import java.nio.file.Path;

final class Format {
    private Format() {
    }

    static String elapsed(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return seconds / 60 + "m " + seconds % 60 + "s";
        return seconds / 3600 + "h " + seconds % 3600 / 60 + "m";
    }

    static String ago(long timestamp, long now) {
        long minutes = Math.max(0, (now - timestamp) / 60_000);
        if (minutes < 1) return "now";
        if (minutes < 60) return minutes + "m ago";
        if (minutes < 60 * 24) return minutes / 60 + "h ago";
        return minutes / (60 * 24) + "d ago";
    }

    static String folder(Path path) {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }
}
