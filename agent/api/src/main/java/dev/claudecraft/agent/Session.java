package dev.claudecraft.agent;

public interface Session {
    void send(String text);

    void interrupt();

    void setModel(String modelId);

    void setPermissionMode(String modeId);

    void rename(String title);

    boolean isOpen();

    void close();
}
