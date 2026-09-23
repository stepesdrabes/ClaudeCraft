package dev.claudecraft.core.view;

import dev.claudecraft.agent.ConnectorInfo;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class Suggestions {
    private static final int LIMIT = 8;
    private static final int ROW = 11;

    private List<ConnectorInfo.Command> matches = Collections.emptyList();
    private String query = "";
    private int selected;

    void update(String input, List<ConnectorInfo.Command> commands) {
        String next = input.startsWith("/") && !input.contains(" ") && !input.contains("\n") ? input.substring(1).toLowerCase(Locale.ROOT) : null;
        if (next == null) {
            matches = Collections.emptyList();
            return;
        }
        if (next.equals(query) && !matches.isEmpty()) return;
        query = next;
        selected = 0;
        List<ConnectorInfo.Command> found = new ArrayList<>();
        for (ConnectorInfo.Command command : commands) {
            if (command.name().toLowerCase(Locale.ROOT).startsWith(next)) found.add(command);
        }
        for (ConnectorInfo.Command command : commands) {
            if (!found.contains(command) && command.name().toLowerCase(Locale.ROOT).contains(next)) found.add(command);
        }
        matches = found.subList(0, Math.min(LIMIT, found.size()));
    }

    boolean visible() {
        return !matches.isEmpty();
    }

    void move(int delta) {
        selected = (selected + delta + matches.size()) % matches.size();
    }

    String completion() {
        return "/" + matches.get(selected).name() + " ";
    }

    void dismiss() {
        matches = Collections.emptyList();
    }

    void render(Canvas canvas, Rect composer, Clicks clicks, double mouseX, double mouseY, Runnable onPick) {
        int width = composer.width;
        Rect box = new Rect(composer.x, composer.y - matches.size() * ROW - 5, width, matches.size() * ROW + 4);
        box.fill(canvas, 0xD0000000);
        for (int i = 0; i < matches.size(); i++) {
            ConnectorInfo.Command command = matches.get(i);
            Rect row = new Rect(box.x, box.y + 2 + i * ROW, width, ROW);
            if (row.contains(mouseX, mouseY)) selected = i;
            int index = i;
            clicks.add(row, () -> {
                selected = index;
                onPick.run();
            });
            String name = "/" + command.name() + (command.hint().isEmpty() ? "" : " " + command.hint());
            int color = i == selected ? Theme.SUGGESTION_SELECTED : Theme.SUGGESTION;
            String shown = TextWrap.ellipsize(canvas, name, width / 2, 0);
            canvas.text(shown, row.x + 3, row.y + 1, color);
            int descriptionX = row.x + 9 + canvas.width(shown);
            canvas.text(TextWrap.ellipsize(canvas, command.description(), row.right() - descriptionX - 3, 0), descriptionX, row.y + 1, Theme.DIM);
        }
    }
}
