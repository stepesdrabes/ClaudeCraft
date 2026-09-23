package dev.claudecraft.core.chat;

import dev.claudecraft.agent.PermissionRequest;
import dev.claudecraft.agent.QuestionRequest;
import dev.claudecraft.agent.Session;
import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.SessionSpec;
import dev.claudecraft.agent.SessionSummary;
import dev.claudecraft.agent.ToolUse;
import dev.claudecraft.agent.TurnResult;
import dev.claudecraft.agent.json.Json;
import dev.claudecraft.core.ui.Theme;

import java.nio.file.Path;
import java.util.List;

public final class Chat implements SessionListener {
    private static final int TITLE_LENGTH = 60;

    public interface Host {
        Session open(SessionSpec spec, SessionListener listener);

        void finished(Chat chat, TurnResult result);

        void needsYou(Chat chat);
    }

    private final Host host;
    private final Path workspace;
    private Transcript transcript = new Transcript();
    private String sessionId;
    private String title;
    private long updatedAt;
    private Status status = Status.IDLE;
    private String draft = "";
    private String model;
    private String activeModel;
    private String mode;
    private Session session;
    private PermissionRequest permission;
    private QuestionRequest question;
    private long turnStartedAt;
    private String step;
    private final StringBuilder thinking = new StringBuilder();
    private boolean historyLoaded;

    Chat(Host host, Path workspace) {
        this.host = host;
        this.workspace = workspace;
        this.historyLoaded = true;
        this.updatedAt = System.currentTimeMillis();
    }

    Chat(Host host, Path workspace, SessionSummary summary) {
        this.host = host;
        this.workspace = workspace;
        this.sessionId = summary.id();
        this.title = summary.title();
        this.updatedAt = summary.updatedAt();
        this.status = Status.DONE;
    }

    public void send(String text) {
        if (title == null) title = titleFrom(text);
        transcript.onUserMessage(text);
        try {
            if (session == null || !session.isOpen()) session = host.open(spec(text), this);
        } catch (IllegalStateException e) {
            transcript.notice(e.getMessage(), Theme.ERROR);
            status = Status.FAILED;
            return;
        }
        session.send(text);
        thinking.setLength(0);
        status = Status.WORKING;
        step = "Thinking";
        turnStartedAt = updatedAt = System.currentTimeMillis();
    }

    private SessionSpec spec(String firstMessage) {
        SessionSpec spec = new SessionSpec(workspace).model(model).permissionMode(mode);
        return sessionId != null ? spec.resume(sessionId) : spec.title(titleFrom(firstMessage));
    }

    public void interrupt() {
        if (session != null && status.isActive()) session.interrupt();
    }

    public void selectModel(String modelId) {
        model = modelId;
        if (session != null && session.isOpen()) session.setModel(modelId);
    }

    public void selectMode(String modeId) {
        mode = modeId;
        if (session != null && session.isOpen()) session.setPermissionMode(modeId);
    }

    public void close() {
        if (session != null) session.close();
        session = null;
    }

    void loadHistory(Transcript history) {
        if (transcript.isEmpty()) transcript = history;
        historyLoaded = true;
    }

    public void answerPermission(boolean allow, boolean remember) {
        if (permission == null) return;
        if (allow) permission.allow(remember);
        else permission.deny("The user declined this action.");
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
    public void onStarted(String sessionId, String model) {
        this.sessionId = sessionId;
        this.activeModel = model;
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
    public void onToolResult(String toolUseId, String output, boolean error) {
        transcript.onToolResult(toolUseId, output, error);
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
        host.finished(this, result);
    }

    @Override
    public void onError(String message) {
        transcript.notice(message, Theme.ERROR);
    }

    @Override
    public void onClosed() {
        session = null;
        if (!status.isActive()) return;
        transcript.settle();
        status = Status.FAILED;
        host.finished(this, new TurnResult(TurnResult.Outcome.FAILED, null, "Claude Code stopped", 0, 0));
    }

    private static String titleFrom(String text) {
        String line = text.trim().split("\n", 2)[0];
        return line.length() <= TITLE_LENGTH ? line : line.substring(0, TITLE_LENGTH - 1).trim() + "…";
    }

    public Path workspace() {
        return workspace;
    }

    public Transcript transcript() {
        return transcript;
    }

    public String sessionId() {
        return sessionId;
    }

    public String title() {
        return title != null ? title : "New chat";
    }

    public long updatedAt() {
        return updatedAt;
    }

    public Status status() {
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

    public String mode() {
        return mode;
    }

    public PermissionRequest permission() {
        return permission;
    }

    public QuestionRequest question() {
        return question;
    }

    public boolean historyLoaded() {
        return historyLoaded;
    }

    public boolean isNew() {
        return sessionId == null && transcript.isEmpty();
    }
}
