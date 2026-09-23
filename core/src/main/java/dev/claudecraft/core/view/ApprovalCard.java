package dev.claudecraft.core.view;

import dev.claudecraft.agent.PermissionRequest;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.chat.ToolLabels;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

final class ApprovalCard {
    static final int HEIGHT = 34;
    private static final int BUTTON_HEIGHT = 16;

    private ApprovalCard() {
    }

    static boolean isPlan(PermissionRequest request) {
        return "ExitPlanMode".equals(request.toolName());
    }

    static void render(Canvas canvas, Rect area, Chat chat, String feedback, Clicks clicks, double mouseX, double mouseY) {
        PermissionRequest request = chat.permission();
        area.fill(canvas, Theme.APPROVAL_BACKGROUND);
        int x = area.right() - Theme.PADDING;
        int y = area.y + (area.height - BUTTON_HEIGHT) / 2;
        boolean plan = isPlan(request);
        String decline = feedback.isEmpty() ? (plan ? "Keep planning (N)" : "No (N)") : "Tell Claude (Enter)";
        x = button(canvas, decline, x, y, clicks, mouseX, mouseY, () -> chat.answerPermission(false, false, declineMessage(plan, feedback)));
        if (plan) {
            x = button(canvas, "Yes (Y)", x, y, clicks, mouseX, mouseY, () -> chat.approvePlan(false));
            x = button(canvas, "Auto-accept (A)", x, y, clicks, mouseX, mouseY, () -> chat.approvePlan(true));
        } else {
            if (request.canRemember()) x = button(canvas, "Always (A)", x, y, clicks, mouseX, mouseY, () -> chat.answerPermission(true, true));
            x = button(canvas, "Yes (Y)", x, y, clicks, mouseX, mouseY, () -> chat.answerPermission(true, false));
        }
        int textWidth = x - area.x - 2 * Theme.PADDING;
        String title = plan ? "Start coding?" : "Allow " + request.title() + "?";
        canvas.text(TextWrap.ellipsize(canvas, title, textWidth, Canvas.BOLD), area.x + Theme.PADDING, area.y + 6, Theme.NEEDS_YOU, Canvas.BOLD | Canvas.SHADOW);
        canvas.text(TextWrap.ellipsize(canvas, detail(request), textWidth, 0), area.x + Theme.PADDING, area.y + 19, Theme.TEXT_SOFT);
    }

    static String declineMessage(boolean plan, String feedback) {
        if (!feedback.isEmpty()) return feedback;
        return plan ? "The user wants to keep planning." : "The user declined this action.";
    }

    private static String detail(PermissionRequest request) {
        if (isPlan(request)) return "Or type feedback to keep planning";
        String detail = ToolLabels.describe(request.toolName(), request.input())[1];
        return detail.isEmpty() ? request.toolName() : detail;
    }

    private static int button(Canvas canvas, String label, int right, int y, Clicks clicks, double mouseX, double mouseY, Runnable action) {
        int width = canvas.width(label) + 12;
        Rect bounds = new Rect(right - width, y, width, BUTTON_HEIGHT);
        Theme.button(canvas, bounds, label, bounds.contains(mouseX, mouseY), true);
        clicks.add(bounds, action);
        return bounds.x - 4;
    }
}
