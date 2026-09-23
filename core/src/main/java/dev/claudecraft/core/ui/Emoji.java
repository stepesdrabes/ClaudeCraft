package dev.claudecraft.core.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public final class Emoji {
    private static final String ROOT = "/assets/claudecraft/emoji/";
    private static final Emoji INSTANCE = new Emoji();
    private static final char VARIATION_SELECTOR = '️';

    private final Map<String, Integer> cells = new HashMap<>();
    private final Set<String> prefixes = new HashSet<>();
    private final BitSet firsts = new BitSet(Character.MAX_VALUE + 1);
    private int maxLength;
    private int cellSize;
    private int columns;
    private volatile Image atlas;

    private Emoji() {
    }

    public static Emoji get() {
        return INSTANCE;
    }

    public void load(Executor background) {
        background.execute(() -> {
            try (InputStream index = Emoji.class.getResourceAsStream(ROOT + "twemoji.txt");
                 InputStream png = Emoji.class.getResourceAsStream(ROOT + "twemoji.png")) {
                if (index == null || png == null) return;
                readIndex(index);
                atlas = Images.read(png);
            } catch (IOException | RuntimeException ignored) {
            }
        });
    }

    private void readIndex(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String[] header = reader.readLine().split(" ");
        cellSize = Integer.parseInt(header[0]);
        columns = Integer.parseInt(header[1]);
        int cell = 0;
        for (String line; (line = reader.readLine()) != null; cell++) {
            for (String sequence : line.split(" ")) {
                StringBuilder text = new StringBuilder();
                for (String codePoint : sequence.split("-")) text.appendCodePoint(Integer.parseInt(codePoint, 16));
                String emoji = text.toString();
                cells.put(emoji, cell);
                firsts.set(emoji.charAt(0));
                for (int end = Character.charCount(emoji.codePointAt(0)); end < emoji.length(); end += Character.charCount(emoji.codePointAt(end))) {
                    prefixes.add(emoji.substring(0, end));
                }
                maxLength = Math.max(maxLength, emoji.length());
            }
        }
    }

    public boolean ready() {
        return atlas != null;
    }

    static boolean mayContain(String text) {
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) >= 0xA9) return true;
        return false;
    }

    public int length(String text, int index) {
        char first = text.charAt(index);
        if (atlas == null || !firsts.get(first) || first < 0x80 && !keycap(text, index)) return 0;
        int best = 0;
        for (int end = index; end < text.length() && end - index < maxLength; ) {
            end += Character.charCount(text.codePointAt(end));
            String candidate = text.substring(index, end);
            if (cells.containsKey(candidate)) best = end - index;
            if (!prefixes.contains(candidate)) break;
        }
        if (best > 0 && index + best < text.length() && text.charAt(index + best) == VARIATION_SELECTOR) best++;
        return best;
    }

    private static boolean keycap(String text, int index) {
        return index + 1 < text.length() && (text.charAt(index + 1) == VARIATION_SELECTOR || text.charAt(index + 1) == '⃣');
    }

    public static int next(String text, int index) {
        int emoji = INSTANCE.length(text, index);
        if (emoji > 0) return index + emoji;
        return Math.min(text.length(), index + Character.charCount(text.codePointAt(index)));
    }

    public static int previous(String text, int index) {
        int cluster = 0;
        while (true) {
            int next = next(text, cluster);
            if (next >= index) return cluster;
            cluster = next;
        }
    }

    int size(TextMetrics metrics) {
        return metrics.lineHeight() - 1;
    }

    int width(TextMetrics metrics, String text, int style) {
        if (atlas == null || !mayContain(text)) return metrics.width(text, style);
        int width = 0;
        int run = 0;
        for (int i = 0; i < text.length(); ) {
            int length = length(text, i);
            if (length == 0) {
                i += Character.charCount(text.codePointAt(i));
                continue;
            }
            width += metrics.width(plain(text.substring(run, i)), style) + size(metrics) + 1;
            i += length;
            run = i;
        }
        return width + metrics.width(plain(text.substring(run)), style);
    }

    void draw(Canvas canvas, String text, int x, int y, int argb, int style) {
        if (atlas == null || !mayContain(text)) {
            canvas.text(text, x, y, argb, style);
            return;
        }
        int cursor = x;
        int run = 0;
        for (int i = 0; i < text.length(); ) {
            int length = length(text, i);
            if (length == 0) {
                i += Character.charCount(text.codePointAt(i));
                continue;
            }
            cursor += drawPlain(canvas, text.substring(run, i), cursor, y, argb, style);
            drawEmoji(canvas, text.substring(i, i + length), cursor, y);
            cursor += size(canvas) + 1;
            i += length;
            run = i;
        }
        drawPlain(canvas, text.substring(run), cursor, y, argb, style);
    }

    private int drawPlain(Canvas canvas, String text, int x, int y, int argb, int style) {
        String plain = plain(text);
        if (plain.isEmpty()) return 0;
        canvas.text(plain, x, y, argb, style);
        return canvas.width(plain, style);
    }

    private void drawEmoji(Canvas canvas, String sequence, int x, int y) {
        Integer cell = cells.get(sequence);
        if (cell == null) cell = cells.get(sequence.substring(0, sequence.length() - 1));
        int pitch = cellSize + 2;
        int size = size(canvas);
        canvas.image(atlas, x, y, size, size, cell % columns * pitch + 1, cell / columns * pitch + 1, cellSize, cellSize);
    }

    private static String plain(String text) {
        StringBuilder out = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean invisible = c == VARIATION_SELECTOR || c == '︎' || c == '‍';
            if (invisible && out == null) out = new StringBuilder(text.substring(0, i));
            else if (!invisible && out != null) out.append(c);
        }
        return out == null ? text : out.toString();
    }

    public static TextMetrics metrics(TextMetrics metrics) {
        return new TextMetrics() {
            @Override
            public int width(String text, int style) {
                return INSTANCE.width(metrics, text, style);
            }

            @Override
            public int lineHeight() {
                return metrics.lineHeight();
            }
        };
    }
}
