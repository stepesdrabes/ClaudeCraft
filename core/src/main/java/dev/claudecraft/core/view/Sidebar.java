package dev.claudecraft.core.view;

import dev.claudecraft.agent.Usage;
import dev.claudecraft.core.ClaudeCraft;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.chat.Status;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.util.List;

final class Sidebar {
    interface Actions {
        void select(Chat chat);

        void startNew();

        void newMenu(Rect anchor);

        void workspaces();

        void chatMenu(Chat chat, int x, int y);
    }

    private static final int WORKSPACE_ROW = 16;
    private static final int NEW_WIDTH = 40;
    private static final int MENU_WIDTH = 12;
    private static final int FOOTER_LINE = 11;
    private static final int METER = 18;

    private final ClaudeCraft app;
    private final Actions actions;
    private int scroll;
    private Rect list = new Rect(0, 0, 0, 0);

    Sidebar(ClaudeCraft app, Actions actions) {
        this.app = app;
        this.actions = actions;
    }

    Rect workspaceRow(Rect area) {
        return new Rect(area.x, area.y + Theme.HEADER, area.width, WORKSPACE_ROW);
    }

    void render(Canvas canvas, Rect area, Clicks clicks, double mouseX, double mouseY, long now) {
        area.fill(canvas, Theme.SIDEBAR);
        renderHeader(canvas, area.top(Theme.HEADER), clicks, mouseX, mouseY);
        renderWorkspace(canvas, workspaceRow(area), clicks, mouseX, mouseY);
        int footer = footerHeight();
        list = area.shrinkTop(Theme.HEADER + WORKSPACE_ROW + 3).shrinkBottom(footer);
        canvas.fill(area.x + 4, list.y - 2, area.right() - 4, list.y - 1, Theme.SEPARATOR);
        renderChats(canvas, list, clicks, mouseX, mouseY, now);
        if (footer > 0) renderFooter(canvas, area.bottom(footer), clicks, mouseX, mouseY, now);
    }

    private void renderHeader(Canvas canvas, Rect header, Clicks clicks, double mouseX, double mouseY) {
        int textY = header.y + (header.height - canvas.lineHeight()) / 2 + 1;
        canvas.text("Claude", header.x + Theme.PADDING, textY, Theme.WHITE, Canvas.SHADOW | Canvas.BOLD);
        int dotX = header.x + Theme.PADDING + canvas.width("Claude", Canvas.BOLD) + 5;
        canvas.fill(dotX, textY + 2, dotX + Theme.DOT, textY + 2 + Theme.DOT, connectionColor());
        Rect menu = header.right(MENU_WIDTH + 2).inset(0, 4);
        Rect newButton = new Rect(menu.x - NEW_WIDTH, menu.y, NEW_WIDTH, menu.height);
        if (newButton.contains(mouseX, mouseY)) newButton.fill(canvas, Theme.HOVER);
        if (menu.contains(mouseX, mouseY)) menu.fill(canvas, Theme.HOVER);
        canvas.text("+ New", newButton.x + 4, textY, Theme.TEXT);
        canvas.text("▾", menu.x + 3, textY, Theme.MUTED);
        clicks.add(newButton, actions::startNew);
        clicks.add(menu, () -> actions.newMenu(menu));
    }

    private int connectionColor() {
        if (app.connectorError() != null) return Theme.ERROR;
        return app.info() != null ? Theme.DONE : Theme.IDLE;
    }

