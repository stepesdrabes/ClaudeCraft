package dev.claudecraft.core.view;

import dev.claudecraft.agent.McpServerInfo;
import dev.claudecraft.agent.Task;
import dev.claudecraft.agent.Usage;
import dev.claudecraft.core.ClaudeCraft;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.chat.Todos;
import dev.claudecraft.core.game.MinecraftTools;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.KeyPress;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.util.ArrayList;
import java.util.List;

final class SidePanel {
    enum Section { CONTEXT, TODOS, TASKS, MCP }

    private static final int LINE = 11;
    private static final int TWO_LINES = 20;
    private static final int HEADER = 14;
    private static final int SECTION_GAP = 8;
    private static final int KEPT_TASKS = 6;

    private static final class Row {
        final String text;
        final String detail;
        final int color;
        int dot;
        int percent = -1;
        int style;
        String button;
        int buttonColor = Theme.DIM;
        Runnable buttonAction;
        Runnable action;

        Row(String text, String detail, int color) {
            this.text = text;
            this.detail = detail;
            this.color = color;
        }

        int height() {
            return percent >= 0 ? TWO_LINES : detail != null ? TWO_LINES : LINE;
        }
    }

    private final ClaudeCraft app;
    private int scroll;
    private Rect area = new Rect(0, 0, 0, 0);

    SidePanel(ClaudeCraft app) {
        this.app = app;
    }

    void render(Canvas canvas, Rect area, Chat chat, Clicks clicks, double mouseX, double mouseY, long now) {
        this.area = area;
        area.fill(canvas, Theme.SIDEBAR);
        Rect inner = area.inset(Theme.PADDING, Theme.PADDING);
        int total = 0;
        for (Section section : Section.values()) total += height(section, chat, now) + SECTION_GAP;
        scroll = Math.max(0, Math.min(scroll, total - inner.height));
        canvas.pushClip(area.x, area.y, area.right(), area.bottom());
        int y = inner.y - scroll;
        for (Section section : Section.values()) {
            int height = height(section, chat, now);
            if (height == 0) continue;
            renderSection(canvas, section, new Rect(inner.x, y, inner.width, height), chat, clicks, mouseX, mouseY, now);
            y += height + SECTION_GAP;
        }
        canvas.popClip();
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (!area.contains(mouseX, mouseY)) return false;
        scroll -= (int) Math.signum(amount) * 3 * LINE;
        return true;
    }

    int height(Section section, Chat chat, long now) {
        List<Row> rows = rows(section, chat, now);
        if (rows.isEmpty()) return 0;
        int height = HEADER;
        for (Row row : rows) height += row.height();
        return height;
    }

    void renderSection(Canvas canvas, Section section, Rect area, Chat chat, Clicks clicks, double mouseX, double mouseY, long now) {
        List<Row> rows = rows(section, chat, now);
        int titleRight = area.right();
        Runnable headerAction = headerAction(section, chat);
        if (headerAction != null) {
            String label = "Background";
            Rect button = new Rect(area.right() - canvas.width(label) - 4, area.y, canvas.width(label) + 4, HEADER - 3);
            canvas.text(label, button.x + 2, area.y + 2, button.contains(mouseX, mouseY) ? Theme.WHITE : Theme.LINK);
            clicks.add(button, headerAction);
            titleRight = button.x - 4;
        }
        canvas.text(TextWrap.ellipsize(canvas, title(section, chat), titleRight - area.x, Canvas.BOLD), area.x, area.y + 2, Theme.MUTED, Canvas.BOLD);
        int y = area.y + HEADER;
        for (Row row : rows) {
            renderRow(canvas, row, new Rect(area.x, y, area.width, row.height()), clicks, mouseX, mouseY, now);
            y += row.height();
        }
    }

