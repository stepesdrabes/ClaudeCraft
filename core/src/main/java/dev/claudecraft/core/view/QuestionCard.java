package dev.claudecraft.core.view;

import dev.claudecraft.agent.QuestionRequest;
import dev.claudecraft.core.chat.Chat;
import dev.claudecraft.core.ui.Canvas;
import dev.claudecraft.core.ui.Line;
import dev.claudecraft.core.ui.Rect;
import dev.claudecraft.core.ui.TextWrap;
import dev.claudecraft.core.ui.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class QuestionCard {
    private static final int OPTION_ROW = 12;
    private static final int MAX_QUESTION_LINES = 3;

    private QuestionRequest request;
    private int index;
    private final List<String> answers = new ArrayList<>();
    private final Set<Integer> picked = new TreeSet<>();

    int height(Canvas canvas, Chat chat, int width) {
        QuestionRequest.Question question = current(chat);
        int questionLines = Math.min(MAX_QUESTION_LINES, TextWrap.wrap(canvas, question.text(), Theme.WHITE, width - 2 * Theme.PADDING).size());
        return 8 + (1 + questionLines) * (canvas.lineHeight() + 1) + question.options().size() * OPTION_ROW + canvas.lineHeight() + 6;
    }

    void render(Canvas canvas, Rect area, Chat chat, Clicks clicks, double mouseX, double mouseY) {
        QuestionRequest.Question question = current(chat);
        area.fill(canvas, Theme.QUESTION_BACKGROUND);
        int x = area.x + Theme.PADDING;
        int width = area.width - 2 * Theme.PADDING;
        int rowHeight = canvas.lineHeight() + 1;
        int y = area.y + 5;
        String header = question.header().isEmpty() ? "Question" : question.header();
        canvas.text(header, x, y, Theme.USER, Canvas.BOLD | Canvas.SHADOW);
        if (request.questions().size() > 1) {
            canvas.text((index + 1) + "/" + request.questions().size(), x + canvas.width(header, Canvas.BOLD) + 6, y, Theme.DIM);
        }
        Rect skip = new Rect(area.right() - Theme.PADDING - canvas.width("Skip") - 4, y - 2, canvas.width("Skip") + 4, rowHeight + 2);
        if (skip.contains(mouseX, mouseY)) skip.fill(canvas, Theme.HOVER);
        canvas.text("Skip", skip.x + 2, y, Theme.MUTED);
        clicks.add(skip, chat::dismissQuestion);
        y += rowHeight;
        List<Line> lines = TextWrap.wrap(canvas, question.text(), Theme.WHITE, width);
        for (int i = 0; i < lines.size() && i < MAX_QUESTION_LINES; i++, y += rowHeight) lines.get(i).draw(canvas, x, y, width);
        y += 2;
        for (int i = 0; i < question.options().size(); i++, y += OPTION_ROW) {
            int number = i + 1;
            Rect row = new Rect(area.x + 2, y - 2, area.width - 4, OPTION_ROW);
            if (picked.contains(i)) row.fill(canvas, Theme.QUESTION_PICKED);
            else if (row.contains(mouseX, mouseY)) row.fill(canvas, Theme.HOVER);
            clicks.add(row, () -> pick(chat, number));
            renderOption(canvas, question, i, x, y, width);
        }
        String hint = question.multiSelect() ? "Toggle with 1-" + question.options().size() + ", Enter to confirm" : "Press 1-" + question.options().size() + " or type your own answer";
        canvas.text(TextWrap.ellipsize(canvas, hint, width, 0), x, y + 2, Theme.DIM);
    }

    private void renderOption(Canvas canvas, QuestionRequest.Question question, int i, int x, int y, int width) {
        QuestionRequest.Option option = question.options().get(i);
        String prefix = (i + 1) + (question.multiSelect() ? (picked.contains(i) ? " [x] " : " [ ] ") : "  ");
        canvas.text(prefix, x, y, Theme.NEEDS_YOU, Canvas.BOLD);
        int labelX = x + canvas.width(prefix, Canvas.BOLD);
        String label = TextWrap.ellipsize(canvas, option.label(), width - (labelX - x), 0);
        canvas.text(label, labelX, y, Theme.WHITE);
        int descriptionX = labelX + canvas.width(label);
        if (!option.description().isEmpty() && descriptionX < x + width - 20) {
            canvas.text(TextWrap.ellipsize(canvas, " — " + option.description(), x + width - descriptionX, 0), descriptionX, y, Theme.MUTED);
        }
    }

    boolean pick(Chat chat, int number) {
        QuestionRequest.Question question = current(chat);
        if (number < 1 || number > question.options().size()) return false;
        if (question.multiSelect()) {
            if (!picked.remove(number - 1)) picked.add(number - 1);
        } else {
            advance(chat, question.options().get(number - 1).label());
        }
        return true;
    }

    void confirm(Chat chat, String typed) {
        QuestionRequest.Question question = current(chat);
        if (!typed.trim().isEmpty()) {
            advance(chat, typed.trim());
        } else if (question.multiSelect() && !picked.isEmpty()) {
            advance(chat, picked.stream().map(i -> question.options().get(i).label()).collect(Collectors.joining(", ")));
        }
    }

    private void advance(Chat chat, String answer) {
        answers.add(answer);
        picked.clear();
        index++;
        if (index >= request.questions().size()) chat.answerQuestion(new ArrayList<>(answers));
    }

    private QuestionRequest.Question current(Chat chat) {
        if (chat.question() != request) {
            request = chat.question();
            index = 0;
            answers.clear();
            picked.clear();
        }
        return request.questions().get(Math.min(index, request.questions().size() - 1));
    }
}
