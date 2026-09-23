package dev.claudecraft.core.view;

import dev.claudecraft.core.ui.Rect;

import java.util.ArrayList;
import java.util.List;

final class Clicks {
    private final List<Rect> areas = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();

    void clear() {
        areas.clear();
        actions.clear();
    }

    void add(Rect area, Runnable action) {
        areas.add(area);
        actions.add(action);
    }

    boolean click(double x, double y) {
        for (int i = areas.size() - 1; i >= 0; i--) {
            if (areas.get(i).contains(x, y)) {
                actions.get(i).run();
                return true;
            }
        }
        return false;
    }
}