    private void renderRow(Canvas canvas, Row row, Rect bounds, Clicks clicks, double mouseX, double mouseY, long now) {
        if (row.action != null) {
            if (bounds.contains(mouseX, mouseY)) bounds.fill(canvas, Theme.HOVER);
            clicks.add(bounds, row.action);
        }
        int x = bounds.x + 2;
        if (row.dot != 0) {
            canvas.fill(x, bounds.y + 3, x + Theme.DOT, bounds.y + 3 + Theme.DOT, row.dot);
            x += Theme.DOT + 4;
        }
        int right = bounds.right() - 2;
        if (row.button != null) {
            Rect button = new Rect(right - canvas.width(row.button) - 4, bounds.y, canvas.width(row.button) + 4, LINE);
            canvas.text(row.button, button.x + 2, bounds.y + 1, button.contains(mouseX, mouseY) ? Theme.WHITE : row.buttonColor);
            clicks.add(button, row.buttonAction);
            right = button.x - 2;
        }
        canvas.text(TextWrap.ellipsize(canvas, row.text, right - x, row.style), x, bounds.y + 1, row.color, row.style);
        if (row.percent >= 0) {
            Rect bar = new Rect(x, bounds.y + 12, bounds.right() - 2 - x, 4);
            meter(canvas, bar, row.percent);
            if (row.detail != null) {
                canvas.text(row.detail, bounds.right() - 2 - canvas.width(row.detail), bounds.y + 1, Theme.DIM);
            }
        } else if (row.detail != null) {
            canvas.text(TextWrap.ellipsize(canvas, row.detail, bounds.right() - 2 - x, 0), x, bounds.y + 10, Theme.DIM);
        }
    }

    static void meter(Canvas canvas, Rect bar, int percent) {
        bar.fill(canvas, 0x40FFFFFF);
        int color = percent >= 90 ? Theme.ERROR : percent >= 70 ? Theme.NEEDS_YOU : Theme.CLAUDE;
        canvas.fill(bar.x, bar.y, bar.x + bar.width * Math.max(0, Math.min(100, percent)) / 100, bar.bottom(), color);
    }

    private String title(Section section, Chat chat) {
        switch (section) {
            case CONTEXT: return "Context";
            case TODOS: return "Todos  " + chat.todos().done() + "/" + chat.todos().items().size();
            case TASKS: return "Tasks";
            default: return "MCP servers";
        }
    }

    private Runnable headerAction(Section section, Chat chat) {
        if (section != Section.TASKS || !chat.isOpen()) return null;
        for (Task task : chat.tasks()) if (task.status().isActive() && !task.background()) return chat::backgroundTasks;
        return null;
    }

    private List<Row> rows(Section section, Chat chat, long now) {
        switch (section) {
            case CONTEXT: return contextRows(chat);
            case TODOS: return todoRows(chat, now);
            case TASKS: return taskRows(chat, now);
            default: return mcpRows(chat);
        }
    }

    private List<Row> contextRows(Chat chat) {
        List<Row> rows = new ArrayList<>();
        Usage.Context context = chat.context();
        if (context != null && context.max() > 0) {
            Row row = new Row(Chat.tokens(context.used()) + " of " + Chat.tokens(context.max()), context.percent() + "%", Theme.TEXT_SOFT);
            row.percent = context.percent();
            rows.add(row);
        } else {
            rows.add(new Row(chat.isOpen() ? "Measuring…" : "Shown after a reply", null, Theme.DIM));
        }
        return rows;
    }

    private List<Row> todoRows(Chat chat, long now) {
        List<Row> rows = new ArrayList<>();
        for (Todos.Item item : chat.todos().items()) {
            Row row;
            switch (item.state()) {
                case ACTIVE:
                    row = new Row("▶ " + item.label(), null, Theme.pulse(Theme.CLAUDE, Theme.TEXT, now));
                    break;
                case DONE:
                    row = new Row("✔ " + item.label(), null, Theme.DIM);
                    row.style = Canvas.STRIKETHROUGH;
                    break;
                default:
                    row = new Row("☐ " + item.label(), null, Theme.TEXT_SOFT);
            }
            rows.add(row);
        }
        return rows;
    }

    private List<Row> taskRows(Chat chat, long now) {
        List<Row> rows = new ArrayList<>();
        List<Task> tasks = new ArrayList<>(chat.tasks());
        tasks.sort((a, b) -> Boolean.compare(b.status().isActive(), a.status().isActive()));
        for (Task task : tasks.subList(0, Math.min(KEPT_TASKS, tasks.size()))) {
            String name = task.description().isEmpty() ? task.id() : task.description();
            Row row = new Row(name, taskDetail(task, now), Theme.TEXT);
            row.dot = taskColor(task, now);
            if (task.status().isActive() && chat.isOpen()) {
                row.button = "Stop";
                row.buttonColor = Theme.ERROR;
                row.buttonAction = () -> chat.stopTask(task.id());
            }
            rows.add(row);
        }
        return rows;
    }

