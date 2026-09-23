package dev.claudecraft.platform;

import dev.claudecraft.core.ui.Canvas;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.MutableComponent;
//? if >=1.19 {
import net.minecraft.network.chat.Component;
//?} else
//import net.minecraft.network.chat.TextComponent;
//? if <1.17
//import net.minecraft.network.chat.TextColor;

final class Text {
    private static final int FORMATTING = Canvas.BOLD | Canvas.ITALIC | Canvas.UNDERLINE | Canvas.STRIKETHROUGH;

    private Text() {
    }

    static MutableComponent literal(String text, int style) {
        //? if >=1.19 {
        MutableComponent component = Component.literal(text.replace('§', '?'));
        //?} else
        //MutableComponent component = new TextComponent(text.replace('§', '?'));
        if ((style & FORMATTING) == 0) return component;
        component = component.withStyle(s -> s
            .withBold((style & Canvas.BOLD) != 0)
            .withItalic((style & Canvas.ITALIC) != 0)
            .withUnderlined((style & Canvas.UNDERLINE) != 0));
        return (style & Canvas.STRIKETHROUGH) != 0 ? component.withStyle(ChatFormatting.STRIKETHROUGH) : component;
    }

    static MutableComponent colored(String text, int rgb) {
        //? if >=1.17 {
        return literal(text, 0).withStyle(s -> s.withColor(rgb));
        //?} else
        //return literal(text, 0).withStyle(s -> s.withColor(TextColor.fromRgb(rgb)));
    }

    static int width(Font font, String text, int style) {
        return font.width(literal(text, style));
    }
}
