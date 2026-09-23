package dev.claudecraft.core.view;

import dev.claudecraft.agent.ConnectorInfo;
import dev.claudecraft.agent.Installation;
import dev.claudecraft.core.ClaudeCraft;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Key;
import dev.claudecraft.core.ui.KeyPress;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ModelPicker implements Overlay {
    private static final int WIDTH = 270;
    private static final int ROW = 22;
    private static final int LINE = 13;
    private static final int PADDING = 5;
    private static final String AUTO = "auto";

    private final ClaudeCraft app;
    private final Chat chat;
    private final int anchorRight;
    private final int anchorY;
    private final Runnable close;
    private final Clicks clicks = new Clicks();
    private int highlighted = -1;
    private boolean installations;
    private Rect bounds = new Rect(0, 0, 0, 0);

    ModelPicker(ClaudeCraft app, Chat chat, int anchorRight, int anchorY, Runnable close) {
        this.app = app;
        this.chat = chat;
        this.anchorRight = anchorRight;
        this.anchorY = anchorY;
        this.close = close;
    }

    static String modelId(ClaudeCraft app, Chat chat) {
        if (chat.model() != null) return chat.model();
        return app.config().model() != null ? app.config().model() : "default";
    }

    static String effort(ClaudeCraft app, Chat chat) {
        return chat.effort() != null ? chat.effort() : app.config().effort();
    }

    private List<ConnectorInfo.Model> models() {
        return app.info() != null ? app.info().models() : Collections.<ConnectorInfo.Model>emptyList();
    }

    @Override
    public void render(Canvas canvas, int screenWidth, int screenHeight, double mouseX, double mouseY, long now) {
        clicks.clear();
        List<ConnectorInfo.Model> models = models();
        if (highlighted < 0) highlighted = Math.max(0, indexOf(models, modelId(app, chat)));
        int height = installations ? installationsHeight() : modelsHeight(models);
        int x = Math.max(4, Math.min(anchorRight - WIDTH, screenWidth - WIDTH - 4));
        int y = Math.max(4, Math.min(anchorY, screenHeight - height - 4));
        bounds = new Rect(x, y, WIDTH, height);
        Theme.tooltip(canvas, bounds);
        Rect inner = new Rect(x + 3, y + PADDING, WIDTH - 6, height - 2 * PADDING);
        if (installations) renderInstallations(canvas, inner, mouseX, mouseY);
        else renderModels(canvas, inner, models, mouseX, mouseY);
    }

    private int modelsHeight(List<ConnectorInfo.Model> models) {
        if (models.isEmpty()) return 2 * PADDING + LINE;
        int height = 2 * PADDING + models.size() * ROW + 4 + LINE;
        if (!efforts(models).isEmpty()) height += 4 + ROW;
        if (anyUnavailable(models) || app.updating() || app.updateResult() != null) height += LINE;
        return height;
    }

    private void renderModels(Canvas canvas, Rect area, List<ConnectorInfo.Model> models, double mouseX, double mouseY) {
        if (models.isEmpty()) {
            canvas.text(app.connectorError() != null ? TextWrap.ellipsize(canvas, app.connectorError(), area.width - 8, 0) : "Loading models…",
                area.x + 4, area.y + 2, app.connectorError() != null ? Theme.ERROR : Theme.MUTED);
            return;
        }
        String current = modelId(app, chat);
        int y = area.y;
        for (int i = 0; i < models.size(); i++, y += ROW) {
            ConnectorInfo.Model model = models.get(i);
            Rect row = new Rect(area.x, y, area.width, ROW);
            if (row.contains(mouseX, mouseY) && model.available()) highlighted = i;
            if (i == highlighted) row.fill(canvas, Theme.SELECTED);
            int textWidth = row.width - 16;
            int labelColor = !model.available() ? Theme.DIM : i == highlighted ? Theme.SUGGESTION_SELECTED : Theme.WHITE;
            canvas.text(TextWrap.ellipsize(canvas, Panel.cleanLabel(model.label()), textWidth, 0), row.x + 4, row.y + 3, labelColor, Canvas.SHADOW);
            canvas.text(TextWrap.ellipsize(canvas, model.description(), textWidth, 0), row.x + 4, row.y + 12, model.available() ? Theme.MUTED : Theme.NEEDS_YOU);
            if (model.id().equals(current)) canvas.text("✔", row.right() - 10, row.y + 3, Theme.DONE);
            if (model.available()) clicks.add(row, () -> choose(model));
        }
        List<String> levels = efforts(models);
        if (!levels.isEmpty()) {
            canvas.fill(area.x + 2, y + 1, area.right() - 2, y + 2, Theme.SEPARATOR);
            renderEffort(canvas, new Rect(area.x, y + 4, area.width, ROW), levels, mouseX, mouseY);
            y += 4 + ROW;
        }
        canvas.fill(area.x + 2, y + 1, area.right() - 2, y + 2, Theme.SEPARATOR);
        y += 4;
        renderFooter(canvas, new Rect(area.x, y, area.width, LINE), mouseX, mouseY);
        if (anyUnavailable(models) || app.updating() || app.updateResult() != null) {
            renderUpdate(canvas, new Rect(area.x, y + LINE, area.width, LINE), mouseX, mouseY);
        }
    }

    private void renderEffort(Canvas canvas, Rect row, List<String> levels, double mouseX, double mouseY) {
        canvas.text("Effort", row.x + 4, row.y + 7, Theme.MUTED);
        String current = effort(app, chat);
        int x = row.x + 8 + canvas.width("Effort");
        List<String> options = new ArrayList<>();
        options.add(AUTO);
        options.addAll(levels);
        int available = row.right() - 4 - x;
        int gap = 3;
        int chipWidth = (available - gap * (options.size() - 1)) / options.size();
        for (String level : options) {
            Rect chip = new Rect(x, row.y + 3, chipWidth, 15);
            boolean selected = AUTO.equals(level) ? current == null : level.equals(current);
            chip.fill(canvas, selected ? 0x60E08A6A : chip.contains(mouseX, mouseY) ? Theme.HOVER : 0x18FFFFFF);
            if (selected) canvas.outline(chip.x, chip.y, chip.right(), chip.bottom(), Theme.CLAUDE);
            String label = TextWrap.ellipsize(canvas, level, chipWidth - 2, 0);
            canvas.text(label, chip.x + (chip.width - canvas.width(label)) / 2, chip.y + 4, selected ? Theme.WHITE : Theme.TEXT_SOFT);
            clicks.add(chip, () -> setEffort(AUTO.equals(level) ? null : level));
            x += chipWidth + gap;
        }
    }

    private void renderFooter(Canvas canvas, Rect row, double mouseX, double mouseY) {
        ConnectorInfo info = app.info();
        String version = info != null && info.version() != null ? "Claude Code " + info.version() : "Claude Code";
        String change = "Change binary ›";
        canvas.text(version, row.x + 4, row.y + 2, Theme.DIM);
        Rect link = new Rect(row.right() - canvas.width(change) - 6, row.y, canvas.width(change) + 4, row.height);
        canvas.text(change, link.x + 2, row.y + 2, link.contains(mouseX, mouseY) ? Theme.WHITE : Theme.LINK);
        clicks.add(link, () -> installations = true);
    }

    private void renderUpdate(Canvas canvas, Rect row, double mouseX, double mouseY) {
        if (app.updating()) {
            canvas.text("Updating Claude Code…", row.x + 4, row.y + 2, Theme.WORKING);
            return;
        }
        String label = app.updateResult() != null ? app.updateResult() : "Update Claude Code to use every model ›";
        int color = app.updateResult() != null ? Theme.DIM : row.contains(mouseX, mouseY) ? Theme.WHITE : Theme.NEEDS_YOU;
        canvas.text(TextWrap.ellipsize(canvas, label, row.width - 8, 0), row.x + 4, row.y + 2, color);
        if (app.updateResult() == null) clicks.add(row, app::updateClaude);
    }

    private int installationsHeight() {
        List<Installation> found = app.installations();
        int rows = found == null ? 1 : found.size() + 1;
        return 2 * PADDING + LINE + 2 + rows * ROW;
    }

    private void renderInstallations(Canvas canvas, Rect area, double mouseX, double mouseY) {
        canvas.text("Claude Code binary", area.x + 4, area.y + 2, Theme.WHITE, Canvas.BOLD | Canvas.SHADOW);
        Rect back = new Rect(area.right() - canvas.width("‹ Back") - 6, area.y, canvas.width("‹ Back") + 4, LINE);
        canvas.text("‹ Back", back.x + 2, area.y + 2, back.contains(mouseX, mouseY) ? Theme.WHITE : Theme.LINK);
        clicks.add(back, () -> installations = false);
        int y = area.y + LINE + 2;
        List<Installation> found = app.installations();
        String configured = app.config().claudePath();
        String active = app.info() != null ? app.info().executable() : null;
        installationRow(canvas, new Rect(area.x, y, area.width, ROW), "Auto-detect",
            "Use the first claude on your PATH", configured.isEmpty(), mouseX, mouseY, () -> use(""));
        y += ROW;
        if (found == null) {
            canvas.text("Searching…", area.x + 4, y + 7, Theme.MUTED);
            return;
        }
        for (Installation installation : found) {
            boolean current = !configured.isEmpty() && installation.path().equals(configured)
                || configured.isEmpty() && installation.path().equals(active);
            installationRow(canvas, new Rect(area.x, y, area.width, ROW), "Claude Code " + installation.version(), installation.path(),
                !configured.isEmpty() && current, mouseX, mouseY, () -> use(installation.path()));
            y += ROW;
        }
    }

    private void installationRow(Canvas canvas, Rect row, String label, String detail, boolean current, double mouseX, double mouseY, Runnable action) {
        if (row.contains(mouseX, mouseY)) row.fill(canvas, Theme.SELECTED);
        canvas.text(TextWrap.ellipsize(canvas, label, row.width - 16, 0), row.x + 4, row.y + 3, Theme.WHITE, Canvas.SHADOW);
        canvas.text(TextWrap.ellipsize(canvas, detail, row.width - 16, 0), row.x + 4, row.y + 12, Theme.MUTED);
        if (current) canvas.text("✔", row.right() - 10, row.y + 3, Theme.DONE);
        clicks.add(row, action);
    }

    private void use(String path) {
        app.useExecutable(path);
        installations = false;
        highlighted = -1;
    }

    private List<String> efforts(List<ConnectorInfo.Model> models) {
        if (highlighted < 0 || highlighted >= models.size()) return Collections.emptyList();
        return models.get(highlighted).effortLevels();
    }

    private static boolean anyUnavailable(List<ConnectorInfo.Model> models) {
        for (ConnectorInfo.Model model : models) if (!model.available()) return true;
        return false;
    }

    private static int indexOf(List<ConnectorInfo.Model> models, String id) {
        for (int i = 0; i < models.size(); i++) if (models.get(i).id().equals(id)) return i;
        return -1;
    }

    private void choose(ConnectorInfo.Model model) {
        chat.selectModel(model.id());
        app.config().setModel(model.id());
        close.run();
    }

    private void setEffort(String level) {
        chat.selectEffort(level);
        app.config().setEffort(level);
    }

    @Override
    public boolean click(double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY)) return false;
        clicks.click(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyPress press) {
        List<ConnectorInfo.Model> models = models();
        if (installations || models.isEmpty()) return false;
        if (press.is(Key.UP) || press.is(Key.DOWN)) {
            int direction = press.is(Key.UP) ? -1 : 1;
            for (int step = 1; step <= models.size(); step++) {
                int index = Math.floorMod(highlighted + direction * step, models.size());
                if (models.get(index).available()) {
                    highlighted = index;
                    break;
                }
            }
        } else if (press.is(Key.LEFT) || press.is(Key.RIGHT)) {
            List<String> levels = new ArrayList<>();
            levels.add(null);
            levels.addAll(efforts(models));
            if (levels.size() == 1) return true;
            int index = levels.indexOf(effort(app, chat));
            setEffort(levels.get(Math.max(0, Math.min(levels.size() - 1, index + (press.is(Key.LEFT) ? -1 : 1)))));
        } else if (press.is(Key.ENTER)) {
            if (models.get(highlighted).available()) choose(models.get(highlighted));
        } else {
            return false;
        }
        return true;
    }
}
