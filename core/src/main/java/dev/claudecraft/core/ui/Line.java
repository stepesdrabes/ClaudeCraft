package dev.claudecraft.core.ui;

import java.util.Collections;
import java.util.List;

public final class Line {
    public static final Line BLANK = new Line(Collections.<Span>emptyList(), 0, 0, 0);

    public final List<Span> spans;
    public final int indent;
    public final int background;
    public final int barColor;
    private final int ruleColor;
    private final Span marker;
    private final int markerIndent;

    public Line(List<Span> spans, int indent, int background, int barColor) {
        this(spans, indent, background, barColor, 0, null, 0);
    }

    private Line(List<Span> spans, int indent, int background, int barColor, int ruleColor, Span marker, int markerIndent) {
        this.spans = spans;
        this.indent = indent;
        this.background = background;
        this.barColor = barColor;
        this.ruleColor = ruleColor;
        this.marker = marker;
        this.markerIndent = markerIndent;
    }

    public static Line rule(int color) {
        return new Line(Collections.<Span>emptyList(), 0, 0, 0, color, null, 0);
    }

    public Line withMarker(Span marker, int markerIndent) {
        return new Line(spans, indent, background, barColor, ruleColor, marker, markerIndent);
    }

    public void draw(Canvas canvas, int x, int y, int width) {
        int height = canvas.lineHeight() + 1;
        if (background != 0) canvas.fill(x + indent - 2, y - 1, x + width, y + height - 1, background);
        if (barColor != 0) canvas.fill(x + indent - 6, y - 1, x + indent - 4, y + height - 1, barColor);
        if (ruleColor != 0) canvas.fill(x, y + height / 2 - 1, x + width, y + height / 2, ruleColor);
        if (marker != null) canvas.text(marker.text, x + markerIndent, y, marker.color, marker.style);
        int cursor = x + indent;
        for (Span span : spans) {
            canvas.text(span.text, cursor, y, span.color, span.style);
            cursor += canvas.width(span.text, span.style);
        }
    }

    public String plainText() {
        StringBuilder out = new StringBuilder();
        for (Span span : spans) out.append(span.text);
        return out.toString();
    }
}
