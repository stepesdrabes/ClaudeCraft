package dev.claudecraft.core.chat;

import dev.claudecraft.agent.ImageData;
import dev.claudecraft.agent.LiveSession;
import dev.claudecraft.agent.McpServerInfo;
import dev.claudecraft.agent.PermissionRequest;
import dev.claudecraft.agent.QuestionRequest;
import dev.claudecraft.agent.Session;
import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.SessionSpec;
import dev.claudecraft.agent.SessionSummary;
import dev.claudecraft.agent.Task;
import dev.claudecraft.agent.ToolOutput;
import dev.claudecraft.agent.ToolUse;
import dev.claudecraft.agent.TurnResult;
import dev.claudecraft.agent.Usage;
import dev.claudecraft.agent.json.Json;
import dev.claudecraft.core.ui.Picture;
import dev.claudecraft.core.ui.Theme;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Chat implements SessionListener {
    private static final int TITLE_LENGTH = 60;

    public interface Host {
        Session open(SessionSpec spec, SessionListener listener);

        void finished(Chat chat, TurnResult result);

        void needsYou(Chat chat);

        void planUsage(Usage.Plan usage);
    }

    private final Host host;
    private final Path workspace;
    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private final List<Picture> attachments = new ArrayList<>();
    private final StringBuilder thinking = new StringBuilder();
    private Path cwd;
    private Transcript transcript = new Transcript();
    private String sessionId;
    private String forkOf;
    private boolean worktree;
    private String title;
    private long updatedAt;
    private Status status = Status.IDLE;
    private String draft = "";
    private String model;
    private String activeModel;
    private String effort;
    private String mode;
    private Session session;
    private PermissionRequest permission;
    private QuestionRequest question;
    private long turnStartedAt;
    private String step;
    private boolean historyLoaded;
    private volatile Usage.Context context;
    private volatile List<McpServerInfo> mcpServers;
    private LiveSession live;
    private boolean archived;
    private boolean persistTitle;

    Chat(Host host, Path workspace) {
        this.host = host;
        this.workspace = workspace;
        this.cwd = workspace;
        this.historyLoaded = true;
        this.updatedAt = System.currentTimeMillis();
    }

    Chat(Host host, Path workspace, SessionSummary summary) {
        this.host = host;
        this.workspace = workspace;
        this.cwd = summary.cwd();
        this.sessionId = summary.id();
        this.title = summary.title();
        this.updatedAt = summary.updatedAt();
        this.status = Status.DONE;
    }

    static Chat forkOf(Chat source) {
        Chat fork = new Chat(source.host, source.workspace);
        fork.cwd = source.cwd;
        fork.forkOf = source.sessionId;
        fork.title = "Fork of " + source.title();
        fork.persistTitle = true;
        fork.model = source.model;
        fork.effort = source.effort;
        fork.mode = source.mode;
        fork.historyLoaded = false;
        return fork;
    }

    void nameIfUntitled(String text) {
        if (title == null) title = titleFrom(text.isEmpty() ? "Image" : text);
    }

    public void send(String text, List<Picture> images) {
        nameIfUntitled(text);
        transcript.addUser(text, images);
        try {
            if (session == null || !session.isOpen()) session = host.open(spec(text), this);
        } catch (IllegalStateException e) {
            transcript.notice(e.getMessage(), Theme.ERROR);
            status = Status.FAILED;
            return;
        }
        List<ImageData> data = new ArrayList<>();
        for (Picture image : images) data.add(image.data());
        session.send(text, data);
        forkOf = null;
        worktree = false;
        startWorking("Thinking");
    }

    SessionSpec spec(String firstMessage) {
        SessionSpec spec = new SessionSpec(cwd).model(model).effort(effort).permissionMode(mode);
        if (sessionId != null) return spec.resume(sessionId);
        if (forkOf != null) return spec.resume(forkOf).fork(true);
        if (worktree) spec.worktree("");
        return spec.title(titleFrom(firstMessage));
    }

    private void startWorking(String step) {
        thinking.setLength(0);
        status = Status.WORKING;
        this.step = step;
        turnStartedAt = updatedAt = System.currentTimeMillis();
    }

    public void interrupt() {
        if (session != null && status.isActive()) session.interrupt();
    }

    public void selectModel(String modelId) {
        model = modelId;
        if (isOpen()) session.setModel(modelId);
    }

    public void selectEffort(String level) {
        effort = level;
        if (isOpen()) session.setEffort(level);
    }

    public void selectMode(String modeId) {
        mode = modeId;
        if (isOpen()) session.setPermissionMode(modeId);
    }

    public void stopTask(String taskId) {
        if (isOpen()) session.stopTask(taskId);
    }

    public void backgroundTasks() {
        if (isOpen()) session.backgroundTasks();
    }

    public void setMcpServerEnabled(String name, boolean enabled) {
        if (!isOpen()) return;
        session.setMcpServerEnabled(name, enabled);
        refreshMcpServers();
    }

    public void reconnectMcpServer(String name) {
        if (!isOpen()) return;
        session.reconnectMcpServer(name);
        refreshMcpServers();
    }

    public void refreshContext() {
        if (isOpen()) session.contextUsage().thenAccept(usage -> context = usage);
    }

    public void refreshMcpServers() {
        if (isOpen()) session.mcpServers().thenAccept(servers -> mcpServers = servers);
    }

    public boolean isOpen() {
        return session != null && session.isOpen();
    }

    public void close() {
        if (session != null) session.close();
        session = null;
    }

    void loadHistory(Transcript history) {
        if (transcript.isEmpty()) transcript = history;
        historyLoaded = true;
    }

    void reloadHistory(Transcript history) {
        transcript = history;
        historyLoaded = true;
    }

    void rename(String title) {
        this.title = title;
        if (isOpen()) session.rename(title);
    }

    public void answerPermission(boolean allow, boolean remember) {
        answerPermission(allow, remember, "The user declined this action.");
    }

    public void approvePlan(boolean acceptEdits) {
        if (permission == null) return;
        if (acceptEdits) permission.allowAndSetMode("acceptEdits");
        else permission.allow(false);
        permission = null;
        resume();
    }

    public void answerPermission(boolean allow, boolean remember, String feedback) {
        if (permission == null) return;
        if (allow) permission.allow(remember);
        else permission.deny(feedback);
        permission = null;
        resume();
    }

    public void answerQuestion(List<String> answers) {
        if (question == null) return;
        question.answer(answers);
        question = null;
        resume();
    }

    public void dismissQuestion() {
        if (question == null) return;
        question.dismiss();
        question = null;
        resume();
    }

    private void resume() {
        if (status == Status.NEEDS_YOU && permission == null && question == null) status = Status.WORKING;
    }

    @Override
    public void onStarted(String sessionId, String model, String cwd) {
        this.sessionId = sessionId;
        this.activeModel = model;
        if (cwd != null) this.cwd = Paths.get(cwd);
        if (persistTitle && isOpen()) session.rename(title);
        persistTitle = false;
        refreshMcpServers();
    }

    @Override
    public void onText(String delta) {
        transcript.onText(delta);
        step = "Writing";
    }

    @Override
    public void onThinking(String delta) {
        if (delta.isEmpty()) thinking.setLength(0);
        thinking.append(delta);
        step = "Thinking";
    }

    @Override
    public void onToolStarting(String toolName) {
        step = ToolLabels.describe(toolName, Json.object())[0];
    }

    @Override
    public void onToolUse(ToolUse use) {
        transcript.onToolUse(use);
        step = ToolLabels.describe(use.name(), use.input())[0];
    }

    @Override
    public void onSubagentToolUse(String parentToolUseId, ToolUse use) {
        transcript.onSubagentToolUse(parentToolUseId, use);
    }

    @Override
    public void onToolResult(ToolOutput output) {
        transcript.onToolResult(output);
    }

    @Override
    public void onTask(Task task) {
        tasks.put(task.id(), task);
    }

    @Override
    public void onPermissionRequest(PermissionRequest request) {
        permission = request;
        status = Status.NEEDS_YOU;
        host.needsYou(this);
    }

    @Override
    public void onQuestion(QuestionRequest request) {
        question = request;
        status = Status.NEEDS_YOU;
        host.needsYou(this);
    }

    @Override
    public void onRequestCancelled(String requestId) {
        if (permission != null && permission.id().equals(requestId)) permission = null;
        if (question != null && question.id().equals(requestId)) question = null;
        resume();
    }

    @Override
    public void onStatus(String status) {
        if ("compacting".equals(status)) step = "Compacting";
        else if ("retrying".equals(status)) step = "Retrying";
    }

    @Override
    public void onPermissionMode(String mode) {
        this.mode = mode;
    }

    @Override
    public void onBusy(boolean busy) {
        if (busy && !status.isActive()) startWorking("Working");
    }

    @Override
    public void onCompacted(long tokensBefore, long tokensAfter) {
        String detail = tokensAfter > 0 ? " (" + tokens(tokensBefore) + " → " + tokens(tokensAfter) + ")" : "";
        transcript.notice("Conversation compacted" + detail, Theme.MUTED);
        refreshContext();
    }

    @Override
    public void onNotice(String text) {
        transcript.notice(text, Theme.MUTED);
    }

    @Override
    public void onPlanUsage(Usage.Plan usage) {
        host.planUsage(usage);
    }

    @Override
    public void onTurnEnd(TurnResult result) {
        transcript.settle();
        permission = null;
        question = null;
        updatedAt = System.currentTimeMillis();
        switch (result.outcome()) {
            case COMPLETED:
                status = Status.DONE;
                break;
            case INTERRUPTED:
                status = Status.IDLE;
                transcript.notice("Interrupted", Theme.MUTED);
                break;
            default:
                status = Status.FAILED;
                transcript.notice(result.error() != null ? result.error() : "Something went wrong", Theme.ERROR);
        }
        refreshContext();
        refreshMcpServers();
        host.finished(this, result);
    }

    @Override
    public void onError(String message) {
        transcript.notice(message, Theme.ERROR);
    }

    @Override
    public void onClosed() {
        session = null;
        for (Task task : new ArrayList<>(tasks.values())) {
            if (task.status().isActive()) tasks.remove(task.id());
        }
        if (!status.isActive()) return;
        transcript.settle();
        status = Status.FAILED;
        host.finished(this, new TurnResult(TurnResult.Outcome.FAILED, null, "Claude Code stopped", 0, 0));
    }

    static String titleFrom(String text) {
        String line = text.trim().split("\n", 2)[0];
        return line.length() <= TITLE_LENGTH ? line : line.substring(0, TITLE_LENGTH - 1).trim() + "…";
    }

    public static String tokens(long count) {
        if (count >= 1_000_000) return trim(count / 1_000_000.0) + "M";
        if (count >= 1000) return trim(count / 1000.0) + "k";
        return String.valueOf(count);
    }

    private static String trim(double value) {
        return value >= 100 || value == Math.rint(value) ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    public Path workspace() {
        return workspace;
    }

    public Path cwd() {
        return cwd;
    }

    void moveTo(Path cwd) {
        this.cwd = cwd;
    }

    public boolean inWorktree() {
        return !cwd.equals(workspace) || worktree;
    }

    public void useWorktree(boolean worktree) {
        if (sessionId == null) this.worktree = worktree;
    }

    public Transcript transcript() {
        return transcript;
    }

    public Todos todos() {
        return transcript.todos();
    }

    public Collection<Task> tasks() {
        return Collections.unmodifiableCollection(tasks.values());
    }

    public Task task(String toolUseId) {
        for (Task task : tasks.values()) if (toolUseId.equals(task.toolUseId())) return task;
        return null;
    }

    public List<Picture> attachments() {
        return attachments;
    }

    public String sessionId() {
        return sessionId;
    }

    void assignSession(String sessionId) {
        if (this.sessionId == null) this.sessionId = sessionId;
    }

    public String forkOf() {
        return forkOf;
    }

    public String title() {
        return title != null ? title : "New chat";
    }

    public long updatedAt() {
        return updatedAt;
    }

    public Status status() {
        if (live != null) return live.background() ? Status.of(live.state()) : status;
        return status;
    }

    public String step() {
        return step;
    }

    public String thought() {
        if (!"Thinking".equals(step)) return null;
        String[] lines = thinking.toString().trim().split("\n");
        String last = lines[lines.length - 1].replaceAll("[*_#`]", "").trim();
        return last.isEmpty() ? null : last;
    }

    public long turnStartedAt() {
        return turnStartedAt;
    }

    public String draft() {
        return draft;
    }

    public void saveDraft(String draft) {
        this.draft = draft;
    }

    public String model() {
        return model;
    }

    public String activeModel() {
        return activeModel;
    }

    public String effort() {
        return effort;
    }

    public String mode() {
        return mode;
    }

    public PermissionRequest permission() {
        return permission;
    }

    public QuestionRequest question() {
        return question;
    }

    public Usage.Context context() {
        return context;
    }

    public List<McpServerInfo> mcpServers() {
        return mcpServers;
    }

    public LiveSession live() {
        return live;
    }

    void setLive(LiveSession live) {
        this.live = live;
    }

    public boolean inBackground() {
        return live != null && live.background();
    }

    public boolean runningElsewhere() {
        return live != null && !live.background() && session == null;
    }

    public boolean archived() {
        return archived;
    }

    void setArchived(boolean archived) {
        this.archived = archived;
    }

    public boolean historyLoaded() {
        return historyLoaded;
    }

    public boolean isNew() {
        return sessionId == null && forkOf == null && transcript.isEmpty();
    }
}
