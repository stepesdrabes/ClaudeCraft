package dev.claudecraft.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Markdown {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$");
    private static final Pattern RULE = Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern TABLE_DIVIDER = Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$");
    private static final String[] BULLETS = {"•", "◦", "▪"};
    private static final int LIST_INDENT = 10;
    private static final int QUOTE_INDENT = 8;
    private static final int CODE_INDENT = 4;

    private final Canvas canvas;
    private final int width;
    private final int color;
    private final List<Line> lines = new ArrayList<>();

    private Markdown(Canvas canvas, int width, int color) {
        this.canvas = canvas;
        this.width = width;
        this.color = color;
    }

    public static List<Line> render(Canvas canvas, String markdown, int width, int color) {
        Markdown renderer = new Markdown(canvas, width, color);
        renderer.blocks(markdown.replace("\r", "").split("\n", -1));
        return renderer.lines;
    }

    private void blocks(String[] source) {
        boolean inCode = false;
        for (String raw : source) {
            String line = raw.replace("\t", "    ");
            if (line.trim().startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (inCode) code(line);
            else block(line);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1) == Line.BLANK) lines.remove(lines.size() - 1);
    }

    private void block(String line) {
        if (line.trim().isEmpty()) {
            if (!lines.isEmpty() && lines.get(lines.size() - 1) != Line.BLANK) lines.add(Line.BLANK);
            return;
        }
        Matcher heading = HEADING.matcher(line);
        Matcher item = LIST_ITEM.matcher(line);
        if (heading.matches()) {
            int headingColor = heading.group(1).length() <= 2 ? Theme.WHITE : color;
            lines.addAll(TextWrap.wrap(canvas, inline(heading.group(2), headingColor, Canvas.BOLD), width, 0));
        } else if (RULE.matcher(line).matches()) {
            lines.add(Line.rule(Theme.SEPARATOR));
        } else if (line.trim().startsWith(">")) {
            String quoted = line.trim().replaceFirst("^>+\\s?", "");
            lines.addAll(TextWrap.wrap(canvas, inline(quoted, Theme.MUTED, 0), width, QUOTE_INDENT, QUOTE_INDENT, 0, Theme.QUOTE_BAR));
        } else if (item.matches()) {
            listItem(item.group(1).length() / 2, item.group(2), item.group(3));
        } else if (line.trim().startsWith("|")) {
            if (!TABLE_DIVIDER.matcher(line).matches()) tableRow(line.trim());
        } else {
            lines.addAll(TextWrap.wrap(canvas, inline(line.trim(), color, 0), width, 0));
        }
    }

    private void listItem(int depth, String marker, String text) {
        String bullet = Character.isDigit(marker.charAt(0)) ? marker : BULLETS[Math.min(depth, BULLETS.length - 1)];
        int indent = depth * LIST_INDENT;
        int textIndent = indent + Math.max(LIST_INDENT, canvas.width(bullet + " "));
        List<Line> wrapped = TextWrap.wrap(canvas, inline(text, color, 0), width, textIndent, textIndent, 0, 0);
        wrapped.set(0, wrapped.get(0).withMarker(new Span(bullet, Theme.MUTED, 0), indent));
        lines.addAll(wrapped);
    }

    private void tableRow(String row) {
        String[] cells = row.replaceAll("^\\|", "").replaceAll("\\|$", "").split("\\|");
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) spans.add(new Span(" │ ", Theme.FAINT, 0));
            spans.addAll(inline(cells[i].trim(), color, 0));
        }
        lines.addAll(TextWrap.wrap(canvas, spans, width, 0));
    }

    private void code(String line) {
        int available = width - CODE_INDENT - 2;
        String rest = line;
        do {
            int end = rest.length();
            while (end > 1 && canvas.width(rest.substring(0, end)) > available) end--;
            List<Span> spans = new ArrayList<>();
            spans.add(new Span(rest.substring(0, end), Theme.CODE, 0));
            lines.add(new Line(spans, CODE_INDENT, Theme.CODE_BACKGROUND, 0));
            rest = rest.substring(end);
        } while (!rest.isEmpty());
    }

    static List<Span> inline(String text, int color, int baseStyle) {
        List<Span> spans = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int style = baseStyle;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int codeEnd = c == '`' ? text.indexOf('`', i + 1) : -1;
            if (codeEnd > i + 1) {
                flush(spans, plain, color, style);
                spans.add(new Span(text.substring(i + 1, codeEnd), Theme.CODE, style & ~Canvas.ITALIC));
                i = codeEnd + 1;
            } else if (toggles(text, i, "**", style, Canvas.BOLD) || toggles(text, i, "__", style, Canvas.BOLD)) {
                flush(spans, plain, color, style);
                style ^= Canvas.BOLD;
                i += 2;
            } else if (toggles(text, i, "~~", style, Canvas.STRIKETHROUGH)) {
                flush(spans, plain, color, style);
                style ^= Canvas.STRIKETHROUGH;
                i += 2;
            } else if ((c == '*' || c == '_') && togglesItalic(text, i, style)) {
                flush(spans, plain, color, style);
                style ^= Canvas.ITALIC;
                i++;
            } else if (c == '[' && link(text, i) != null) {
                String[] link = link(text, i);
                flush(spans, plain, color, style);
                spans.add(new Span(link[0], Theme.LINK, style | Canvas.UNDERLINE));
                i = Integer.parseInt(link[1]);
            } else {
                plain.append(c);
                i++;
            }
        }
        flush(spans, plain, color, style);
        return spans;
    }

    private static boolean toggles(String text, int i, String marker, int style, int flag) {
        if (!text.startsWith(marker, i)) return false;
        if ((style & flag) != 0) return true;
        int close = text.indexOf(marker, i + marker.length());
        return close > i + marker.length() && text.charAt(i + marker.length()) != ' ';
    }

    private static boolean togglesItalic(String text, int i, int style) {
        char marker = text.charAt(i);
        char before = i > 0 ? text.charAt(i - 1) : ' ';
        char after = i + 1 < text.length() ? text.charAt(i + 1) : ' ';
        if ((style & Canvas.ITALIC) != 0) return before != ' ' && !(marker == '_' && Character.isLetterOrDigit(after));
        if (after == ' ' || after == marker || (marker == '_' && Character.isLetterOrDigit(before))) return false;
        int close = text.indexOf(marker, i + 1);
        return close > i + 1;
    }

    private static String[] link(String text, int i) {
        int labelEnd = text.indexOf("](", i);
        int urlEnd = labelEnd < 0 ? -1 : text.indexOf(')', labelEnd);
        if (labelEnd <= i + 1 || urlEnd < 0) return null;
        return new String[]{text.substring(i + 1, labelEnd), String.valueOf(urlEnd + 1)};
    }

    private static void flush(List<Span> spans, StringBuilder plain, int color, int style) {
        if (plain.length() == 0) return;
        spans.add(new Span(plain.toString(), color, style));
        plain.setLength(0);
    }
}
