package dev.claudecraft.core.view;

import dev.claudecraft.core.ClaudeCraft;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.chat.Status;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.util.List;
import java.util.function.Consumer;

final class Sidebar {
    private static final int WORKSPACE_ROW = 16;
    private static final int NEW_WIDTH = 40;

    private final ClaudeCraft app;
    private final Consumer<Chat> onSelect;
    private final Runnable onNew;
    private final Runnable onWorkspace;
    private int scroll;
    private Rect list = new Rect(0, 0, 0, 0);

    Sidebar(ClaudeCraft app, Consumer<Chat> onSelect, Runnable onNew, Runnable onWorkspace) {
        this.app = app;
        this.onSelect = onSelect;
        this.onNew = onNew;
        this.onWorkspace = onWorkspace;
    }

    Rect workspaceRow(Rect area) {
        return new Rect(area.x, area.y + Theme.HEADER, area.width, WORKSPACE_ROW);
    }

    void render(Canvas canvas, Rect area, Clicks clicks, double mouseX, double mouseY, long now) {
        area.fill(canvas, Theme.SIDEBAR);
        renderHeader(canvas, area.top(Theme.HEADER), clicks, mouseX, mouseY);
        renderWorkspace(canvas, workspaceRow(area), clicks, mouseX, mouseY);
        list = area.shrinkTop(Theme.HEADER + WORKSPACE_ROW + 3);
        canvas.fill(area.x + 4, list.y - 2, area.right() - 4, list.y - 1, Theme.SEPARATOR);
        renderChats(canvas, list, clicks, mouseX, mouseY, now);
    }

    private void renderHeader(Canvas canvas, Rect header, Clicks clicks, double mouseX, double mouseY) {
        int textY = header.y + (header.height - canvas.lineHeight()) / 2 + 1;
        canvas.text("Claude", header.x + Theme.PADDING, textY, Theme.WHITE, Canvas.SHADOW | Canvas.BOLD);
        int dotX = header.x + Theme.PADDING + canvas.width("Claude", Canvas.BOLD) + 5;
        canvas.fill(dotX, textY + 2, dotX + Theme.DOT, textY + 2 + Theme.DOT, connectionColor());
        Rect newButton = header.right(NEW_WIDTH).inset(0, 4);
        if (newButton.contains(mouseX, mouseY)) newButton.fill(canvas, Theme.HOVER);
        canvas.text("+ New", newButton.x + 4, textY, Theme.TEXT);
        clicks.add(newButton, onNew);
    }

    private int connectionColor() {
        if (app.connectorError() != null) return Theme.ERROR;
        return app.info() != null ? Theme.DONE : Theme.IDLE;
    }

    private void renderWorkspace(Canvas canvas, Rect row, Clicks clicks, double mouseX, double mouseY) {
        if (row.contains(mouseX, mouseY)) row.fill(canvas, Theme.HOVER);
        String label = "▾ " + Format.folder(app.chats().workspace());
        canvas.text(TextWrap.ellipsize(canvas, label, row.width - 2 * Theme.PADDING, 0), row.x + Theme.PADDING, row.y + 4, Theme.MUTED);
        clicks.add(row, onWorkspace);
    }

    private void renderChats(Canvas canvas, Rect area, Clicks clicks, double mouseX, double mouseY, long now) {
        List<Chat> chats = app.chats().visible();
        int visibleRows = Math.max(1, area.height / Theme.ROW);
        scroll = Math.max(0, Math.min(scroll, chats.size() - visibleRows));
        Chat selected = app.chats().selected();
        canvas.pushClip(area.x, area.y, area.right(), area.bottom());
        for (int i = scroll; i < chats.size() && i < scroll + visibleRows + 1; i++) {
            Chat chat = chats.get(i);
            Rect row = new Rect(area.x, area.y + (i - scroll) * Theme.ROW, area.width, Theme.ROW);
            if (chat == selected) row.fill(canvas, Theme.SELECTED);
            else if (row.contains(mouseX, mouseY)) row.fill(canvas, Theme.HOVER);
            renderChat(canvas, row, chat, now);
            clicks.add(row, () -> onSelect.accept(chat));
        }
        canvas.popClip();
    }

    private static void renderChat(Canvas canvas, Rect row, Chat chat, long now) {
        Status status = chat.status();
        int dotColor = status == Status.WORKING ? Theme.pulse(Theme.WORKING, Theme.WORKING_PULSE, now) : status.color();
        int textX = row.x + Theme.PADDING + Theme.DOT + 5;
        int available = row.right() - textX - 4;
        canvas.fill(row.x + Theme.PADDING, row.y + 5, row.x + Theme.PADDING + Theme.DOT, row.y + 5 + Theme.DOT, dotColor);
        canvas.text(TextWrap.ellipsize(canvas, chat.title(), available, 0), textX, row.y + 3, Theme.TEXT);
        canvas.text(TextWrap.ellipsize(canvas, meta(chat, now), available, 0), textX, row.y + 12, Theme.DIM);
    }

    private static String meta(Chat chat, long now) {
        if (chat.isNew()) return "Draft";
        if (chat.status().isActive()) return chat.status().label() + " " + Format.elapsed(now - chat.turnStartedAt());
        return chat.status().label() + " · " + Format.ago(chat.updatedAt(), now);
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (!list.contains(mouseX, mouseY)) return false;
        scroll -= (int) Math.signum(amount);
        return true;
    }
}
