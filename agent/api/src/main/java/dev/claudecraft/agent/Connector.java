package dev.claudecraft.agent;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Connector {
    String name();

    CompletableFuture<ConnectorInfo> info(Path workspace);

    List<Installation> installations();

    void useExecutable(String path);

    CompletableFuture<String> update();

    Session open(SessionSpec spec, SessionListener listener);

    List<SessionSummary> sessions(Path workspace, int limit);

    void replay(Path cwd, String sessionId, SessionListener listener);

    void rename(Path cwd, String sessionId, String title);

    void delete(Path cwd, String sessionId);

    List<Path> recentWorkspaces(int limit);

    CompletableFuture<Usage.Plan> planUsage(Path workspace);

    CompletableFuture<List<McpServerInfo>> mcpServers(Path workspace);

    List<LiveSession> liveSessions();

    CompletableFuture<String> startBackground(SessionSpec spec, String prompt);

    void stopBackground(String id);

    void removeBackground(String id);
}
