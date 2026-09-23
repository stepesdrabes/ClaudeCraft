package dev.claudecraft.agent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Session {
    void send(String text, List<ImageData> images);

    void interrupt();

    void setModel(String modelId);

    void setEffort(String level);

    void setPermissionMode(String modeId);

    void rename(String title);

    void stopTask(String taskId);

    void backgroundTasks();

    CompletableFuture<Usage.Context> contextUsage();

    CompletableFuture<List<McpServerInfo>> mcpServers();

    void setMcpServerEnabled(String name, boolean enabled);

    void reconnectMcpServer(String name);

    boolean isOpen();

    void close();
}
