package dev.claudecraft.core.view;

import dev.claudecraft.core.ClaudeCraft;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.chat.Status;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class Hud {
    private static final long TOAST_MILLIS = 6000;
    private static final long SLIDE_MILLIS = 250;
    private static final int TOAST_WIDTH = 170;
    private static final int TOAST_HEIGHT = 40;
    private static final int MAX_TOASTS = 3;
    private static final int PILL_HEIGHT = 14;
    private static final int PILL_TITLE_WIDTH = 120;

    private static final class Toast {
        final Chat chat;
        final Status status;
        final String detail;
        final long createdAt;

        Toast(Chat chat, Status status, String detail, long createdAt) {
            this.chat = chat;
            this.status = status;
            this.detail = detail;
            this.createdAt = createdAt;
        }
    }

    private final ClaudeCraft app;
    private final List<Toast> toasts = new ArrayList<>();

    public Hud(ClaudeCraft app) {
        this.app = app;
    }

    public void toast(Chat chat, String detail) {
        toasts.removeIf(toast -> toast.chat == chat);
        toasts.add(0, new Toast(chat, chat.status(), detail != null ? detail : "", System.currentTimeMillis()));
        while (toasts.size() > MAX_TOASTS) toasts.remove(toasts.size() - 1);
    }

    public void render(Canvas canvas, int width, int height, long now) {
        renderStatus(canvas, now);
        renderToasts(canvas, width, now);
    }

    private void renderStatus(Canvas canvas, long now) {
        List<Chat> active = app.chats().active();
        if (active.isEmpty()) return;
        active.sort(Comparator.comparing((Chat chat) -> chat.status() != Status.NEEDS_YOU));
        Chat chat = active.get(0);
        boolean waiting = chat.status() == Status.NEEDS_YOU;
        String title = TextWrap.ellipsize(canvas, chat.title(), PILL_TITLE_WIDTH, 0);
        String state = waiting ? " · Needs you" : " · " + Format.elapsed(now - chat.turnStartedAt()) + (chat.step() != null ? " · " + chat.step() : "");
        String extra = (active.size() > 1 ? "  +" + (active.size() - 1) : "") + "  [" + app.platform().openKeyName() + "]";
        int x = 4 + 4 + Theme.DOT + 4;
        int pillWidth = x + canvas.width(title) + canvas.width(state) + canvas.width(extra);
        new Rect(4, 4, pillWidth, PILL_HEIGHT).fill(canvas, 0xA0101014);
        int dot = waiting ? Theme.NEEDS_YOU : Theme.pulse(Theme.WORKING, Theme.WORKING_PULSE, now);
        canvas.fill(8, 9, 8 + Theme.DOT, 9 + Theme.DOT, dot);
        canvas.text(title, x, 7, Theme.WHITE);
        canvas.text(state, x + canvas.width(title), 7, waiting ? Theme.NEEDS_YOU : Theme.MUTED);
        canvas.text(extra, x + canvas.width(title) + canvas.width(state), 7, Theme.DIM);
    }

    public void renderToasts(Canvas canvas, int width, long now) {
        int index = 0;
        for (Iterator<Toast> it = toasts.iterator(); it.hasNext(); ) {
            Toast toast = it.next();
            long age = now - toast.createdAt;
            if (age > TOAST_MILLIS) {
                it.remove();
                continue;
            }
            renderToast(canvas, toast, width - TOAST_WIDTH - 4 + slide(age), 4 + index++ * (TOAST_HEIGHT + 4));
        }
    }

    private static int slide(long age) {
        float hidden;
        if (age < SLIDE_MILLIS) hidden = 1 - age / (float) SLIDE_MILLIS;
        else if (age > TOAST_MILLIS - SLIDE_MILLIS) hidden = (age - (TOAST_MILLIS - SLIDE_MILLIS)) / (float) SLIDE_MILLIS;
        else hidden = 0;
        return Math.round(hidden * (TOAST_WIDTH + 8));
    }

    private void renderToast(Canvas canvas, Toast toast, int x, int y) {
        Rect box = new Rect(x, y, TOAST_WIDTH, TOAST_HEIGHT);
        Theme.tooltip(canvas, box);
        canvas.fill(x + 3, y + 3, x + 5, y + TOAST_HEIGHT - 3, toast.status.color());
        int textX = x + 9;
        int textWidth = TOAST_WIDTH - 14;
        String key = "[" + app.platform().openKeyName() + "]";
        canvas.text("Claude · " + toast.status.label(), textX, y + 5, toast.status.color(), Canvas.BOLD | Canvas.SHADOW);
        canvas.text(key, x + TOAST_WIDTH - 6 - canvas.width(key), y + 5, Theme.DIM);
        canvas.text(TextWrap.ellipsize(canvas, toast.chat.title(), textWidth, 0), textX, y + 16, Theme.WHITE);
        canvas.text(TextWrap.ellipsize(canvas, toast.detail, textWidth, 0), textX, y + 27, Theme.MUTED);
    }
}
