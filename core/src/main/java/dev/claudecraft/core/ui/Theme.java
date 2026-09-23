package dev.claudecraft.core.ui;

public final class Theme {
    public static final int SIDEBAR = 0xC0101014;
    public static final int CONVERSATION = 0xB0101014;
    public static final int HOVER = 0x20FFFFFF;
    public static final int SELECTED = 0x40FFFFFF;
    public static final int SEPARATOR = 0x30FFFFFF;
    public static final int SCRIM = 0x60000000;

    public static final int WHITE = 0xFFFFFFFF;
    public static final int TEXT = 0xFFE5E7EB;
    public static final int TEXT_SOFT = 0xFFD1D5DB;
    public static final int MUTED = 0xFF9CA3AF;
    public static final int DIM = 0xFF6B7280;
    public static final int FAINT = 0xFF4B5563;

    public static final int CLAUDE = 0xFFE08A6A;
    public static final int USER = 0xFF93C5FD;
    public static final int CODE = 0xFFF0C674;
    public static final int CODE_BACKGROUND = 0x70000000;
    public static final int LINK = 0xFF7DD3FC;
    public static final int QUOTE_BAR = 0x60FFFFFF;

    public static final int WORKING = 0xFF5EA8FF;
    public static final int WORKING_PULSE = 0xFF2F5F99;
    public static final int NEEDS_YOU = 0xFFFFB02E;
    public static final int DONE = 0xFF4ADE80;
    public static final int ERROR = 0xFFF87171;
    public static final int IDLE = 0xFF9CA3AF;

    public static final int APPROVAL_BACKGROUND = 0x40FFB02E;
    public static final int QUESTION_BACKGROUND = 0x405EA8FF;
    public static final int QUESTION_PICKED = 0x505EA8FF;

    public static final int INPUT_BACKGROUND = 0xFF000000;
    public static final int INPUT_BORDER = 0xFFA0A0A0;
    public static final int INPUT_BORDER_FOCUSED = 0xFFFFFFFF;
    public static final int SELECTION = 0xFF3050A0;

    public static final int TOOLTIP_BACKGROUND = 0xF0100010;
    public static final int TOOLTIP_BORDER_TOP = 0x505000FF;
    public static final int TOOLTIP_BORDER_BOTTOM = 0x5028007F;
    public static final int SUGGESTION_SELECTED = 0xFFFFFF55;
    public static final int SUGGESTION = 0xFFAAAAAA;

    public static final int BUTTON_FACE = 0xFF6F6F6F;
    public static final int BUTTON_FACE_HOVER = 0xFF7F8CB8;
    public static final int BUTTON_LIGHT = 0xFFAAAAAA;
    public static final int BUTTON_DARK = 0xFF3C3C3C;

    public static final int MARGIN = 10;
    public static final int GAP = 8;
    public static final int PADDING = 6;
    public static final int HEADER = 26;
    public static final int ROW = 22;
    public static final int DOT = 4;

    private Theme() {
    }

    public static void tooltip(Canvas canvas, Rect area) {
        int x0 = area.x, y0 = area.y, x1 = area.right(), y1 = area.bottom();
        canvas.fill(x0 + 1, y0, x1 - 1, y1, TOOLTIP_BACKGROUND);
        canvas.fill(x0, y0 + 1, x0 + 1, y1 - 1, TOOLTIP_BACKGROUND);
        canvas.fill(x1 - 1, y0 + 1, x1, y1 - 1, TOOLTIP_BACKGROUND);
        canvas.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, TOOLTIP_BORDER_TOP);
        canvas.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, TOOLTIP_BORDER_BOTTOM);
        canvas.fill(x0 + 1, y0 + 2, x0 + 2, y1 - 2, TOOLTIP_BORDER_TOP);
        canvas.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 2, TOOLTIP_BORDER_TOP);
    }

    public static void button(Canvas canvas, Rect area, String label, boolean hovered, boolean enabled) {
        canvas.fill(area.x, area.y, area.right(), area.bottom(), 0xFF000000);
        Rect face = area.inset(1);
        face.fill(canvas, hovered && enabled ? BUTTON_FACE_HOVER : BUTTON_FACE);
        canvas.fill(face.x, face.y, face.right(), face.y + 1, BUTTON_LIGHT);
        canvas.fill(face.x, face.y, face.x + 1, face.bottom(), BUTTON_LIGHT);
        canvas.fill(face.x, face.bottom() - 1, face.right(), face.bottom(), BUTTON_DARK);
        canvas.fill(face.right() - 1, face.y, face.right(), face.bottom(), BUTTON_DARK);
        int textX = area.x + (area.width - canvas.width(label)) / 2;
        int textY = area.y + (area.height - canvas.lineHeight() + 2) / 2;
        canvas.text(label, textX, textY, enabled ? (hovered ? 0xFFFFFFA0 : WHITE) : 0xFFA0A0A0, Canvas.SHADOW);
    }

    public static int blend(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int pulse(int bright, int dark, long now) {
        return (now / 500) % 2 == 0 ? bright : dark;
    }
}
