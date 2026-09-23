package dev.claudecraft.core.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownTest {
    private final FakeCanvas canvas = new FakeCanvas();

    private List<Line> render(String markdown) {
        return Markdown.render(canvas, markdown, 300, Theme.TEXT);
    }

    @Test
    void stylesInlineMarkup() {
        List<Span> spans = Markdown.inline("a **bold** and *it* with `code` and snake_case_name", Theme.TEXT, 0);
        assertEquals("bold", spans.get(1).text);
        assertTrue((spans.get(1).style & Canvas.BOLD) != 0);
        assertEquals("it", spans.get(3).text);
        assertTrue((spans.get(3).style & Canvas.ITALIC) != 0);
        assertEquals("code", spans.get(5).text);
        assertEquals(Theme.CODE, spans.get(5).color);
        assertEquals(" and snake_case_name", spans.get(6).text);
    }

    @Test
    void rendersBlocks() {
        List<String> text = render("# Title\n\n- one\n- two\n\n```\n  indented()\n```\n> quote\n| a | b |\n|---|---|\n| 1 | 2 |")
            .stream().map(Line::plainText).collect(Collectors.toList());
        assertEquals(List.of("Title", "", "one", "two", "", "  indented()", "quote", "a │ b", "1 │ 2"), text);
    }

    @Test
    void rendersLinksAsUnderlinedText() {
        List<Span> spans = Markdown.inline("see [docs](https://example.com) now", Theme.TEXT, 0);
        assertEquals("docs", spans.get(1).text);
        assertTrue((spans.get(1).style & Canvas.UNDERLINE) != 0);
    }
}
