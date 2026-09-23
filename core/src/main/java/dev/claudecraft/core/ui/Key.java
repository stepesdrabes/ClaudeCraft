package dev.claudecraft.core.ui;

public enum Key {
    ENTER, ESCAPE, BACKSPACE, DELETE, TAB,
    LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN,
    A, B, C, N, V, X, Y,
    DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
    OPEN_PANEL, OTHER;

    public int digit() {
        return ordinal() >= DIGIT_1.ordinal() && ordinal() <= DIGIT_9.ordinal() ? ordinal() - DIGIT_1.ordinal() + 1 : 0;
    }
}
