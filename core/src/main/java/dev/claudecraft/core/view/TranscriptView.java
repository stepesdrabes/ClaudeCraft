package dev.claudecraft.core.view;

import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.chat.Entry;
import dev.claudecraft.core.chat.MessageEntry;
import dev.claudecraft.core.chat.NoticeEntry;
import dev.claudecraft.core.chat.Status;
import dev.claudecraft.core.chat.ToolEntry;
import dev.claudecraft.core.chat.Transcript;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Line;
import dev.claudecraft.core.ui.Markdown;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.Span;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class TranscriptView {
    private static final int TOOL_INDENT = 9;
    private static final int OUTPUT_INDENT = 14;
    private static final int OUTPUT_LINES = 12;
    private static final int SCROLL_LINES = 3;
    private static final Line YOU = label("You", Theme.USER);
    private static final Line CLAUDE = label("Claude", Theme.CLAUDE);
    private static final String[] LOGO = {
        "....#....", ".#..#..#.", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", ".#..#..#.", "....#...."};
    private static final List<String> GAME_PROMPTS = Arrays.asList(
        "Build a cozy oak cabin next to me", "What am I looking at?", "Make it a clear sunny morning");
    private static final List<String> CODE_PROMPTS = Arrays.asList(
        "Summarize this project", "What changed recently?", "Find something worth fixing");

    private static final class Row {
        final Line line;
        final ToolEntry tool;

        Row(Line line, ToolEntry tool) {
            this.line = line;
            this.tool = tool;
        }
    }

    private static final class Block {
        final int revision;
        final int width;
        final List<Line> lines;

        Block(int revision, int width, List<Line> lines) {
            this.revision = revision;
            this.width = width;
            this.lines = lines;
        }
    }

    private final Map<Entry, Block> cache = new IdentityHashMap<>();
    private final Consumer<String> onPrompt;
    private Transcript cachedFor;
    private int scrollFromBottom;
    private int lastHeight;
    private Rect area = new Rect(0, 0, 0, 0);

    TranscriptView(Consumer<String> onPrompt) {
        this.onPrompt = onPrompt;
    }

    void render(Canvas canvas, Rect area, Chat chat, boolean gameWorkspace, Clicks clicks, double mouseX, double mouseY, long now) {
        this.area = area;
        if (chat.transcript() != cachedFor) {
            cache.clear();
            cachedFor = chat.transcript();
            scrollFromBottom = lastHeight = 0;
        }
        if (chat.transcript().isEmpty()) {
            if (chat.historyLoaded()) renderEmpty(canvas, area, gameWorkspace, clicks, mouseX, mouseY);
            else centered(canvas, area, "Loading…", Theme.MUTED);
            return;
        }
        List<Row> rows = rows(canvas, chat, area.width - 4, now);
        int rowHeight = canvas.lineHeight() + 1;
        int height = rows.size() * rowHeight;
        if (scrollFromBottom > 0 && lastHeight > 0) scrollFromBottom += height - lastHeight;
        scrollFromBottom = Math.max(0, Math.min(scrollFromBottom, height - area.height));
        lastHeight = height;
        int top = Math.min(area.y, area.bottom() - height) + scrollFromBottom;
        canvas.pushClip(area.x, area.y, area.right(), area.bottom());
        for (int i = 0; i < rows.size(); i++) {
            int y = top + i * rowHeight;
            if (y + rowHeight < area.y || y > area.bottom()) continue;
            Row row = rows.get(i);
            if (row.tool != null) renderToolRow(canvas, row, new Rect(area.x, y - 1, area.width - 4, rowHeight), clicks, mouseX, mouseY, now);
            row.line.draw(canvas, area.x, y, area.width - 4);
        }
        canvas.popClip();
        renderScrollbar(canvas, height);
    }

    private List<Row> rows(Canvas canvas, Chat chat, int width, long now) {
        List<Row> rows = new ArrayList<>();
        Entry previous = null;
        for (Entry entry : chat.transcript().entries()) {
            boolean user = entry instanceof MessageEntry && ((MessageEntry) entry).role() == MessageEntry.Role.USER;
            boolean startsTurn = previous == null || isUser(previous);
            if (user) {
                if (previous != null) rows.add(new Row(Line.BLANK, null));
                rows.add(new Row(YOU, null));
            } else if (startsTurn && !(entry instanceof NoticeEntry)) {
                if (previous != null) rows.add(new Row(Line.BLANK, null));
                rows.add(new Row(CLAUDE, null));
            } else if (entry instanceof MessageEntry && previous instanceof ToolEntry) {
                rows.add(new Row(Line.BLANK, null));
            }
            ToolEntry tool = entry instanceof ToolEntry ? (ToolEntry) entry : null;
            for (Line line : lines(canvas, entry, width)) rows.add(new Row(line, tool));
            previous = entry;
        }
        Status status = chat.status();
        if (status.isActive()) {
            rows.add(new Row(Line.BLANK, null));
            rows.add(new Row(activity(canvas, chat, width, now), null));
        }
        return rows;
    }

    private List<Line> lines(Canvas canvas, Entry entry, int width) {
        Block cached = cache.get(entry);
        if (cached != null && cached.revision == entry.revision() && cached.width == width) return cached.lines;
        List<Line> lines = build(canvas, entry, width);
        cache.put(entry, new Block(entry.revision(), width, lines));
        return lines;
    }

    private static List<Line> build(Canvas canvas, Entry entry, int width) {
        if (entry instanceof ToolEntry) return toolLines(canvas, (ToolEntry) entry, width);
        if (entry instanceof NoticeEntry) {
            NoticeEntry notice = (NoticeEntry) entry;
            return TextWrap.wrap(canvas, Collections.singletonList(new Span(notice.text(), notice.color(), Canvas.ITALIC)), width, 0);
        }
        MessageEntry message = (MessageEntry) entry;
        if (message.role() == MessageEntry.Role.USER) return TextWrap.wrap(canvas, message.text(), Theme.TEXT_SOFT, width);
        return Markdown.render(canvas, message.text() + (message.streaming() ? " ▍" : ""), width, Theme.TEXT);
    }

    private static List<Line> toolLines(Canvas canvas, ToolEntry tool, int width) {
        List<Line> lines = new ArrayList<>();
        int labelWidth = canvas.width(tool.label(), Canvas.BOLD);
        String detail = TextWrap.ellipsize(canvas, "  " + tool.detail(), width - TOOL_INDENT - labelWidth, 0);
        lines.add(new Line(Arrays.asList(new Span(tool.label(), Theme.TEXT_SOFT, Canvas.BOLD), new Span(detail, Theme.DIM, 0)), TOOL_INDENT, 0, 0));
        if (!tool.expanded()) return lines;
        String[] output = tool.output().isEmpty() ? new String[]{"(no output)"} : tool.output().split("\n");
        for (int i = 0; i < output.length && i < OUTPUT_LINES; i++) {
            String text = TextWrap.ellipsize(canvas, output[i].replace("\t", "    "), width - OUTPUT_INDENT - 4, 0);
            lines.add(new Line(Collections.singletonList(new Span(text, Theme.MUTED, 0)), OUTPUT_INDENT, Theme.CODE_BACKGROUND, 0));
        }
        if (output.length > OUTPUT_LINES) {
            String more = "… " + (output.length - OUTPUT_LINES) + " more lines";
            lines.add(new Line(Collections.singletonList(new Span(more, Theme.DIM, 0)), OUTPUT_INDENT, Theme.CODE_BACKGROUND, 0));
        }
        return lines;
    }

    private void renderToolRow(Canvas canvas, Row row, Rect bounds, Clicks clicks, double mouseX, double mouseY, long now) {
        if (bounds.contains(mouseX, mouseY) && area.contains(mouseX, mouseY)) bounds.fill(canvas, Theme.HOVER);
        clicks.add(bounds, row.tool::toggle);
        if (row.line.indent != TOOL_INDENT) return;
        int color;
        switch (row.tool.state()) {
            case RUNNING: color = Theme.pulse(Theme.WORKING, Theme.WORKING_PULSE, now); break;
            case FAILED: color = Theme.ERROR; break;
            default: color = Theme.DONE;
        }
        canvas.fill(bounds.x + 1, bounds.y + 3, bounds.x + 1 + Theme.DOT, bounds.y + 3 + Theme.DOT, color);
    }

    private static Line activity(Canvas canvas, Chat chat, int width, long now) {
        if (chat.status() == Status.NEEDS_YOU) {
            return new Line(Collections.singletonList(new Span("■ Waiting for you", Theme.NEEDS_YOU, 0)), 0, 0, 0);
        }
        String spinner = "✻ ";
        String elapsed = " " + Format.elapsed(now - chat.turnStartedAt());
        String thought = chat.thought();
        String step = thought != null ? thought : chat.step() != null ? chat.step() : "Working";
        int style = thought != null ? Canvas.ITALIC : 0;
        int available = width - canvas.width(spinner) - canvas.width(elapsed);
        return new Line(Arrays.asList(
            new Span(spinner, Theme.pulse(Theme.CLAUDE, Theme.MUTED, now), 0),
            new Span(TextWrap.ellipsize(canvas, step + "…", available, style), Theme.TEXT_SOFT, style),
            new Span(elapsed, Theme.DIM, 0)), 0, 0, 0);
    }

    private void renderScrollbar(Canvas canvas, int height) {
        if (height <= area.height) return;
        int track = area.height;
        int thumb = Math.max(12, track * area.height / height);
        int offset = (track - thumb) * (height - area.height - scrollFromBottom) / (height - area.height);
        canvas.fill(area.right() - 2, area.y, area.right(), area.bottom(), Theme.SEPARATOR);
        canvas.fill(area.right() - 2, area.y + offset, area.right(), area.y + offset + thumb, Theme.MUTED);
    }

    private void renderEmpty(Canvas canvas, Rect area, boolean gameWorkspace, Clicks clicks, double mouseX, double mouseY) {
        int pixel = 2;
        int logoSize = LOGO.length * pixel;
        int blockHeight = logoSize + 12 + 2 * (canvas.lineHeight() + 2) + 3 * 18;
        int top = area.y + Math.max(4, (area.height - blockHeight) / 2);
        int logoX = area.x + (area.width - logoSize) / 2;
        for (int row = 0; row < LOGO.length; row++) {
            for (int col = 0; col < LOGO[row].length(); col++) {
                if (LOGO[row].charAt(col) != '#') continue;
                canvas.fill(logoX + col * pixel, top + row * pixel, logoX + (col + 1) * pixel, top + (row + 1) * pixel, Theme.CLAUDE);
            }
        }
        int y = top + logoSize + 8;
        centered(canvas, new Rect(area.x, y, area.width, canvas.lineHeight()), "How can I help?", Theme.WHITE);
        y += canvas.lineHeight() + 3;
        String subtitle = gameWorkspace ? "Claude can see and build in your world." : "Working in this project folder.";
        centered(canvas, new Rect(area.x, y, area.width, canvas.lineHeight()), subtitle, Theme.MUTED);
        y += canvas.lineHeight() + 10;
        for (String prompt : gameWorkspace ? GAME_PROMPTS : CODE_PROMPTS) {
            int width = Math.min(area.width - 8, canvas.width(prompt) + 16);
            Rect button = new Rect(area.x + (area.width - width) / 2, y, width, 15);
            button.fill(canvas, button.contains(mouseX, mouseY) ? Theme.SELECTED : Theme.HOVER);
            centered(canvas, button.inset(0, 3), TextWrap.ellipsize(canvas, prompt, width - 8, 0), Theme.TEXT_SOFT);
            clicks.add(button, () -> onPrompt.accept(prompt));
            y += 18;
        }
    }

    private static void centered(Canvas canvas, Rect area, String text, int color) {
        canvas.text(text, area.x + (area.width - canvas.width(text)) / 2, area.y + Math.max(0, (area.height - canvas.lineHeight()) / 2) + 1, color);
    }

    boolean scroll(double mouseX, double mouseY, double amount, int lineHeight) {
        if (!area.contains(mouseX, mouseY)) return false;
        scrollFromBottom += (int) Math.signum(amount) * SCROLL_LINES * (lineHeight + 1);
        return true;
    }

    private static boolean isUser(Entry entry) {
        return entry instanceof MessageEntry && ((MessageEntry) entry).role() == MessageEntry.Role.USER;
    }

    private static Line label(String text, int color) {
        return new Line(Collections.singletonList(new Span(text, color, Canvas.BOLD)), 0, 0, 0);
    }
}
