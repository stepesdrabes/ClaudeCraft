package dev.claudecraft.core.view;

import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Key;
import dev.claudecraft.core.ui.KeyPress;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.util.List;

final class Popup implements Overlay {
    static final class Item {
        final String label;
        final String detail;
        final boolean current;
        final boolean enabled;
        final int color;
        final Runnable action;

        Item(String label, String detail, boolean current, Runnable action) {
            this(label, detail, current, true, Theme.WHITE, action);
        }

        Item(String label, String detail, boolean current, boolean enabled, int color, Runnable action) {
            this.label = label;
            this.detail = detail;
            this.current = current;
            this.enabled = enabled;
            this.color = color;
            this.action = action;
        }

        boolean compact() {
            return detail == null || detail.isEmpty();
        }
    }

    private static final int ROW = 22;
    private static final int COMPACT_ROW = 13;
    private static final int PADDING = 5;

    private final List<Item> items;
    private final int anchorX;
    private final int anchorY;
    private final int width;
    private final boolean alignRight;
    private String title;
    private int selected = -1;
    private Rect bounds = new Rect(0, 0, 0, 0);

    Popup(List<Item> items, int anchorX, int anchorY, int width, boolean alignRight) {
        this.items = items;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.width = width;
        this.alignRight = alignRight;
        for (int i = 0; i < items.size(); i++) if (items.get(i).current) selected = i;
        if (selected < 0) selected = next(-1, 1);
    }

    Popup titled(String title) {
        this.title = title;
        return this;
    }

    Popup select(int index) {
        selected = index;
        return this;
    }

    private int rowHeight(Item item) {
        return item.compact() ? COMPACT_ROW : ROW;
    }

    private int titleHeight() {
        return title == null ? 0 : COMPACT_ROW + 2;
    }

    @Override
    public void render(Canvas canvas, int screenWidth, int screenHeight, double mouseX, double mouseY, long now) {
        int height = 2 * PADDING + titleHeight();
        for (Item item : items) height += rowHeight(item);
        int x = Math.max(4, Math.min(alignRight ? anchorX - width : anchorX, screenWidth - width - 4));
        int y = Math.max(4, Math.min(anchorY, screenHeight - height - 4));
        bounds = new Rect(x, y, width, height);
        Theme.tooltip(canvas, bounds);
        int rowY = y + PADDING;
        if (title != null) {
            canvas.text(TextWrap.ellipsize(canvas, title, width - 14, Canvas.BOLD), x + 7, rowY + 2, Theme.WHITE, Canvas.BOLD | Canvas.SHADOW);
            rowY += titleHeight();
        }
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            Rect row = new Rect(x + 3, rowY, width - 6, rowHeight(item));
            rowY += row.height;
            if (row.contains(mouseX, mouseY) && item.enabled) selected = i;
            if (i == selected) row.fill(canvas, Theme.SELECTED);
            int textWidth = row.width - 16;
            int labelColor = !item.enabled ? Theme.DIM : i == selected ? Theme.SUGGESTION_SELECTED : item.color;
            canvas.text(TextWrap.ellipsize(canvas, item.label, textWidth, 0), row.x + 4, row.y + 3, labelColor, Canvas.SHADOW);
            if (!item.compact()) {
                int detailColor = item.enabled ? Theme.MUTED : Theme.NEEDS_YOU;
                canvas.text(TextWrap.ellipsize(canvas, item.detail, textWidth, 0), row.x + 4, row.y + 12, detailColor);
            }
            if (item.current) canvas.text("✔", row.right() - 10, row.y + 3, Theme.DONE);
        }
    }

    @Override
    public boolean click(double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY)) return false;
        int rowY = bounds.y + PADDING + titleHeight();
        for (Item item : items) {
            int height = rowHeight(item);
            if (mouseY >= rowY && mouseY < rowY + height) {
                if (item.enabled) item.action.run();
                break;
            }
            rowY += height;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyPress press) {
        if (press.is(Key.UP)) selected = next(selected, -1);
        else if (press.is(Key.DOWN)) selected = next(selected, 1);
        else if (press.is(Key.ENTER) && selected >= 0) items.get(selected).action.run();
        else return false;
        return true;
    }

    private int next(int from, int direction) {
        for (int step = 1; step <= items.size(); step++) {
            int index = Math.floorMod(from + direction * step, items.size());
            if (items.get(index).enabled) return index;
        }
        return -1;
    }
}