    static String taskDetail(Task task, long now) {
        List<String> parts = new ArrayList<>();
        if (task.background()) parts.add("background");
        if (task.status().isActive() && task.activity() != null) parts.add(task.activity());
        else if (!task.status().isActive()) parts.add(task.status() == Task.Status.DONE ? "done" : task.status().name().toLowerCase());
        if (task.toolUses() > 0) parts.add(task.toolUses() + (task.toolUses() == 1 ? " tool" : " tools"));
        long duration = task.status().isActive() ? Math.max(task.durationMillis(), now - task.startedAt()) : task.durationMillis();
        if (duration > 0) parts.add(Format.elapsed(duration));
        return String.join(" · ", parts);
    }

    static int taskColor(Task task, long now) {
        switch (task.status()) {
            case RUNNING: return Theme.pulse(Theme.WORKING, Theme.WORKING_PULSE, now);
            case DONE: return Theme.DONE;
            case FAILED: return Theme.ERROR;
            default: return Theme.IDLE;
        }
    }

    private List<Row> mcpRows(Chat chat) {
        List<Row> rows = new ArrayList<>();
        List<McpServerInfo> servers = app.mcpServers(chat);
        if (servers == null) return rows;
        for (McpServerInfo server : servers) {
            Row row = new Row(server.name(), null, server.state() == McpServerInfo.State.DISABLED ? Theme.DIM : Theme.TEXT);
            row.dot = mcpColor(server.state());
            String action = mcpAction(server.state());
            if (chat.isOpen() && action != null && !MinecraftTools.NAMESPACE.equals(server.name())) {
                row.button = action;
                row.buttonAction = () -> {
                    if (server.state() == McpServerInfo.State.DISABLED) chat.setMcpServerEnabled(server.name(), true);
                    else if (server.state() == McpServerInfo.State.CONNECTED) chat.setMcpServerEnabled(server.name(), false);
                    else chat.reconnectMcpServer(server.name());
                };
            }
            rows.add(row);
        }
        return rows;
    }

    private static String mcpAction(McpServerInfo.State state) {
        switch (state) {
            case CONNECTED: return "Off";
            case DISABLED: return "On";
            case FAILED: return "Retry";
            default: return null;
        }
    }

    private static int mcpColor(McpServerInfo.State state) {
        switch (state) {
            case CONNECTED: return Theme.DONE;
            case PENDING: return Theme.WORKING;
            case NEEDS_AUTH: return Theme.NEEDS_YOU;
            case FAILED: return Theme.ERROR;
            default: return Theme.IDLE;
        }
    }

    Overlay popup(Section section, Chat chat, int anchorRight, int anchorY) {
        return new SectionPopup(section, chat, anchorRight, anchorY);
    }

    private final class SectionPopup implements Overlay {
        private static final int WIDTH = 220;
        private final Section section;
        private final Chat chat;
        private final int anchorRight;
        private final int anchorY;
        private final Clicks clicks = new Clicks();
        private Rect bounds = new Rect(0, 0, 0, 0);

        SectionPopup(Section section, Chat chat, int anchorRight, int anchorY) {
            this.section = section;
            this.chat = chat;
            this.anchorRight = anchorRight;
            this.anchorY = anchorY;
        }

        @Override
        public void render(Canvas canvas, int screenWidth, int screenHeight, double mouseX, double mouseY, long now) {
            clicks.clear();
            int height = Math.max(HEADER + LINE, height(section, chat, now)) + 2 * Theme.PADDING;
            int x = Math.max(4, anchorRight - WIDTH);
            int y = Math.max(4, Math.min(anchorY, screenHeight - height - 4));
            bounds = new Rect(x, y, WIDTH, height);
            Theme.tooltip(canvas, bounds);
            renderSection(canvas, section, bounds.inset(Theme.PADDING + 2, Theme.PADDING), chat, clicks, mouseX, mouseY, now);
        }

        @Override
        public boolean click(double mouseX, double mouseY) {
            if (!bounds.contains(mouseX, mouseY)) return false;
            clicks.click(mouseX, mouseY);
            return true;
        }

        @Override
        public boolean keyPressed(KeyPress press) {
            return false;
        }
    }
}
