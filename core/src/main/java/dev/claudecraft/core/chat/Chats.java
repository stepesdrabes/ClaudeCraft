package dev.claudecraft.core.chat;

import dev.claudecraft.agent.Connector;
import dev.claudecraft.agent.SessionSummary;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public final class Chats {
    private static final int LISTED_SESSIONS = 30;

    private final Chat.Host host;
    private final Connector connector;
    private final Executor main;
    private final Executor background;
    private final List<Chat> all = new ArrayList<>();
    private Path workspace;
    private Chat selected;

    public Chats(Chat.Host host, Connector connector, Executor main, Executor background, Path workspace) {
        this.host = host;
        this.connector = connector;
        this.main = main;
        this.background = background;
        this.workspace = workspace;
    }

    public Path workspace() {
        return workspace;
    }

    public void setWorkspace(Path workspace) {
        if (workspace.equals(this.workspace)) return;
        this.workspace = workspace;
        selected = null;
        refresh();
    }

    public List<Chat> visible() {
        return all.stream()
            .filter(chat -> chat.workspace().equals(workspace))
            .sorted(Comparator.comparing(Chat::isNew).reversed().thenComparing(Comparator.comparingLong(Chat::updatedAt).reversed()))
            .collect(Collectors.toList());
    }

    public List<Chat> active() {
        return all.stream().filter(chat -> chat.status().isActive()).collect(Collectors.toList());
    }

    public Chat selected() {
        if (selected == null) {
            List<Chat> visible = visible();
            select(visible.isEmpty() ? startNew() : visible.get(0));
        }
        return selected;
    }

    public void select(Chat chat) {
        selected = chat;
        if (chat.historyLoaded()) return;
        Path chatWorkspace = chat.workspace();
        background.execute(() -> {
            Transcript history = new Transcript();
            connector.replay(chatWorkspace, chat.sessionId(), history);
            main.execute(() -> chat.loadHistory(history));
        });
    }

    public Chat startNew() {
        for (Chat chat : visible()) {
            if (chat.isNew()) {
                selected = chat;
                return chat;
            }
        }
        Chat chat = new Chat(host, workspace);
        all.add(chat);
        selected = chat;
        return chat;
    }

    public void refresh() {
        Path listed = workspace;
        background.execute(() -> {
            List<SessionSummary> summaries = connector.sessions(listed, LISTED_SESSIONS);
            main.execute(() -> merge(listed, summaries));
        });
    }

    private void merge(Path listed, List<SessionSummary> summaries) {
        for (SessionSummary summary : summaries) {
            boolean known = all.stream().anyMatch(chat -> summary.id().equals(chat.sessionId()));
            if (!known) all.add(new Chat(host, listed, summary));
        }
    }

    public void closeAll() {
        all.forEach(Chat::close);
    }
}
