package dev.claudecraft.agent;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Connector {
    String name();

    CompletableFuture<ConnectorInfo> info(Path workspace);

    Session open(SessionSpec spec, SessionListener listener);

    List<SessionSummary> sessions(Path workspace, int limit);

    void replay(Path workspace, String sessionId, SessionListener listener);

    List<Path> recentWorkspaces(int limit);
}
