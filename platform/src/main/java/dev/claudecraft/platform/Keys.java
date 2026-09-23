package dev.claudecraft.platform;

import com.mojang.blaze3d.platform.InputConstants;
import dev.claudecraft.core.ui.Key;
import dev.claudecraft.core.ui.KeyPress;
import net.minecraft.client.KeyMapping;
//? if >=1.21.9
import net.minecraft.client.input.KeyEvent;

import java.util.HashMap;
import java.util.Map;

final class Keys {
    private static final int GLFW_MODIFIERS = KeyPress.SHIFT | KeyPress.CONTROL | KeyPress.ALT | KeyPress.SUPER;
    private static final Map<Integer, Key> KEYS = new HashMap<>();

    static {
        bind(Key.ENTER, "enter", "keypad.enter");
        bind(Key.ESCAPE, "escape");
        bind(Key.BACKSPACE, "backspace");
        bind(Key.DELETE, "delete");
        bind(Key.TAB, "tab");
        bind(Key.LEFT, "left");
        bind(Key.RIGHT, "right");
        bind(Key.UP, "up");
        bind(Key.DOWN, "down");
        bind(Key.HOME, "home");
        bind(Key.END, "end");
        bind(Key.PAGE_UP, "page.up");
        bind(Key.PAGE_DOWN, "page.down");
        bind(Key.A, "a");
        bind(Key.B, "b");
        bind(Key.C, "c");
        bind(Key.N, "n");
        bind(Key.V, "v");
        bind(Key.X, "x");
        bind(Key.Y, "y");
        for (int digit = 1; digit <= 9; digit++) bind(Key.values()[Key.DIGIT_1.ordinal() + digit - 1], String.valueOf(digit));
    }

    static int code(String name) {
        return InputConstants.getKey("key.keyboard." + name).getValue();
    }

    private static void bind(Key key, String... names) {
        for (String name : names) KEYS.put(code(name), key);
    }

    private Keys() {
    }

    //? if >=1.21.9 {
    static KeyPress press(KeyEvent event, KeyMapping openKey) {
        Key key = openKey.matches(event) ? Key.OPEN_PANEL : KEYS.getOrDefault(event.key(), Key.OTHER);
        return new KeyPress(key, modifiers(event));
    }
    //?} else {
    /*static KeyPress press(int keyCode, int scanCode, int modifiers, KeyMapping openKey) {
        Key key = openKey.matches(keyCode, scanCode) ? Key.OPEN_PANEL : KEYS.getOrDefault(keyCode, Key.OTHER);
        return new KeyPress(key, modifiers & GLFW_MODIFIERS);
    }
    *///?}

    //? if >=1.21.9 {
    static int button(int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) return 0;
        return button == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : 2;
    }
    //?}

    //? if >=1.21.11 {
    private static int modifiers(KeyEvent event) {
        int modifiers = 0;
        if (event.hasShiftDown()) modifiers |= KeyPress.SHIFT;
        if (event.hasControlDown()) modifiers |= KeyPress.CONTROL;
        if (event.hasAltDown()) modifiers |= KeyPress.ALT;
        if (event.hasControlDownWithQuirk() && !event.hasControlDown()) modifiers |= KeyPress.SUPER;
        return modifiers;
    }
    //?} elif >=1.21.9 {
    /*private static int modifiers(KeyEvent event) {
        return event.modifiers() & GLFW_MODIFIERS;
    }
    *///?}
}
