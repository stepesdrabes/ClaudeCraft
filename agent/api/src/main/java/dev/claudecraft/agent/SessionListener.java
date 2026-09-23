package dev.claudecraft.agent;

import java.util.List;

public interface SessionListener {
    default void onStarted(String sessionId, String model, String cwd) {
    }

    default void onUserMessage(String text, List<ImageData> images) {
    }

    default void onText(String text) {
    }

    default void onThinking(String delta) {
    }

    default void onToolStarting(String toolName) {
    }

    default void onToolUse(ToolUse use) {
    }

    default void onToolResult(ToolOutput output) {
    }

    default void onSubagentToolUse(String parentToolUseId, ToolUse use) {
    }

    default void onTask(Task task) {
    }

    default void onPermissionRequest(PermissionRequest request) {
    }

    default void onQuestion(QuestionRequest request) {
    }

    default void onRequestCancelled(String requestId) {
    }

    default void onStatus(String status) {
    }

    default void onPermissionMode(String mode) {
    }

    default void onBusy(boolean busy) {
    }

    default void onCompacted(long tokensBefore, long tokensAfter) {
    }

    default void onNotice(String text) {
    }

    default void onPlanUsage(Usage.Plan usage) {
    }

    default void onTurnEnd(TurnResult result) {
    }

    default void onError(String message) {
    }

    default void onClosed() {
    }
}
