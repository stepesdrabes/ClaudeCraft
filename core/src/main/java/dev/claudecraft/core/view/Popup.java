package dev.claudecraft.core.view;

import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Key;
import dev.claudecraft.core.ui.KeyPress;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.util.List;

final class Popup {
    static final class Item {
        final String label;
        final String detail;
        final boolean current;
        final Runnable action;

        Item(String label, String detail, boolean current, Runnable action) {
            this.label = label;
            this.detail = detail;
            this.current = current;
            this.action = action;
        }
    }

    private static final int ROW = 22;
    private static final int PADDING = 5;

    private final List<Item> items;
    private final int anchorX;
    private final int anchorY;
    private final int width;
    private final boolean alignRight;
    private int selected;
    private Rect bounds = new Rect(0, 0, 0, 0);

    Popup(List<Item> items, int anchorX, int anchorY, int width, boolean alignRight) {
        this.items = items;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.width = width;
        this.alignRight = alignRight;
        for (int i = 0; i < items.size(); i++) if (items.get(i).current) selected = i;
    }

    void render(Canvas canvas, int screenHeight, double mouseX, double mouseY) {
        int height = items.size() * ROW + 2 * PADDING;
        int x = alignRight ? anchorX - width : anchorX;
        int y = Math.max(4, Math.min(anchorY, screenHeight - height - 4));
        bounds = new Rect(x, y, width, height);
        Theme.tooltip(canvas, bounds);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            Rect row = new Rect(x + 3, y + PADDING + i * ROW, width - 6, ROW);
            if (row.contains(mouseX, mouseY)) selected = i;
            if (i == selected) row.fill(canvas, Theme.SELECTED);
            int textWidth = row.width - 16;
            canvas.text(TextWrap.ellipsize(canvas, item.label, textWidth, 0), row.x + 4, row.y + 3,
                i == selected ? Theme.SUGGESTION_SELECTED : Theme.WHITE, Canvas.SHADOW);
            canvas.text(TextWrap.ellipsize(canvas, item.detail, textWidth, 0), row.x + 4, row.y + 12, Theme.MUTED);
            if (item.current) canvas.text("✔", row.right() - 10, row.y + 3, Theme.DONE);
        }
    }

    boolean click(double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY)) return false;
        int row = (int) ((mouseY - bounds.y - PADDING) / ROW);
        if (row >= 0 && row < items.size()) items.get(row).action.run();
        return true;
    }

    boolean keyPressed(KeyPress press) {
        if (press.is(Key.UP)) selected = (selected + items.size() - 1) % items.size();
        else if (press.is(Key.DOWN)) selected = (selected + 1) % items.size();
        else if (press.is(Key.ENTER)) items.get(selected).action.run();
        else return false;
        return true;
    }
}
