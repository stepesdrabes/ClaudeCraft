package dev.claudecraft.core.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmojiTest {
    private static final Emoji EMOJI = Emoji.get();

    @BeforeAll
    static void load() {
        EMOJI.load(Runnable::run);
        assertTrue(EMOJI.ready());
    }

    @Test
    void matchesSequences() {
        assertEquals(2, EMOJI.length("😀 hi", 0));
        assertEquals(2, EMOJI.length("❤️", 0));
        assertEquals(0, EMOJI.length("❤ text", 0));
        assertEquals(3, EMOJI.length("#️⃣", 0));
        assertEquals(0, EMOJI.length("#1", 0));
        assertEquals(4, EMOJI.length("🇨🇿!", 0));
        assertEquals(11, EMOJI.length("👨‍👩‍👧‍👦", 0));
        assertEquals(4, EMOJI.length("👍🏽", 0));
        assertEquals(0, EMOJI.length("čeština", 0));
    }

    @Test
    void measuresEmojiAsSquares() {
        FakeCanvas canvas = new FakeCanvas();
        int size = canvas.lineHeight() - 1;
        assertEquals(canvas.width("a") + size + 1 + canvas.width("b"), EMOJI.width(canvas, "a😀b", 0));
        assertEquals(canvas.width("❤"), EMOJI.width(canvas, "❤", 0));
    }

    @Test
    void movesByCluster() {
        String text = "a👨‍👩‍👧‍👦b";
        assertEquals(1, Emoji.next(text, 0));
        assertEquals(12, Emoji.next(text, 1));
        assertEquals(1, Emoji.previous(text, 12));
    }
}
