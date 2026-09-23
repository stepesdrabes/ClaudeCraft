package dev.claudecraft.core.chat;

import dev.claudecraft.agent.Connector;
import dev.claudecraft.agent.LiveSession;
import dev.claudecraft.agent.SessionSpec;
import dev.claudecraft.agent.SessionSummary;
import dev.claudecraft.agent.TurnResult;
import dev.claudecraft.core.ui.Picture;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class Chats {
    private static final int LISTED_SESSIONS = 40;
    private static final long LIVE_REFRESH_MILLIS = 4000;
    private static final long IDLE_LIVE_REFRESH_MILLIS = 20_000;
    private static final long RELOAD_MILLIS = 2500;

    public interface Background {
        CompletableFuture<String> start(SessionSpec spec, String prompt);
    }

    private final Chat.Host host;
    private final Connector connector;
    private final Executor main;
    private final Executor background;
    private final Set<String> archived;
    private final Consumer<Set<String>> saveArchived;
    private final List<Chat> all = new ArrayList<>();
    private final Set<String> deleted = new HashSet<>();
    private Path workspace;
    private Chat selected;
    private boolean showArchived;
    private boolean liveRefreshing;
    private int liveGeneration;
    private long liveRefreshedAt;
    private long reloadedAt;
    private boolean reloading;

    public Chats(Chat.Host host, Connector connector, Executor main, Executor background, Path workspace,
                 Set<String> archived, Consumer<Set<String>> saveArchived) {
        this.host = host;
        this.connector = connector;
        this.main = main;
        this.background = background;
        this.workspace = workspace;
        this.archived = archived;
        this.saveArchived = saveArchived;
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
            .filter(chat -> chat.workspace().equals(workspace) && (showArchived || !chat.archived()))
            .sorted(Comparator.comparing(Chat::isNew).reversed().thenComparing(Comparator.comparingLong(Chat::updatedAt).reversed()))
            .collect(Collectors.toList());
    }

    public int archivedCount() {
        return (int) all.stream().filter(chat -> chat.workspace().equals(workspace) && chat.archived()).count();
    }

    public boolean showArchived() {
        return showArchived;
    }

    public void toggleArchived() {
        showArchived = !showArchived;
    }

    public List<Chat> active() {
        return all.stream().filter(chat -> chat.status().isActive()).collect(Collectors.toList());
    }

    public Chat selected() {
        if (selected == null || !all.contains(selected)) {
            List<Chat> visible = visible();
            select(visible.isEmpty() ? startNew() : visible.get(0));
        }
        return selected;
    }

    public void select(Chat chat) {
        selected = chat;
        if (!chat.historyLoaded()) load(chat, false);
    }

    private void load(Chat chat, boolean reload) {
        String id = chat.sessionId() != null ? chat.sessionId() : chat.forkOf();
        if (id == null) return;
        Path cwd = chat.cwd();
        background.execute(() -> {
            Transcript history = new Transcript();
            connector.replay(cwd, id, history);
            main.execute(() -> {
                if (reload) chat.reloadHistory(history);
                else chat.loadHistory(history);
                reloading = false;
            });
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

    public void fork(Chat chat) {
        if (chat.sessionId() == null) return;
        Chat fork = Chat.forkOf(chat);
        all.add(fork);
        select(fork);
    }

    public void rename(Chat chat, String title) {
        chat.rename(title);
        String id = chat.sessionId();
        if (id == null || chat.isOpen()) return;
        Path cwd = chat.cwd();
        background.execute(() -> connector.rename(cwd, id, title));
    }

    public void archive(Chat chat, boolean archive) {
        if (chat.sessionId() == null) return;
        chat.setArchived(archive);
        if (archive) archived.add(chat.sessionId());
        else archived.remove(chat.sessionId());
        saveArchived.accept(archived);
        if (archive && selected == chat && !showArchived) selected = null;
    }

    public void delete(Chat chat) {
        chat.close();
        all.remove(chat);
        if (selected == chat) selected = null;
        String id = chat.sessionId();
        if (id == null) return;
        deleted.add(id);
        if (archived.remove(id)) saveArchived.accept(archived);
        LiveSession live = chat.live();
        Path cwd = chat.cwd();
        background.execute(() -> {
            if (live != null && live.background()) {
                stopQuietly(live.id());
                removeQuietly(live.id());
            }
            try {
                connector.delete(cwd, id);
            } catch (RuntimeException e) {
                main.execute(() -> {
                    deleted.remove(id);
                    all.add(chat);
                    chat.onError("Could not delete this chat: " + rootMessage(e));
                });
            }
        });
    }

    public void sendToBackground(Chat chat, String prompt, Background starter) {
        chat.close();
        chat.nameIfUntitled(prompt);
        chat.transcript().addUser(prompt, Collections.<Picture>emptyList());
        chat.saveDraft("");
        starter.start(chat.spec(prompt), prompt).whenComplete((id, failure) -> main.execute(() -> {
            if (failure != null) {
                chat.onError("Could not start a background session: " + rootMessage(failure));
                return;
            }
            chat.setLive(new LiveSession(id, chat.sessionId(), chat.cwd(), true, LiveSession.State.WORKING));
            liveGeneration++;
            liveRefreshedAt = 0;
        }));
    }

    public void bringBack(Chat chat) {
        LiveSession live = chat.live();
        if (live == null || !live.background()) return;
        background.execute(() -> {
            stopQuietly(live.id());
            main.execute(() -> {
                chat.setLive(null);
                liveGeneration++;
                load(chat, true);
            });
        });
    }

    private void removeQuietly(String id) {
        try {
            connector.removeBackground(id);
        } catch (RuntimeException ignored) {
        }
    }

    private void stopQuietly(String id) {
        try {
            connector.stopBackground(id);
        } catch (RuntimeException ignored) {
        }
    }

    public void tick(long now, boolean watching) {
        long interval = watching ? LIVE_REFRESH_MILLIS : IDLE_LIVE_REFRESH_MILLIS;
        if (!liveRefreshing && now - liveRefreshedAt > interval) {
            liveRefreshing = true;
            liveRefreshedAt = now;
            int generation = liveGeneration;
            background.execute(() -> {
                List<LiveSession> live = connector.liveSessions();
                main.execute(() -> mergeLive(live, generation));
            });
        }
        Chat chat = selected;
        if (watching && chat != null && chat.inBackground() && chat.sessionId() != null && !reloading && now - reloadedAt > RELOAD_MILLIS) {
            reloading = true;
            reloadedAt = now;
            load(chat, true);
        }
    }

    private void mergeLive(List<LiveSession> sessions, int generation) {
        liveRefreshing = false;
        if (generation != liveGeneration) return;
        Map<String, LiveSession> byId = new HashMap<>();
        for (LiveSession session : sessions) byId.put(session.sessionId(), session);
        boolean unknown = false;
        for (Chat chat : all) {
            LiveSession previous = chat.live();
            if (chat.sessionId() == null && previous != null) {
                for (LiveSession session : sessions) if (session.sessionId().startsWith(previous.id())) chat.assignSession(session.sessionId());
            }
            if (chat.sessionId() == null && previous != null) continue;
            LiveSession current = chat.sessionId() != null ? byId.remove(chat.sessionId()) : null;
            chat.setLive(current);
            if (previous != null && previous.background() && current != null) notifyTransition(chat, previous.state(), current.state());
        }
        for (LiveSession session : byId.values()) {
            if (session.background() && session.cwd().startsWith(workspace)) unknown = true;
        }
        if (unknown) refresh();
    }

    private void notifyTransition(Chat chat, LiveSession.State before, LiveSession.State after) {
        if (before == after) return;
        if (after == LiveSession.State.NEEDS_YOU) host.needsYou(chat);
        else if (before == LiveSession.State.WORKING && after == LiveSession.State.IDLE) {
            host.finished(chat, new TurnResult(TurnResult.Outcome.COMPLETED, null, null, 0, 0));
        } else if (after == LiveSession.State.FAILED) {
            host.finished(chat, new TurnResult(TurnResult.Outcome.FAILED, null, "The background session failed", 0, 0));
        }
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
            if (deleted.contains(summary.id())) continue;
            Chat existing = all.stream().filter(chat -> summary.id().equals(chat.sessionId())
                || chat.sessionId() == null && chat.live() != null && summary.id().startsWith(chat.live().id())).findFirst().orElse(null);
            if (existing != null) {
                existing.assignSession(summary.id());
                if (!existing.isOpen()) existing.moveTo(summary.cwd());
                continue;
            }
            Chat chat = new Chat(host, listed, summary);
            chat.setArchived(archived.contains(summary.id()));
            all.add(chat);
        }
        liveRefreshedAt = 0;
    }

    public void closeAll() {
        all.forEach(Chat::close);
    }

    static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }
}
