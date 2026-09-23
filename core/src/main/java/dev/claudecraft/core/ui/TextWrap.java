package dev.claudecraft.core.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TextWrap {
    private final Canvas canvas;
    private final int width;
    private final int firstIndent;
    private final int nextIndent;
    private final int background;
    private final int barColor;
    private final List<Line> lines = new ArrayList<>();
    private List<Span> current = new ArrayList<>();
    private int used;

    private TextWrap(Canvas canvas, int width, int firstIndent, int nextIndent, int background, int barColor) {
        this.canvas = canvas;
        this.width = width;
        this.firstIndent = firstIndent;
        this.nextIndent = nextIndent;
        this.background = background;
        this.barColor = barColor;
    }

    public static List<Line> wrap(Canvas canvas, List<Span> spans, int width, int indent) {
        return wrap(canvas, spans, width, indent, indent, 0, 0);
    }

    public static List<Line> wrap(Canvas canvas, List<Span> spans, int width, int firstIndent, int nextIndent, int background, int barColor) {
        TextWrap wrap = new TextWrap(canvas, width, firstIndent, nextIndent, background, barColor);
        for (Span span : spans) wrap.add(span);
        if (!wrap.current.isEmpty() || wrap.lines.isEmpty()) wrap.flush(true);
        return wrap.lines;
    }

    public static List<Line> wrap(Canvas canvas, String text, int color, int width) {
        return wrap(canvas, Collections.singletonList(new Span(text, color, 0)), width, 0);
    }

    public static String ellipsize(Canvas canvas, String text, int maxWidth, int style) {
        if (canvas.width(text, style) <= maxWidth) return text;
        String ellipsis = "...";
        int available = maxWidth - canvas.width(ellipsis, style);
        int end = text.length();
        while (end > 0 && canvas.width(text.substring(0, end), style) > available) end--;
        return text.substring(0, end).trim() + ellipsis;
    }

    private void add(Span span) {
        String text = span.text;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n') {
                flush(true);
                i++;
            } else if (c == ' ') {
                int end = i;
                while (end < text.length() && text.charAt(end) == ' ') end++;
                space(span.withText(text.substring(i, end)));
                i = end;
            } else {
                int end = i;
                while (end < text.length() && text.charAt(end) != ' ' && text.charAt(end) != '\n') end++;
                word(span.withText(text.substring(i, end)));
                i = end;
            }
        }
    }

    private void space(Span space) {
        if (current.isEmpty()) return;
        int w = canvas.width(space.text, space.style);
        if (used + w > available()) flush(false);
        else append(space, w);
    }

    private void word(Span word) {
        int w = canvas.width(word.text, word.style);
        if (used + w <= available()) {
            append(word, w);
            return;
        }
        if (!current.isEmpty()) flush(false);
        if (w <= available()) {
            append(word, w);
            return;
        }
        String rest = word.text;
        while (!rest.isEmpty()) {
            int end = rest.length();
            while (end > 1 && used + canvas.width(rest.substring(0, end), word.style) > available()) end--;
            String piece = rest.substring(0, end);
            append(word.withText(piece), canvas.width(piece, word.style));
            rest = rest.substring(end);
            if (!rest.isEmpty()) flush(false);
        }
    }

    private void append(Span span, int w) {
        int last = current.size() - 1;
        if (last >= 0 && current.get(last).color == span.color && current.get(last).style == span.style) {
            current.set(last, span.withText(current.get(last).text + span.text));
        } else {
            current.add(span);
        }
        used += w;
    }

    private int available() {
        return width - indent();
    }

    private int indent() {
        return lines.isEmpty() ? firstIndent : nextIndent;
    }

    private void flush(boolean hard) {
        if (!current.isEmpty() || hard) lines.add(new Line(trimTrailingSpace(current), indent(), background, barColor));
        current = new ArrayList<>();
        used = 0;
    }

    private static List<Span> trimTrailingSpace(List<Span> spans) {
        if (spans.isEmpty()) return spans;
        int last = spans.size() - 1;
        String trimmed = spans.get(last).text.replaceAll(" +$", "");
        if (trimmed.isEmpty()) spans.remove(last);
        else spans.set(last, spans.get(last).withText(trimmed));
        return spans;
    }
}
