package dev.claudecraft.agent;

public final class Installation {
    private final String path;
    private final String version;

    public Installation(String path, String version) {
        this.path = path;
        this.version = version;
    }

    public String path() {
        return path;
    }

    public String version() {
        return version;
    }
}
