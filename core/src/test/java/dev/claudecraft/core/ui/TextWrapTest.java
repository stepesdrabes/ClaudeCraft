package dev.claudecraft.core.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextWrapTest {
    private final FakeCanvas canvas = new FakeCanvas();

    private List<String> wrap(String text, int columns) {
        return TextWrap.wrap(canvas, text, 0, columns * FakeCanvas.CHAR_WIDTH).stream()
            .map(Line::plainText).collect(Collectors.toList());
    }

    @Test
    void wrapsAtWordBoundaries() {
        assertEquals(List.of("the quick", "brown fox"), wrap("the quick brown fox", 10));
    }

    @Test
    void keepsHardLineBreaks() {
        assertEquals(List.of("one", "two"), wrap("one\ntwo", 20));
    }

    @Test
    void breaksWordsLongerThanTheLine() {
        assertEquals(List.of("abcde", "fghij", "k"), wrap("abcdefghijk", 5));
    }

    @Test
    void ellipsizesToFit() {
        assertEquals("hello...", TextWrap.ellipsize(canvas, "hello world", 8 * FakeCanvas.CHAR_WIDTH, 0));
    }
}
