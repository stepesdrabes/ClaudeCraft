package dev.claudecraft.core.view;

import dev.claudecraft.agent.Task;
import dev.claudecraft.agent.json.Json;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.chat.Entry;
import dev.claudecraft.core.chat.MessageEntry;
import dev.claudecraft.core.chat.NoticeEntry;
import dev.claudecraft.core.chat.Status;
import dev.claudecraft.core.chat.ToolEntry;
import dev.claudecraft.core.chat.Transcript;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Image;
import dev.claudecraft.core.ui.Line;
import dev.claudecraft.core.ui.Markdown;
import dev.claudecraft.core.ui.Picture;
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
    private static final int THUMBNAIL_HEIGHT = 56;
    private static final int THUMBNAIL_MAX_WIDTH = 150;
    private static final int PLAN_COLOR = 0xFF5EEAD4;
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
        final List<Picture> pictures;
        final int indent;
        final int height;

        Row(Line line, ToolEntry tool, int height) {
            this.line = line;
            this.tool = tool;
            this.pictures = null;
            this.indent = 0;
            this.height = height;
        }

        Row(List<Picture> pictures, int indent) {
            this.line = null;
            this.tool = null;
            this.pictures = pictures;
            this.indent = indent;
            this.height = THUMBNAIL_HEIGHT + 4;
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
    private final Consumer<Picture> onPicture;
    private Transcript cachedFor;
    private int scrollFromBottom;
    private int lastHeight;
    private Rect area = new Rect(0, 0, 0, 0);

    TranscriptView(Consumer<String> onPrompt, Consumer<Picture> onPicture) {
        this.onPrompt = onPrompt;
        this.onPicture = onPicture;
    }

    void render(Canvas canvas, Rect area, Chat chat, boolean gameWorkspace, Clicks clicks, double mouseX, double mouseY, long now) {
        this.area = area;
        if (chat.transcript() != cachedFor) {
            cache.clear();
            cachedFor = chat.transcript();
        }
        if (chat.transcript().isEmpty()) {
            if (chat.historyLoaded()) renderEmpty(canvas, area, gameWorkspace, clicks, mouseX, mouseY);
            else centered(canvas, area, "Loading…", Theme.MUTED);
            return;
        }
        int width = area.width - 4;
        List<Row> rows = rows(canvas, chat, width, now);
        int height = 0;
        for (Row row : rows) height += row.height;
        if (scrollFromBottom > 0 && lastHeight > 0) scrollFromBottom += height - lastHeight;
        scrollFromBottom = Math.max(0, Math.min(scrollFromBottom, height - area.height));
        lastHeight = height;
        int y = Math.min(area.y, area.bottom() - height) + scrollFromBottom;
        canvas.pushClip(area.x, area.y, area.right(), area.bottom());
        for (Row row : rows) {
            if (y + row.height >= area.y && y <= area.bottom()) {
                if (row.pictures != null) renderPictures(canvas, row, area.x + row.indent, y + 1, clicks, mouseX, mouseY);
                else {
                    if (row.tool != null) renderToolRow(canvas, row, new Rect(area.x, y - 1, width, row.height), clicks, mouseX, mouseY, now);
                    row.line.draw(canvas, area.x, y, width);
                }
            }
            y += row.height;
        }
        canvas.popClip();
        renderScrollbar(canvas, height);
    }

    void reset() {
        scrollFromBottom = lastHeight = 0;
    }

    private List<Row> rows(Canvas canvas, Chat chat, int width, long now) {
        int lineHeight = canvas.lineHeight() + 1;
        List<Row> rows = new ArrayList<>();
        Entry previous = null;
        for (Entry entry : chat.transcript().entries()) {
            boolean user = isUser(entry);
            boolean startsTurn = previous == null || isUser(previous);
            if (user) {
                if (previous != null) rows.add(new Row(Line.BLANK, null, lineHeight));
                rows.add(new Row(YOU, null, lineHeight));
            } else if (startsTurn && !(entry instanceof NoticeEntry)) {
                if (previous != null) rows.add(new Row(Line.BLANK, null, lineHeight));
                rows.add(new Row(CLAUDE, null, lineHeight));
            } else if (entry instanceof MessageEntry && previous instanceof ToolEntry) {
                rows.add(new Row(Line.BLANK, null, lineHeight));
            }
            ToolEntry tool = entry instanceof ToolEntry ? (ToolEntry) entry : null;
            for (Line line : lines(canvas, entry, width)) rows.add(new Row(line, tool, lineHeight));
            if (tool != null) {
                Line progress = progress(canvas, chat, tool, width, now);
                if (progress != null) rows.add(new Row(progress, tool, lineHeight));
            }
            List<Picture> pictures = entry instanceof MessageEntry ? ((MessageEntry) entry).images() : tool != null ? tool.images() : null;
            if (pictures != null && !pictures.isEmpty()) rows.add(new Row(pictures, tool != null ? OUTPUT_INDENT : 0));
            previous = entry;
        }
        if (chat.status().isActive()) {
            rows.add(new Row(Line.BLANK, null, lineHeight));
            rows.add(new Row(activity(canvas, chat, width, now), null, lineHeight));
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
        if (message.role() == MessageEntry.Role.USER) {
            return message.text().isEmpty() ? Collections.<Line>emptyList() : TextWrap.wrap(canvas, message.text(), Theme.TEXT_SOFT, width);
        }
        return Markdown.render(canvas, message.text() + (message.streaming() ? " ▍" : ""), width, Theme.TEXT);
    }

    private static List<Line> toolLines(Canvas canvas, ToolEntry tool, int width) {
        List<Line> lines = new ArrayList<>();
        int labelWidth = canvas.width(tool.label(), Canvas.BOLD);
        String detail = TextWrap.ellipsize(canvas, "  " + tool.detail(), width - TOOL_INDENT - labelWidth, 0);
        lines.add(new Line(Arrays.asList(new Span(tool.label(), Theme.TEXT_SOFT, Canvas.BOLD), new Span(detail, Theme.DIM, 0)), TOOL_INDENT, 0, 0));
        if (tool.state() == ToolEntry.State.RUNNING) {
            for (String child : tool.children()) {
                lines.add(new Line(Collections.singletonList(new Span(TextWrap.ellipsize(canvas, "↳ " + child, width - OUTPUT_INDENT, 0), Theme.DIM, 0)), OUTPUT_INDENT, 0, 0));
            }
        }
        if (!tool.expanded()) return lines;
        if ("ExitPlanMode".equals(tool.name())) {
            for (Line line : Markdown.render(canvas, tool.input().get("plan").asString(""), width - OUTPUT_INDENT - 4, Theme.TEXT)) {
                lines.add(line.boxed(OUTPUT_INDENT, PLAN_COLOR));
            }
            return lines;
        }
        if ("TodoWrite".equals(tool.name())) {
            for (Json todo : tool.input().get("todos").items()) lines.add(todoLine(canvas, todo, width));
            return lines;
        }
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

    private static Line todoLine(Canvas canvas, Json todo, int width) {
        String status = todo.get("status").asString("");
        String text = todo.get("content").asString("");
        Span span;
        if ("completed".equals(status)) span = new Span("✔ " + text, Theme.DIM, Canvas.STRIKETHROUGH);
        else if ("in_progress".equals(status)) span = new Span("▶ " + text, Theme.CLAUDE, 0);
        else span = new Span("☐ " + text, Theme.TEXT_SOFT, 0);
        return new Line(Collections.singletonList(span.withText(TextWrap.ellipsize(canvas, span.text, width - OUTPUT_INDENT, span.style))), OUTPUT_INDENT, 0, 0);
    }

    private static Line progress(Canvas canvas, Chat chat, ToolEntry tool, int width, long now) {
        Task task = chat.task(tool.id());
        if (task == null) return null;
        String text = "↳ " + SidePanel.taskDetail(task, now);
        return new Line(Collections.singletonList(new Span(TextWrap.ellipsize(canvas, text, width - OUTPUT_INDENT, 0),
            task.status().isActive() ? Theme.MUTED : Theme.DIM, 0)), OUTPUT_INDENT, 0, 0);
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

    private void renderPictures(Canvas canvas, Row row, int x, int y, Clicks clicks, double mouseX, double mouseY) {
        for (Picture picture : row.pictures) {
            Image image = picture.preview();
            int width = image == null ? THUMBNAIL_HEIGHT : Math.min(THUMBNAIL_MAX_WIDTH, image.width() * THUMBNAIL_HEIGHT / Math.max(1, image.height()));
            int height = image == null ? THUMBNAIL_HEIGHT : Math.min(THUMBNAIL_HEIGHT, image.height() * width / Math.max(1, image.width()));
            Rect frame = new Rect(x, y + (THUMBNAIL_HEIGHT - height) / 2, width, height);
            thumbnail(canvas, picture, frame, frame.contains(mouseX, mouseY));
            if (image != null) clicks.add(frame, () -> onPicture.accept(picture));
            x += width + 4;
            if (x > area.right()) break;
        }
    }

    static void thumbnail(Canvas canvas, Picture picture, Rect frame, boolean hovered) {
        Image image = picture.preview();
        canvas.outline(frame.x - 1, frame.y - 1, frame.right() + 1, frame.bottom() + 1, hovered ? Theme.WHITE : Theme.INPUT_BORDER);
        if (image != null) {
            canvas.image(image, frame.x, frame.y, frame.width, frame.height);
            return;
        }
        frame.fill(canvas, Theme.CODE_BACKGROUND);
        String text = picture.failed() ? "image" : "…";
        canvas.text(text, frame.x + (frame.width - canvas.width(text)) / 2, frame.y + (frame.height - canvas.lineHeight()) / 2 + 1, Theme.DIM);
    }

    private static Line activity(Canvas canvas, Chat chat, int width, long now) {
        if (chat.status() == Status.NEEDS_YOU) {
            String text = chat.inBackground() ? "■ Waiting for you in the background" : "■ Waiting for you";
            return new Line(Collections.singletonList(new Span(text, Theme.NEEDS_YOU, 0)), 0, 0, 0);
        }
        String spinner = "✻ ";
        String elapsed = chat.inBackground() ? "" : " " + Format.elapsed(now - chat.turnStartedAt());
        String thought = chat.thought();
        String step = chat.inBackground() ? "Working in the background" : thought != null ? thought : chat.step() != null ? chat.step() : "Working";
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
