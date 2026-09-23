package dev.claudecraft.core.view;

import dev.claudecraft.agent.ConnectorInfo;
import dev.claudecraft.core.ClaudeCraft;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.chat.Status;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Key;
import dev.claudecraft.core.ui.KeyPress;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextField;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Panel {
    private static final int SIDEBAR_WIDTH = 150;
    private static final int MAX_CONVERSATION_WIDTH = 480;
    private static final int MAX_COMPOSER_LINES = 6;
    private static final int HINT_HEIGHT = 11;
    private static final int CHIP_HEIGHT = 14;
    private static final long OPEN_GRACE_MILLIS = 250;

    private final ClaudeCraft app;
    private final TextField composer;
    private final Sidebar sidebar;
    private final TranscriptView transcript;
    private final QuestionCard questionCard = new QuestionCard();
    private final Suggestions suggestions = new Suggestions();
    private final Clicks clicks = new Clicks();
    private final List<String> sent = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();
    private Popup popup;
    private Chat shown;
    private int width;
    private int height;
    private Rect sidebarArea = new Rect(0, 0, 0, 0);
    private Rect composerArea = new Rect(0, 0, 0, 0);
    private boolean selecting;
    private int recalled = -1;

    public Panel(ClaudeCraft app) {
        this.app = app;
        this.composer = new TextField(app.platform().textMetrics(), app.platform().clipboard());
        this.sidebar = new Sidebar(app, this::select, this::startNew, this::openWorkspaces);
        this.transcript = new TranscriptView(this::fill);
        show(app.chats().selected());
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void render(Canvas canvas, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        Chat chat = app.chats().selected();
        if (chat != shown) show(chat);
        clicks.clear();
        sidebarArea = new Rect(Theme.MARGIN, Theme.MARGIN, Math.min(SIDEBAR_WIDTH, width / 3), height - 2 * Theme.MARGIN);
        int conversationX = sidebarArea.right() + Theme.GAP;
        Rect conversation = new Rect(conversationX, Theme.MARGIN, Math.min(MAX_CONVERSATION_WIDTH, width - conversationX - Theme.MARGIN), sidebarArea.height);
        sidebar.render(canvas, sidebarArea, clicks, mouseX, mouseY, now);
        renderConversation(canvas, conversation, chat, mouseX, mouseY, now);
        if (popup != null) popup.render(canvas, height, mouseX, mouseY);
        app.hud().renderToasts(canvas, width, now);
    }

    private void renderConversation(Canvas canvas, Rect area, Chat chat, double mouseX, double mouseY, long now) {
        area.fill(canvas, Theme.CONVERSATION);
        renderHeader(canvas, area.top(Theme.HEADER), chat, mouseX, mouseY, now);
        canvas.fill(area.x, area.y + Theme.HEADER, area.right(), area.y + Theme.HEADER + 1, Theme.SEPARATOR);
        Rect body = new Rect(area.x + Theme.PADDING, area.y + Theme.HEADER + 5, area.width - 2 * Theme.PADDING, area.height - Theme.HEADER - 5 - 3);
        Rect hint = body.bottom(HINT_HEIGHT);
        int composerHeight = composer.height(body.width, MAX_COMPOSER_LINES);
        composerArea = new Rect(body.x, hint.y - composerHeight - 2, body.width, composerHeight);
        int cardHeight = chat.permission() != null ? ApprovalCard.HEIGHT : chat.question() != null ? questionCard.height(canvas, chat, body.width) : 0;
        Rect card = new Rect(body.x, composerArea.y - cardHeight - 4, body.width, cardHeight);
        int transcriptBottom = cardHeight > 0 ? card.y - 4 : composerArea.y - 6;
        transcript.render(canvas, new Rect(body.x, body.y, body.width, transcriptBottom - body.y), chat, isGameWorkspace(), clicks, mouseX, mouseY, now);
        if (chat.permission() != null) ApprovalCard.render(canvas, card, chat, clicks, mouseX, mouseY);
        else if (chat.question() != null) questionCard.render(canvas, card, chat, clicks, mouseX, mouseY);
        composer.render(canvas, composerArea, popup == null, placeholder(chat), now);
        suggestions.update(composer.value(), app.info());
        if (suggestions.visible()) suggestions.render(canvas, composerArea, clicks, mouseX, mouseY, this::acceptSuggestion);
        renderHint(canvas, hint, chat);
    }

    private void renderHeader(Canvas canvas, Rect header, Chat chat, double mouseX, double mouseY, long now) {
        int right = header.right() - Theme.PADDING;
        int chipY = header.y + (header.height - CHIP_HEIGHT) / 2;
        String modelText = modelLabel(chat) + " ▾";
        Rect modelChip = chip(canvas, modelText, right, chipY, Theme.TEXT, mouseX, mouseY);
        clicks.add(modelChip, () -> openModels(chat, modelChip));
        Rect modeChip = chip(canvas, modeLabel(chat), modelChip.x - 4, chipY, modeColor(chat), mouseX, mouseY);
        clicks.add(modeChip, () -> cycleMode(chat));
        int textRight = modeChip.x - 6;
        if (chat.status().isActive()) {
            Rect stop = new Rect(modeChip.x - 40, chipY, 36, CHIP_HEIGHT);
            Theme.button(canvas, stop, "Stop", stop.contains(mouseX, mouseY), true);
            clicks.add(stop, chat::interrupt);
            textRight = stop.x - 6;
        }
        int textWidth = textRight - header.x - Theme.PADDING;
        canvas.text(TextWrap.ellipsize(canvas, chat.title(), textWidth, 0), header.x + Theme.PADDING, header.y + 4, Theme.WHITE, Canvas.SHADOW);
        String folder = Format.folder(chat.workspace()) + " · ";
        int metaX = header.x + Theme.PADDING;
        canvas.text(TextWrap.ellipsize(canvas, folder, textWidth, 0), metaX, header.y + 15, Theme.DIM);
        int statusX = metaX + canvas.width(folder);
        canvas.text(TextWrap.ellipsize(canvas, statusText(chat, now), textRight - statusX, 0), statusX, header.y + 15, chat.status().color());
    }

    private Rect chip(Canvas canvas, String text, int right, int y, int color, double mouseX, double mouseY) {
        int chipWidth = canvas.width(text) + 10;
        Rect chip = new Rect(right - chipWidth, y, chipWidth, CHIP_HEIGHT);
        chip.fill(canvas, chip.contains(mouseX, mouseY) ? 0x50FFFFFF : 0x28FFFFFF);
        canvas.text(text, chip.x + 5, chip.y + 3, color);
        return chip;
    }

    private String statusText(Chat chat, long now) {
        if (chat.isNew()) return "New chat";
        if (chat.status() != Status.WORKING) return chat.status().label();
        String step = chat.step() != null ? " · " + chat.step() : "";
        return "Working " + Format.elapsed(now - chat.turnStartedAt()) + step;
    }

    private void renderHint(Canvas canvas, Rect hint, Chat chat) {
        String error = app.connectorError();
        if (error != null) {
            canvas.text(TextWrap.ellipsize(canvas, error, hint.width, 0), hint.x, hint.y + 2, Theme.ERROR);
            return;
        }
        String keys;
        if (chat.permission() != null) keys = "Y allow · " + (chat.permission().canRemember() ? "A always · " : "") + "N deny · Esc play";
        else if (chat.question() != null) keys = "1-9 pick · Enter confirm · Esc play";
        else keys = "Enter send · Shift+Enter stay · Shift+Tab mode · Esc play";
        canvas.text(TextWrap.ellipsize(canvas, keys, hint.width, 0), hint.x, hint.y + 2, Theme.DIM);
    }

    public boolean keyPressed(KeyPress press) {
        Chat chat = app.chats().selected();
        if (popup != null) {
            if (press.is(Key.ESCAPE)) popup = null;
            else popup.keyPressed(press);
            return true;
        }
        if (press.is(Key.ESCAPE)) {
            if (suggestions.visible()) suggestions.dismiss();
            else close();
            return true;
        }
        if (press.is(Key.OPEN_PANEL) && composer.isEmpty() && System.currentTimeMillis() - openedAt > OPEN_GRACE_MILLIS) {
            close();
            return true;
        }
        if (composer.isEmpty() && !press.shortcut() && answerShortcut(chat, press)) return true;
        if (suggestions.visible() && suggestionKey(press)) return true;
        if (press.is(Key.ENTER)) {
            if (press.alt()) composer.insert("\n");
            else submit(chat, press.shift());
            return true;
        }
        if (press.is(Key.TAB) && press.shift()) {
            cycleMode(chat);
            return true;
        }
        if (press.is(Key.C) && press.control() && !press.shortcut() && chat.status().isActive()) {
            chat.interrupt();
            return true;
        }
        if (composer.keyPressed(press)) {
            recalled = -1;
            return true;
        }
        return (press.is(Key.UP) || press.is(Key.DOWN)) && recall(press.is(Key.UP) ? 1 : -1);
    }

    private boolean answerShortcut(Chat chat, KeyPress press) {
        if (chat.question() != null && press.key().digit() > 0) return questionCard.pick(chat, press.key().digit());
        if (chat.permission() == null) return false;
        if (press.is(Key.Y)) chat.answerPermission(true, false);
        else if (press.is(Key.A) && chat.permission().canRemember()) chat.answerPermission(true, true);
        else if (press.is(Key.N)) chat.answerPermission(false, false);
        else return false;
        return true;
    }

    private boolean suggestionKey(KeyPress press) {
        if (press.is(Key.UP) || press.is(Key.DOWN)) suggestions.move(press.is(Key.UP) ? -1 : 1);
        else if (press.is(Key.TAB) && !press.shift()) acceptSuggestion();
        else return false;
        return true;
    }

    public boolean charTyped(char c) {
        Chat chat = app.chats().selected();
        if (popup != null) return true;
        if (composer.isEmpty()) {
            if (System.currentTimeMillis() - openedAt < OPEN_GRACE_MILLIS) return true;
            if (chat.permission() != null && "yYnNaA".indexOf(c) >= 0) return true;
            if (chat.question() != null && c >= '1' && c <= '9') return true;
        }
        return composer.charTyped(c);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        if (popup != null) {
            if (!popup.click(x, y)) popup = null;
            return true;
        }
        if (composerArea.contains(x, y)) {
            composer.click(x, y, false);
            selecting = true;
            return true;
        }
        return clicks.click(x, y);
    }

    public boolean mouseDragged(double x, double y) {
        if (!selecting) return false;
        composer.drag(x, y);
        return true;
    }

    public boolean mouseReleased() {
        selecting = false;
        return false;
    }

    public boolean mouseScrolled(double x, double y, double amount) {
        return sidebar.scroll(x, y, amount) || transcript.scroll(x, y, amount, app.platform().textMetrics().lineHeight());
    }

    public void removed() {
        if (shown != null) shown.saveDraft(composer.value());
        app.panelClosed();
    }

    private void submit(Chat chat, boolean stay) {
        String text = composer.value().trim();
        if (chat.question() != null) {
            questionCard.confirm(chat, text);
            composer.setValue("");
            return;
        }
        if (text.isEmpty()) return;
        suggestions.dismiss();
        chat.send(text);
        sent.remove(text);
        sent.add(0, text);
        recalled = -1;
        composer.setValue("");
        if (!stay) close();
    }

    private boolean recall(int direction) {
        int next = recalled + direction;
        if (next < -1 || next >= sent.size()) return false;
        recalled = next;
        composer.setValue(recalled < 0 ? "" : sent.get(recalled));
        return true;
    }

    private void acceptSuggestion() {
        composer.setValue(suggestions.completion());
        suggestions.dismiss();
    }

    private void fill(String prompt) {
        composer.setValue(prompt);
    }

    private void show(Chat chat) {
        if (shown != null) shown.saveDraft(composer.value());
        shown = chat;
        composer.setValue(chat.draft());
        popup = null;
    }

    private void select(Chat chat) {
        app.chats().select(chat);
    }

    private void startNew() {
        app.chats().startNew();
    }

    private void close() {
        app.platform().closePanel();
    }

    private void openWorkspaces() {
        Path current = app.chats().workspace();
        Set<Path> paths = new LinkedHashSet<>();
        paths.add(app.gameWorkspace());
        paths.add(current);
        paths.addAll(app.recentWorkspaces());
        List<Popup.Item> items = new ArrayList<>();
        for (Path path : paths) {
            String label = path.equals(app.gameWorkspace()) ? "Minecraft (this instance)" : Format.folder(path);
            items.add(new Popup.Item(label, path.toString(), path.equals(current), () -> {
                app.switchWorkspace(path);
                popup = null;
            }));
        }
        Rect row = sidebar.workspaceRow(sidebarArea);
        popup = new Popup(items, row.x, row.bottom() + 2, Math.max(220, sidebarArea.width), false);
    }

    private void openModels(Chat chat, Rect chip) {
        ConnectorInfo info = app.info();
        if (info == null) return;
        String current = modelId(chat);
        List<Popup.Item> items = new ArrayList<>();
        for (ConnectorInfo.Model model : info.models()) {
            items.add(new Popup.Item(cleanLabel(model.label()), model.description(), model.id().equals(current), () -> {
                chat.selectModel(model.id());
                app.config().setModel(model.id());
                popup = null;
            }));
        }
        popup = new Popup(items, chip.right(), chip.bottom() + 2, 250, true);
    }

    private void cycleMode(Chat chat) {
        List<ConnectorInfo.Mode> modes = app.info() != null ? app.info().modes() : new ArrayList<ConnectorInfo.Mode>();
        if (modes.isEmpty()) return;
        String current = modeId(chat);
        int index = 0;
        for (int i = 0; i < modes.size(); i++) if (modes.get(i).id().equals(current)) index = i;
        String next = modes.get((index + 1) % modes.size()).id();
        chat.selectMode(next);
        app.config().setPermissionMode(next);
    }

    private String modelId(Chat chat) {
        if (chat.model() != null) return chat.model();
        return app.config().model() != null ? app.config().model() : "default";
    }

    private String modeId(Chat chat) {
        return chat.mode() != null ? chat.mode() : app.config().permissionMode();
    }

    private String modelLabel(Chat chat) {
        ConnectorInfo info = app.info();
        ConnectorInfo.Model model = info != null ? info.model(modelId(chat)) : null;
        return model != null ? cleanLabel(model.label()) : "Claude";
    }

    private String modeLabel(Chat chat) {
        String id = modeId(chat);
        if (app.info() != null) for (ConnectorInfo.Mode mode : app.info().modes()) if (mode.id().equals(id)) return mode.label();
        return id;
    }

    private int modeColor(Chat chat) {
        switch (modeId(chat)) {
            case "acceptEdits": return 0xFFC4B5FD;
            case "plan": return 0xFF5EEAD4;
            case "auto": return 0xFFFDBA74;
            default: return Theme.MUTED;
        }
    }

    private static String cleanLabel(String label) {
        return label.replace(" (recommended)", "");
    }

    private String placeholder(Chat chat) {
        if (chat.question() != null) return "Type your own answer…";
        return chat.isNew() ? "Ask Claude anything…" : "Reply to Claude…";
    }

    private boolean isGameWorkspace() {
        return app.chats().workspace().equals(app.gameWorkspace());
    }
}
