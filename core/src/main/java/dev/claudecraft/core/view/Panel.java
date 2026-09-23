package dev.claudecraft.core.view;

import dev.claudecraft.agent.ConnectorInfo;
import dev.claudecraft.agent.ImageData;
import dev.claudecraft.agent.McpServerInfo;
import dev.claudecraft.agent.Task;
import dev.claudecraft.agent.Usage;
import dev.claudecraft.core.ClaudeCraft;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.chat.Chats;
import dev.claudecraft.core.chat.Status;
import dev.claudecraft.core.chat.Todos;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.ClipboardImage;
import dev.claudecraft.core.ui.Emoji;
import dev.claudecraft.core.ui.EmojiCanvas;
import dev.claudecraft.core.ui.Image;
import dev.claudecraft.core.ui.Images;
import dev.claudecraft.core.ui.Key;
import dev.claudecraft.core.ui.KeyPress;
import dev.claudecraft.core.ui.Picture;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextField;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Panel {
    private static final int SIDEBAR_MIN = 130;
    private static final int SIDEBAR_MAX = 190;
    private static final int SIDE_PANEL_SCREEN = 600;
    private static final int SIDE_MIN = 150;
    private static final int SIDE_MAX = 220;
    private static final int MAX_COLUMN = 760;
    private static final int MAX_COMPOSER_LINES = 6;
    private static final int HINT_HEIGHT = 11;
    private static final int CHIP_HEIGHT = 14;
    private static final int MIN_TITLE = 110;
    private static final int ATTACHMENT_HEIGHT = 32;
    private static final int ATTACHMENT_SIZE = 1568;
    private static final long OPEN_GRACE_MILLIS = 250;
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
    private static final List<ConnectorInfo.Command> LOCAL_COMMANDS = Arrays.asList(
        new ConnectorInfo.Command("clear", "", "Start a new chat"),
        new ConnectorInfo.Command("rename", "<title>", "Rename this chat"),
        new ConnectorInfo.Command("fork", "", "Branch this chat into a new one"),
        new ConnectorInfo.Command("background", "<prompt>", "Run it as a background session that keeps going after you quit"),
        new ConnectorInfo.Command("effort", "[auto|low|medium|high|xhigh|max]", "Set how hard Claude thinks"),
        new ConnectorInfo.Command("screenshot", "", "Attach what you see in the game"),
        new ConnectorInfo.Command("worktree", "", "Start a new chat in its own git worktree"),
        new ConnectorInfo.Command("archive", "", "Archive this chat"));

    private final ClaudeCraft app;
    private final TextField composer;
    private final Sidebar sidebar;
    private final SidePanel sidePanel;
    private final TranscriptView transcript;
    private final QuestionCard questionCard = new QuestionCard();
    private final Suggestions suggestions = new Suggestions();
    private final Clicks clicks = new Clicks();
    private final List<String> sent = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();
    private Overlay overlay;
    private Chat shown;
    private int width;
    private int height;
    private Rect sidebarArea = new Rect(0, 0, 0, 0);
    private Rect composerArea = new Rect(0, 0, 0, 0);
    private boolean selecting;
    private int recalled = -1;

    public Panel(ClaudeCraft app) {
        this.app = app;
        this.composer = new TextField(Emoji.metrics(app.platform().textMetrics()), app.platform().clipboard());
        this.sidebar = new Sidebar(app, new Sidebar.Actions() {
            @Override
            public void select(Chat chat) {
                app.chats().select(chat);
            }

            @Override
            public void startNew() {
                app.chats().startNew();
            }

            @Override
            public void newMenu(Rect anchor) {
                openNewMenu(anchor);
            }

            @Override
            public void workspaces() {
                openWorkspaces();
            }

            @Override
            public void chatMenu(Chat chat, int x, int y) {
                openChatMenu(chat, x, y);
            }
        });
        this.sidePanel = new SidePanel(app);
        this.transcript = new TranscriptView(this::fill, picture -> overlay = new ImageViewer(picture));
        show(app.chats().selected());
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void render(Canvas screen, int mouseX, int mouseY) {
        if (app.beginFrame()) return;
        Canvas canvas = new EmojiCanvas(screen);
        long now = System.currentTimeMillis();
        app.tick(now);
        Chat chat = app.chats().selected();
        if (chat != shown) show(chat);
        clicks.clear();
        int margin = width < 480 ? 4 : Theme.MARGIN;
        int gap = width < 480 ? 4 : Theme.GAP;
        int sidebarWidth = width < 420 ? width / 3 : clamp(width / 6, SIDEBAR_MIN, SIDEBAR_MAX);
        int sideWidth = width >= SIDE_PANEL_SCREEN ? clamp(width / 5, SIDE_MIN, SIDE_MAX) : 0;
        int innerHeight = height - 2 * margin;
        sidebarArea = new Rect(margin, margin, sidebarWidth, innerHeight);
        int conversationX = sidebarArea.right() + gap;
        int conversationRight = sideWidth > 0 ? width - margin - sideWidth - gap : width - margin;
        sidebar.render(canvas, sidebarArea, clicks, mouseX, mouseY, now);
        renderConversation(canvas, new Rect(conversationX, margin, conversationRight - conversationX, innerHeight), chat, sideWidth == 0, mouseX, mouseY, now);
        if (sideWidth > 0) sidePanel.render(canvas, new Rect(width - margin - sideWidth, margin, sideWidth, innerHeight), chat, clicks, mouseX, mouseY, now);
        if (overlay != null) overlay.render(canvas, width, height, mouseX, mouseY, now);
        app.hud().renderToasts(canvas, width, now);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void renderConversation(Canvas canvas, Rect area, Chat chat, boolean compact, double mouseX, double mouseY, long now) {
        area.fill(canvas, Theme.CONVERSATION);
        renderHeader(canvas, area.top(Theme.HEADER), chat, compact, mouseX, mouseY, now);
        canvas.fill(area.x, area.y + Theme.HEADER, area.right(), area.y + Theme.HEADER + 1, Theme.SEPARATOR);
        Rect body = new Rect(area.x + Theme.PADDING, area.y + Theme.HEADER + 5, area.width - 2 * Theme.PADDING, area.height - Theme.HEADER - 5 - 3);
        int columnWidth = Math.min(body.width, MAX_COLUMN);
        Rect column = new Rect(body.x + (body.width - columnWidth) / 2, body.y, columnWidth, body.height);
        Rect hint = column.bottom(HINT_HEIGHT);
        int bottom = hint.y - 2;
        Rect attachments = null;
        if (chat.inBackground()) {
            Rect bar = new Rect(column.x, bottom - 20, column.width, 20);
            renderBackgroundBar(canvas, bar, chat, mouseX, mouseY);
            composerArea = new Rect(0, 0, 0, 0);
            bottom = bar.y;
        } else {
            int composerHeight = composer.height(column.width, MAX_COMPOSER_LINES);
            composerArea = new Rect(column.x, bottom - composerHeight, column.width, composerHeight);
            bottom = composerArea.y;
            if (!chat.attachments().isEmpty()) {
                attachments = new Rect(column.x, bottom - ATTACHMENT_HEIGHT - 2, column.width, ATTACHMENT_HEIGHT);
                bottom = attachments.y;
            }
        }
        int cardHeight = chat.permission() != null ? ApprovalCard.HEIGHT : chat.question() != null ? questionCard.height(canvas, chat, column.width) : 0;
        Rect card = new Rect(column.x, bottom - cardHeight - 4, column.width, cardHeight);
        int transcriptBottom = cardHeight > 0 ? card.y - 4 : bottom - 6;
        transcript.render(canvas, new Rect(column.x, column.y, column.width, transcriptBottom - column.y), chat, isGameWorkspace(), clicks, mouseX, mouseY, now);
        if (chat.permission() != null) ApprovalCard.render(canvas, card, chat, composer.value().trim(), clicks, mouseX, mouseY);
        else if (chat.question() != null) questionCard.render(canvas, card, chat, clicks, mouseX, mouseY);
        if (attachments != null) renderAttachments(canvas, attachments, chat, mouseX, mouseY);
        if (!chat.inBackground()) {
            composer.render(canvas, composerArea, overlay == null, placeholder(chat), now);
            suggestions.update(composer.value(), commands());
            if (suggestions.visible()) suggestions.render(canvas, composerArea, clicks, mouseX, mouseY, this::acceptSuggestion);
        }
        renderHint(canvas, hint, chat, mouseX, mouseY);
    }

    private void renderHeader(Canvas canvas, Rect header, Chat chat, boolean compact, double mouseX, double mouseY, long now) {
        int chipY = header.y + (header.height - CHIP_HEIGHT) / 2;
        String effort = supportsEffort(chat) ? ModelPicker.effort(app, chat) : null;
        Rect modelChip = chip(canvas, modelLabel(chat) + (effort != null ? " · " + effort : "") + " ▾", header.right() - Theme.PADDING, chipY, Theme.TEXT, mouseX, mouseY);
        clicks.add(modelChip, () -> overlay = new ModelPicker(app, chat, modelChip.right(), modelChip.bottom() + 2, () -> overlay = null));
        Rect modeChip = chip(canvas, modeLabel(chat), modelChip.x - 4, chipY, modeColor(chat), mouseX, mouseY);
        clicks.add(modeChip, () -> cycleMode(chat));
        int left = modeChip.x;
        if (compact) left = sectionChips(canvas, chat, left, chipY, header.x + MIN_TITLE, mouseX, mouseY);
        if (!chat.inBackground() && chat.status().isActive()) {
            left = button(canvas, "Stop", left, chipY, mouseX, mouseY, chat::interrupt);
        }
        int textRight = left - 6;
        int textWidth = textRight - header.x - Theme.PADDING;
        canvas.text(TextWrap.ellipsize(canvas, chat.title(), textWidth, 0), header.x + Theme.PADDING, header.y + 4, Theme.WHITE, Canvas.SHADOW);
        String folder = (chat.inWorktree() ? "⎇ " : "") + Format.folder(chat.cwd()) + " · ";
        int metaX = header.x + Theme.PADDING;
        canvas.text(TextWrap.ellipsize(canvas, folder, textWidth, 0), metaX, header.y + 15, Theme.DIM);
        int statusX = metaX + canvas.width(folder);
        canvas.text(TextWrap.ellipsize(canvas, statusText(chat, now), textRight - statusX, 0), statusX, header.y + 15, chat.status().color());
    }

    private int sectionChips(Canvas canvas, Chat chat, int right, int y, int limit, double mouseX, double mouseY) {
        List<McpServerInfo> servers = app.mcpServers(chat);
        if (servers != null && !servers.isEmpty()) right = sectionChip(canvas, "MCP", SidePanel.Section.MCP, chat, right, y, limit, Theme.MUTED, mouseX, mouseY);
        int running = 0;
        for (Task task : chat.tasks()) if (task.status().isActive()) running++;
        if (!chat.tasks().isEmpty()) {
            right = sectionChip(canvas, running > 0 ? "Tasks " + running : "Tasks", SidePanel.Section.TASKS, chat, right, y, limit, running > 0 ? Theme.WORKING : Theme.MUTED, mouseX, mouseY);
        }
        Todos todos = chat.todos();
        if (!todos.isEmpty()) {
            right = sectionChip(canvas, "☑ " + todos.done() + "/" + todos.items().size(), SidePanel.Section.TODOS, chat, right, y, limit, Theme.TEXT_SOFT, mouseX, mouseY);
        }
        Usage.Context context = chat.context();
        if (context != null && context.max() > 0) {
            right = sectionChip(canvas, context.percent() + "% ctx", SidePanel.Section.CONTEXT, chat, right, y, limit, context.percent() >= 80 ? Theme.NEEDS_YOU : Theme.MUTED, mouseX, mouseY);
        }
        return right;
    }

    private int sectionChip(Canvas canvas, String label, SidePanel.Section section, Chat chat, int right, int y, int limit, int color, double mouseX, double mouseY) {
        if (right - 4 - canvas.width(label) - 10 < limit) return right;
        Rect chip = chip(canvas, label, right - 4, y, color, mouseX, mouseY);
        clicks.add(chip, () -> overlay = sidePanel.popup(section, chat, chip.right(), chip.bottom() + 2));
        return chip.x;
    }

    private int button(Canvas canvas, String label, int right, int y, double mouseX, double mouseY, Runnable action) {
        int buttonWidth = canvas.width(label) + 10;
        Rect button = new Rect(right - 4 - buttonWidth, y, buttonWidth, CHIP_HEIGHT);
        Theme.button(canvas, button, label, button.contains(mouseX, mouseY), true);
        clicks.add(button, action);
        return button.x;
    }

    private Rect chip(Canvas canvas, String text, int right, int y, int color, double mouseX, double mouseY) {
        int chipWidth = canvas.width(text) + 10;
        Rect chip = new Rect(right - chipWidth, y, chipWidth, CHIP_HEIGHT);
        chip.fill(canvas, chip.contains(mouseX, mouseY) ? 0x50FFFFFF : 0x28FFFFFF);
        canvas.text(text, chip.x + 5, chip.y + 3, color);
        return chip;
    }

    private void renderBackgroundBar(Canvas canvas, Rect bar, Chat chat, double mouseX, double mouseY) {
        bar.fill(canvas, 0x305EA8FF);
        String label = chat.status() == Status.NEEDS_YOU ? "Waiting for you in the background" : "Running as a background session";
        canvas.text(TextWrap.ellipsize(canvas, label, bar.width - 90, 0), bar.x + Theme.PADDING, bar.y + 6, Theme.TEXT_SOFT);
        String action = "Bring back";
        Rect button = new Rect(bar.right() - canvas.width(action) - 16, bar.y + 3, canvas.width(action) + 12, 14);
        Theme.button(canvas, button, action, button.contains(mouseX, mouseY), true);
        clicks.add(button, () -> app.chats().bringBack(chat));
    }

    private void renderAttachments(Canvas canvas, Rect area, Chat chat, double mouseX, double mouseY) {
        int x = area.x;
        int size = area.height - 4;
        for (Picture picture : new ArrayList<>(chat.attachments())) {
            Image image = picture.preview();
            int thumbWidth = image == null ? size : Math.min(size * 2, Math.max(size / 2, image.width() * size / Math.max(1, image.height())));
            Rect frame = new Rect(x + 1, area.y + 2, thumbWidth, size);
            TranscriptView.thumbnail(canvas, picture, frame, frame.contains(mouseX, mouseY));
            clicks.add(frame, () -> overlay = new ImageViewer(picture));
            Rect remove = new Rect(frame.right() - 8, frame.y, 8, 9);
            remove.fill(canvas, 0xC0000000);
            canvas.text("×", remove.x + 1, remove.y, remove.contains(mouseX, mouseY) ? Theme.ERROR : Theme.WHITE);
            clicks.add(remove, () -> chat.attachments().remove(picture));
            x = frame.right() + 5;
        }
    }

    private String statusText(Chat chat, long now) {
        if (chat.sessionId() == null && chat.forkOf() != null) return "Fork";
        if (chat.isNew()) return "New chat";
        if (chat.inBackground()) return "Background · " + chat.status().label();
        if (chat.status() != Status.WORKING) return chat.status().label();
        String step = chat.step() != null ? " · " + chat.step() : "";
        return "Working " + Format.elapsed(now - chat.turnStartedAt()) + step;
    }

    private void renderHint(Canvas canvas, Rect hint, Chat chat, double mouseX, double mouseY) {
        String error = app.connectorError();
        if (error != null) {
            canvas.text(TextWrap.ellipsize(canvas, error, hint.width, 0), hint.x, hint.y + 2, Theme.ERROR);
            return;
        }
        int right = hint.right();
        if (!chat.inBackground()) {
            String shot = "📷 Screenshot";
            Rect button = new Rect(right - canvas.width(shot) - 2, hint.y, canvas.width(shot) + 2, hint.height);
            canvas.text(shot, button.x + 1, hint.y + 2, button.contains(mouseX, mouseY) ? Theme.WHITE : Theme.MUTED);
            clicks.add(button, () -> attachScreenshot(chat));
            right = button.x - 6;
        }
        String keys = fit(canvas, hints(chat), right - hint.x);
        canvas.text(TextWrap.ellipsize(canvas, keys, right - hint.x, 0), hint.x, hint.y + 2, Theme.DIM);
    }

    private static List<String> hints(Chat chat) {
        if (chat.permission() != null && ApprovalCard.isPlan(chat.permission())) {
            return Arrays.asList("Y approve", "A auto-accept edits", "N keep planning", "or type feedback");
        }
        if (chat.permission() != null) {
            return chat.permission().canRemember()
                ? Arrays.asList("Y allow", "A always", "N deny", "or type what to do instead")
                : Arrays.asList("Y allow", "N deny", "or type what to do instead");
        }
        if (chat.question() != null) return Arrays.asList("1-9 pick", "Enter confirm", "Esc play");
        if (chat.inBackground()) return Arrays.asList("Bring it back to reply in game", "Esc play");
        if (chat.runningElsewhere()) return Arrays.asList("Enter send", "Also open in another session", "Esc play");
        return Arrays.asList("Enter send", "Shift+Enter stay", "Shift+Tab mode", (MAC ? "⌘" : "Ctrl+") + "Enter background", "Esc play");
    }

    private static String fit(Canvas canvas, List<String> hints, int width) {
        for (int count = hints.size(); count > 1; count--) {
            String text = String.join(" · ", hints.subList(0, count));
            if (canvas.width(text) <= width) return text;
        }
        return hints.get(0);
    }

    public boolean keyPressed(KeyPress press) {
        Chat chat = app.chats().selected();
        if (overlay != null) {
            if (press.is(Key.ESCAPE)) overlay = null;
            else overlay.keyPressed(press);
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
            else if (press.shortcut()) submitBackground(chat, composer.value().trim());
            else submit(chat, press.shift());
            return true;
        }
        if (press.is(Key.TAB) && press.shift()) {
            cycleMode(chat);
            return true;
        }
        if (press.is(Key.B) && press.control()) {
            chat.backgroundTasks();
            return true;
        }
        if (press.is(Key.C) && press.control() && !press.shortcut() && chat.status().isActive()) {
            chat.interrupt();
            return true;
        }
        if (press.is(Key.V) && press.shortcut() && clipboardEmpty()) {
            pasteImage(chat);
            return true;
        }
        if (chat.inBackground()) return false;
        if (composer.keyPressed(press)) {
            recalled = -1;
            return true;
        }
        return (press.is(Key.UP) || press.is(Key.DOWN)) && recall(press.is(Key.UP) ? 1 : -1);
    }

    private boolean clipboardEmpty() {
        String text = app.platform().clipboard().get();
        return text == null || text.isEmpty();
    }

    private boolean answerShortcut(Chat chat, KeyPress press) {
        if (chat.question() != null && press.key().digit() > 0) return questionCard.pick(chat, press.key().digit());
        if (chat.permission() == null) return false;
        boolean plan = ApprovalCard.isPlan(chat.permission());
        if (press.is(Key.Y)) {
            if (plan) chat.approvePlan(false);
            else chat.answerPermission(true, false);
        } else if (press.is(Key.A) && (plan || chat.permission().canRemember())) {
            if (plan) chat.approvePlan(true);
            else chat.answerPermission(true, true);
        } else if (press.is(Key.N)) {
            chat.answerPermission(false, false, ApprovalCard.declineMessage(plan, ""));
        } else {
            return false;
        }
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
        if (overlay != null || chat.inBackground()) return true;
        if (composer.isEmpty()) {
            if (System.currentTimeMillis() - openedAt < OPEN_GRACE_MILLIS) return true;
            if (chat.permission() != null && "yYnNaA".indexOf(c) >= 0) return true;
            if (chat.question() != null && c >= '1' && c <= '9') return true;
        }
        return composer.charTyped(c);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (overlay != null) {
            if (!overlay.click(x, y)) overlay = null;
            return true;
        }
        if (button == 1) {
            Chat chat = sidebar.chatAt(x, y);
            if (chat != null && !chat.isNew()) openChatMenu(chat, (int) x, (int) y);
            return chat != null;
        }
        if (button != 0) return false;
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
        if (overlay != null) return overlay.scroll(x, y, amount);
        return sidebar.scroll(x, y, amount) || sidePanel.scroll(x, y, amount)
            || transcript.scroll(x, y, amount, app.platform().textMetrics().lineHeight());
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
        if (chat.permission() != null) {
            if (text.isEmpty()) return;
            chat.answerPermission(false, false, text);
            composer.setValue("");
            return;
        }
        if (text.startsWith("/") && runLocal(chat, text)) {
            remember(text);
            return;
        }
        List<Picture> images = new ArrayList<>(chat.attachments());
        if (text.isEmpty() && images.isEmpty() || chat.inBackground()) return;
        suggestions.dismiss();
        chat.send(text, images);
        chat.attachments().clear();
        remember(text);
        if (!stay) close();
    }

    private boolean runLocal(Chat chat, String text) {
        String[] parts = text.substring(1).split("\\s+", 2);
        String argument = parts.length > 1 ? parts[1].trim() : "";
        Chats chats = app.chats();
        switch (parts[0]) {
            case "clear": case "new":
                chats.startNew();
                break;
            case "rename":
                if (argument.isEmpty()) return false;
                chats.rename(chat, argument);
                break;
            case "fork":
                chats.fork(chat);
                break;
            case "background": case "bg":
                if (argument.isEmpty()) return false;
                submitBackground(chat, argument);
                return true;
            case "effort":
                String level = argument.isEmpty() || "auto".equals(argument) ? null : argument;
                chat.selectEffort(level);
                app.config().setEffort(level);
                break;
            case "screenshot":
                attachScreenshot(chat);
                break;
            case "worktree":
                chats.startNew().useWorktree(true);
                break;
            case "archive":
                chats.archive(chat, true);
                break;
            default:
                return false;
        }
        composer.setValue("");
        suggestions.dismiss();
        return true;
    }

    private void submitBackground(Chat chat, String prompt) {
        if (prompt.isEmpty() || chat.inBackground()) return;
        if (chat.status().isActive()) {
            chat.onError("Wait for Claude to finish, or stop it, before moving this chat to the background.");
            return;
        }
        app.chats().sendToBackground(chat, prompt, app::startBackground);
        composer.setValue("");
        suggestions.dismiss();
        remember(prompt);
    }

    private void remember(String text) {
        sent.remove(text);
        sent.add(0, text);
        recalled = -1;
        composer.setValue("");
    }

    private void attachScreenshot(Chat chat) {
        app.captureView(image -> Images.async(() -> attach(chat, image)));
    }

    private void pasteImage(Chat chat) {
        Images.async(() -> {
            byte[] png = ClipboardImage.read();
            if (png == null) return;
            try {
                attach(chat, Images.decode(png, ATTACHMENT_SIZE));
            } catch (IOException ignored) {
            }
        });
    }

    private void attach(Chat chat, Image image) {
        try {
            Image fitted = Images.fit(image, ATTACHMENT_SIZE);
            boolean transparent = false;
            for (int pixel : fitted.argb()) if (pixel >>> 24 != 0xFF) transparent = true;
            ImageData data = transparent ? new ImageData("image/png", Images.png(fitted)) : new ImageData("image/jpeg", Images.jpeg(fitted));
            Picture picture = new Picture(data, fitted);
            app.platform().mainThread().execute(() -> chat.attachments().add(picture));
        } catch (IOException ignored) {
        }
    }

    private boolean recall(int direction) {
        int next = recalled + direction;
        if (next < -1 || next >= sent.size()) return false;
        recalled = next;
        composer.setValue(recalled < 0 ? "" : sent.get(recalled));
        return true;
    }

    private List<ConnectorInfo.Command> commands() {
        List<ConnectorInfo.Command> commands = new ArrayList<>(LOCAL_COMMANDS);
        ConnectorInfo info = app.info();
        if (info == null) return commands;
        for (ConnectorInfo.Command command : info.commands()) {
            boolean local = false;
            for (ConnectorInfo.Command own : LOCAL_COMMANDS) local |= own.name().equals(command.name());
            if (!local) commands.add(command);
        }
        return commands;
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
        overlay = null;
        transcript.reset();
    }

    private void close() {
        app.platform().closePanel();
    }

    private void openNewMenu(Rect anchor) {
        Chats chats = app.chats();
        boolean git = Files.exists(chats.workspace().resolve(".git"));
        List<Popup.Item> items = new ArrayList<>();
        items.add(new Popup.Item("New chat", null, false, () -> {
            chats.startNew();
            overlay = null;
        }));
        items.add(new Popup.Item("New chat in a worktree", git ? null : "Needs a git repository", false, git, Theme.WHITE, () -> {
            chats.startNew().useWorktree(true);
            overlay = null;
        }));
        items.add(new Popup.Item("New background session", null, false, () -> {
            chats.startNew();
            overlay = null;
            composer.setValue("/background ");
        }));
        overlay = new Popup(items, anchor.x, anchor.bottom() + 2, 170, false);
    }

    private void openChatMenu(Chat chat, int x, int y) {
        Chats chats = app.chats();
        List<Popup.Item> items = new ArrayList<>();
        items.add(new Popup.Item("Rename", null, false, () -> {
            chats.select(chat);
            show(chat);
            composer.setValue("/rename " + chat.title());
        }));
        items.add(new Popup.Item("Fork", null, false, () -> {
            chats.fork(chat);
            overlay = null;
        }));
        if (chat.inBackground()) {
            items.add(new Popup.Item("Bring back into game", null, false, () -> {
                chats.bringBack(chat);
                overlay = null;
            }));
        } else {
            items.add(new Popup.Item("Continue in background", null, false, !chat.status().isActive(), Theme.WHITE, () -> {
                chats.select(chat);
                show(chat);
                composer.setValue("/background ");
            }));
        }
        items.add(new Popup.Item(chat.archived() ? "Unarchive" : "Archive", null, false, () -> {
            chats.archive(chat, !chat.archived());
            overlay = null;
        }));
        items.add(new Popup.Item("Delete…", null, false, !chat.runningElsewhere(), Theme.ERROR, () -> confirmDelete(chat, x, y)));
        overlay = new Popup(items, x, y, 160, false).select(-1);
    }

    private void confirmDelete(Chat chat, int x, int y) {
        List<Popup.Item> items = new ArrayList<>();
        items.add(new Popup.Item("Delete", "Moves it to the Trash", false, true, Theme.ERROR, () -> {
            app.chats().delete(chat);
            overlay = null;
        }));
        items.add(new Popup.Item("Cancel", null, false, () -> overlay = null));
        overlay = new Popup(items, x, y, 200, false).titled("Delete “" + chat.title() + "”?").select(1);
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
                overlay = null;
            }));
        }
        Rect row = sidebar.workspaceRow(sidebarArea);
        overlay = new Popup(items, row.x, row.bottom() + 2, Math.max(220, sidebarArea.width), false);
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

    private String modeId(Chat chat) {
        return chat.mode() != null ? chat.mode() : app.config().permissionMode();
    }

    private String modelLabel(Chat chat) {
        ConnectorInfo info = app.info();
        ConnectorInfo.Model model = info != null ? info.model(ModelPicker.modelId(app, chat)) : null;
        return model != null ? cleanLabel(model.label()) : "Claude";
    }

    private boolean supportsEffort(Chat chat) {
        ConnectorInfo info = app.info();
        ConnectorInfo.Model model = info != null ? info.model(ModelPicker.modelId(app, chat)) : null;
        return model == null || !model.effortLevels().isEmpty();
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

    static String cleanLabel(String label) {
        return label.replace(" (recommended)", "");
    }

    private String placeholder(Chat chat) {
        if (chat.permission() != null) return "Or tell Claude what to do instead…";
        if (chat.question() != null) return "Type your own answer…";
        if (chat.forkOf() != null && chat.sessionId() == null) return "Continue the fork…";
        return chat.isNew() ? (chat.inWorktree() ? "Ask Claude (runs in a new worktree)…" : "Ask Claude anything…") : "Reply to Claude…";
    }

    private boolean isGameWorkspace() {
        return app.chats().workspace().equals(app.gameWorkspace());
    }
}
