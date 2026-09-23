package dev.claudecraft.core.ui;

import java.util.ArrayList;
import java.util.List;

public final class TextField {
    public interface Clipboard {
        String get();

        void set(String text);
    }

    private static final int PAD_X = 4;
    private static final int PAD_Y = 3;

    private final TextMetrics metrics;
    private final Clipboard clipboard;
    private String value = "";
    private int cursor;
    private int anchor;
    private int scroll;
    private long lastEdit;
    private Rect area = new Rect(0, 0, 0, 0);

    public TextField(TextMetrics metrics, Clipboard clipboard) {
        this.metrics = metrics;
        this.clipboard = clipboard;
    }

    public String value() {
        return value;
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public void setValue(String text) {
        value = text;
        cursor = anchor = text.length();
        touch();
    }

    public void insert(String text) {
        int start = Math.min(cursor, anchor);
        value = value.substring(0, start) + text + value.substring(Math.max(cursor, anchor));
        cursor = anchor = start + text.length();
        touch();
    }

    public int height(int width, int maxLines) {
        int lines = Math.min(maxLines, layout(width - 2 * PAD_X).size());
        return lines * rowHeight() + 2 * PAD_Y + 1;
    }

    public boolean charTyped(char c) {
        if (c < ' ' || c == 127 || c == '§') return false;
        insert(String.valueOf(c));
        return true;
    }

    public boolean keyPressed(KeyPress press) {
        boolean select = press.shift();
        switch (press.key()) {
            case BACKSPACE: return delete(press.wordJump() ? previousWord(cursor) : cursor > 0 ? Emoji.previous(value, cursor) : 0);
            case DELETE: return delete(press.wordJump() ? nextWord(cursor) : cursor < value.length() ? Emoji.next(value, cursor) : cursor);
            case LEFT: return moveTo(horizontal(-1, press), select);
            case RIGHT: return moveTo(horizontal(1, press), select);
            case HOME: return moveTo(press.shortcut() ? 0 : currentLine()[0], select);
            case END: return moveTo(press.shortcut() ? value.length() : currentLine()[1], select);
            case UP: return vertical(-1, select);
            case DOWN: return vertical(1, select);
            default: return press.shortcut() && shortcut(press.key());
        }
    }

    public void click(double mouseX, double mouseY, boolean extend) {
        moveTo(indexAt(mouseX, mouseY), extend);
    }

    public void drag(double mouseX, double mouseY) {
        moveTo(indexAt(mouseX, mouseY), true);
    }

    public void render(Canvas canvas, Rect area, boolean focused, String placeholder, long now) {
        this.area = area;
        area.fill(canvas, Theme.INPUT_BACKGROUND);
        canvas.outline(area.x, area.y, area.right(), area.bottom(), focused ? Theme.INPUT_BORDER_FOCUSED : Theme.INPUT_BORDER);
        Rect inner = inner();
        List<int[]> lines = layout(inner.width);
        int visible = Math.max(1, (inner.height + 1) / rowHeight());
        int cursorLine = lineIndex(lines, cursor);
        scroll = Math.max(Math.min(scroll, cursorLine), cursorLine - visible + 1);
        if (value.isEmpty()) canvas.text(TextWrap.ellipsize(canvas, placeholder, inner.width, 0), inner.x, inner.y + 1, Theme.DIM);
        canvas.pushClip(inner.x - 1, inner.y, inner.right() + 1, inner.bottom());
        for (int i = scroll; i < lines.size() && i < scroll + visible; i++) {
            int[] line = lines.get(i);
            int y = inner.y + 1 + (i - scroll) * rowHeight();
            highlightSelection(canvas, line, inner.x, y);
            canvas.text(slice(line[0], line[1]), inner.x, y, Theme.TEXT);
            if (focused && i == cursorLine && (now - lastEdit) % 1000 < 500) {
                int x = inner.x + metrics.width(slice(line[0], cursor));
                canvas.fill(x, y - 1, x + 1, y + rowHeight() - 1, Theme.INPUT_BORDER_FOCUSED);
            }
        }
        canvas.popClip();
    }

    private void highlightSelection(Canvas canvas, int[] line, int x, int y) {
        int start = Math.max(Math.min(cursor, anchor), line[0]);
        int end = Math.min(Math.max(cursor, anchor), line[1]);
        if (start >= end) return;
        canvas.fill(x + metrics.width(slice(line[0], start)), y - 1, x + metrics.width(slice(line[0], end)), y + rowHeight() - 1, Theme.SELECTION);
    }

    private boolean shortcut(Key key) {
        switch (key) {
            case A:
                anchor = 0;
                cursor = value.length();
                return true;
            case C:
                if (cursor != anchor) clipboard.set(selection());
                return true;
            case X:
                if (cursor != anchor) {
                    clipboard.set(selection());
                    insert("");
                }
                return true;
            case V:
                String pasted = clipboard.get();
                if (pasted != null) insert(pasted.replace("\r", "").replace("§", ""));
                return true;
            default:
                return false;
        }
    }

    private String selection() {
        return value.substring(Math.min(cursor, anchor), Math.max(cursor, anchor));
    }

    private boolean delete(int to) {
        if (cursor == anchor) anchor = Math.max(0, Math.min(value.length(), to));
        insert("");
        return true;
    }

    private int horizontal(int direction, KeyPress press) {
        if (cursor != anchor && !press.shift()) return direction < 0 ? Math.min(cursor, anchor) : Math.max(cursor, anchor);
        if (press.wordJump()) return direction < 0 ? previousWord(cursor) : nextWord(cursor);
        if (direction < 0) return cursor > 0 ? Emoji.previous(value, cursor) : 0;
        return cursor < value.length() ? Emoji.next(value, cursor) : cursor;
    }

    private boolean vertical(int direction, boolean select) {
        List<int[]> lines = layout(inner().width);
        int target = lineIndex(lines, cursor) + direction;
        if (target < 0 || target >= lines.size()) return false;
        int x = metrics.width(slice(currentLine()[0], cursor));
        return moveTo(indexAtX(lines.get(target), x), select);
    }

    private boolean moveTo(int index, boolean select) {
        cursor = index;
        if (!select) anchor = index;
        touch();
        return true;
    }

    private int previousWord(int from) {
        int i = from;
        while (i > 0 && Character.isWhitespace(value.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(value.charAt(i - 1))) i--;
        return i;
    }

    private int nextWord(int from) {
        int i = from;
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        while (i < value.length() && !Character.isWhitespace(value.charAt(i))) i++;
        return i;
    }

    private int[] currentLine() {
        List<int[]> lines = layout(inner().width);
        return lines.get(lineIndex(lines, cursor));
    }

    private int indexAt(double mouseX, double mouseY) {
        List<int[]> lines = layout(inner().width);
        int row = scroll + (int) Math.floor((mouseY - inner().y - 1) / rowHeight());
        return indexAtX(lines.get(Math.max(0, Math.min(lines.size() - 1, row))), (int) Math.round(mouseX - inner().x));
    }

    private int indexAtX(int[] line, int x) {
        int index = line[0];
        while (index < line[1]) {
            int next = Math.min(line[1], Emoji.next(value, index));
            if (metrics.width(slice(line[0], next)) - metrics.width(slice(index, next)) / 2 > x) break;
            index = next;
        }
        return index;
    }

    private static int lineIndex(List<int[]> lines, int index) {
        for (int i = lines.size() - 1; i > 0; i--) if (index >= lines.get(i)[0]) return i;
        return 0;
    }

    private List<int[]> layout(int width) {
        List<int[]> lines = new ArrayList<>();
        int start = 0;
        while (true) {
            int hardEnd = value.indexOf('\n', start);
            if (hardEnd < 0) hardEnd = value.length();
            int end = start;
            int used = 0;
            int lastSpace = -1;
            while (end < hardEnd) {
                int next = Math.min(hardEnd, Emoji.next(value, end));
                int w = metrics.width(value.substring(end, next));
                if (used + w > width) break;
                if (value.charAt(end) == ' ') lastSpace = end;
                used += w;
                end = next;
            }
            if (end < hardEnd) {
                int wrapAt = lastSpace >= start ? lastSpace + 1 : Math.max(end, Emoji.next(value, start));
                lines.add(new int[]{start, wrapAt});
                start = wrapAt;
            } else {
                lines.add(new int[]{start, hardEnd});
                if (hardEnd >= value.length()) return lines;
                start = hardEnd + 1;
            }
        }
    }

    private String slice(int start, int end) {
        return value.substring(start, end).replace("\n", "");
    }

    private Rect inner() {
        return area.inset(PAD_X, PAD_Y);
    }

    private int rowHeight() {
        return metrics.lineHeight() + 1;
    }

    private void touch() {
        lastEdit = System.currentTimeMillis();
    }
}