    private void renderWorkspace(Canvas canvas, Rect row, Clicks clicks, double mouseX, double mouseY) {
        if (row.contains(mouseX, mouseY)) row.fill(canvas, Theme.HOVER);
        String label = "▾ " + Format.folder(app.chats().workspace());
        canvas.text(TextWrap.ellipsize(canvas, label, row.width - 2 * Theme.PADDING, 0), row.x + Theme.PADDING, row.y + 4, Theme.MUTED);
        clicks.add(row, actions::workspaces);
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
            boolean hovered = row.contains(mouseX, mouseY) && area.contains(mouseX, mouseY);
            if (chat == selected) row.fill(canvas, Theme.SELECTED);
            else if (hovered) row.fill(canvas, Theme.HOVER);
            renderChat(canvas, row, chat, hovered, now);
            clicks.add(row, () -> actions.select(chat));
            if (hovered && !chat.isNew()) {
                Rect more = new Rect(row.right() - 14, row.y + 2, 12, 10);
                canvas.text("⋯", more.x + 2, more.y + 1, more.contains(mouseX, mouseY) ? Theme.WHITE : Theme.MUTED);
                clicks.add(more, () -> actions.chatMenu(chat, more.x, more.bottom()));
            }
        }
        canvas.popClip();
    }

    private static void renderChat(Canvas canvas, Rect row, Chat chat, boolean hovered, long now) {
        Status status = chat.status();
        int dotColor = status == Status.WORKING ? Theme.pulse(Theme.WORKING, Theme.WORKING_PULSE, now) : status.color();
        int textX = row.x + Theme.PADDING + Theme.DOT + 5;
        int available = row.right() - textX - (hovered ? 16 : 4);
        canvas.fill(row.x + Theme.PADDING, row.y + 5, row.x + Theme.PADDING + Theme.DOT, row.y + 5 + Theme.DOT, dotColor);
        String title = (chat.inWorktree() ? "⎇ " : "") + chat.title();
        canvas.text(TextWrap.ellipsize(canvas, title, available, 0), textX, row.y + 3, chat.archived() ? Theme.MUTED : Theme.TEXT);
        canvas.text(TextWrap.ellipsize(canvas, meta(chat, now), available, 0), textX, row.y + 12, Theme.DIM);
    }

    private static String meta(Chat chat, long now) {
        if (chat.isNew()) return chat.inWorktree() ? "Draft · new worktree" : "Draft";
        String place = chat.inBackground() ? "Background · " : chat.runningElsewhere() ? "In a terminal · " : chat.archived() ? "Archived · " : "";
        if (chat.inBackground()) return place + chat.status().label();
        if (chat.status().isActive()) return place + chat.status().label() + " " + Format.elapsed(now - chat.turnStartedAt());
        return place + chat.status().label() + " · " + Format.ago(chat.updatedAt(), now);
    }

    private int footerHeight() {
        int height = 0;
        if (app.chats().archivedCount() > 0) height += FOOTER_LINE + 2;
        Usage.Plan usage = app.planUsage();
        if (usage != null) height += Math.min(2, usage.limits().size()) * METER + 4;
        return height;
    }

    private void renderFooter(Canvas canvas, Rect area, Clicks clicks, double mouseX, double mouseY, long now) {
        canvas.fill(area.x + 4, area.y, area.right() - 4, area.y + 1, Theme.SEPARATOR);
        int y = area.y + 3;
        int archived = app.chats().archivedCount();
        if (archived > 0) {
            String label = app.chats().showArchived() ? "Hide archived" : "Show archived (" + archived + ")";
            Rect row = new Rect(area.x, y - 1, area.width, FOOTER_LINE);
            if (row.contains(mouseX, mouseY)) row.fill(canvas, Theme.HOVER);
            canvas.text(label, area.x + Theme.PADDING, y + 1, Theme.MUTED);
            clicks.add(row, app.chats()::toggleArchived);
            y += FOOTER_LINE + 2;
        }
        Usage.Plan usage = app.planUsage();
        if (usage == null) return;
        for (Usage.Limit limit : usage.limits().subList(0, Math.min(2, usage.limits().size()))) {
            int x = area.x + Theme.PADDING;
            int width = area.width - 2 * Theme.PADDING;
            String value = limit.percent() + "%" + (limit.resetsAt() > now ? " · " + Format.until(limit.resetsAt(), now) : "");
            canvas.text(limit.label(), x, y + 1, Theme.MUTED);
            canvas.text(value, x + width - canvas.width(value), y + 1, Theme.DIM);
            SidePanel.meter(canvas, new Rect(x, y + 11, width, 3), limit.percent());
            y += METER;
        }
    }

    Chat chatAt(double mouseX, double mouseY) {
        if (!list.contains(mouseX, mouseY)) return null;
        int index = scroll + (int) ((mouseY - list.y) / Theme.ROW);
        List<Chat> chats = app.chats().visible();
        return index >= 0 && index < chats.size() ? chats.get(index) : null;
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (!list.contains(mouseX, mouseY)) return false;
        scroll -= (int) Math.signum(amount);
        return true;
    }
}
