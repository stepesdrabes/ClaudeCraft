package dev.claudecraft.agent;

public interface SessionListener {
    default void onStarted(String sessionId, String model) {
    }

    default void onUserMessage(String text) {
    }

    default void onText(String text) {
    }

    default void onThinking(String delta) {
    }

    default void onToolStarting(String toolName) {
    }

    default void onToolUse(ToolUse use) {
    }

    default void onToolResult(String toolUseId, String output, boolean error) {
    }

    default void onPermissionRequest(PermissionRequest request) {
    }

    default void onQuestion(QuestionRequest request) {
    }

    default void onRequestCancelled(String requestId) {
    }

    default void onStatus(String status) {
    }

    default void onTurnEnd(TurnResult result) {
    }

    default void onError(String message) {
    }

    default void onClosed() {
    }
}
