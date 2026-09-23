package dev.claudecraft.legacy;

import dev.claudecraft.core.ui.Key;
import dev.claudecraft.core.ui.KeyPress;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Map;

final class LegacyKeys {
    private static final Map<Integer, Key> KEYS = new HashMap<>();

    static {
        KEYS.put(Keyboard.KEY_RETURN, Key.ENTER);
        KEYS.put(Keyboard.KEY_NUMPADENTER, Key.ENTER);
        KEYS.put(Keyboard.KEY_ESCAPE, Key.ESCAPE);
        KEYS.put(Keyboard.KEY_BACK, Key.BACKSPACE);
        KEYS.put(Keyboard.KEY_DELETE, Key.DELETE);
        KEYS.put(Keyboard.KEY_TAB, Key.TAB);
        KEYS.put(Keyboard.KEY_LEFT, Key.LEFT);
        KEYS.put(Keyboard.KEY_RIGHT, Key.RIGHT);
        KEYS.put(Keyboard.KEY_UP, Key.UP);
        KEYS.put(Keyboard.KEY_DOWN, Key.DOWN);
        KEYS.put(Keyboard.KEY_HOME, Key.HOME);
        KEYS.put(Keyboard.KEY_END, Key.END);
        KEYS.put(Keyboard.KEY_PRIOR, Key.PAGE_UP);
        KEYS.put(Keyboard.KEY_NEXT, Key.PAGE_DOWN);
        KEYS.put(Keyboard.KEY_A, Key.A);
        KEYS.put(Keyboard.KEY_C, Key.C);
        KEYS.put(Keyboard.KEY_N, Key.N);
        KEYS.put(Keyboard.KEY_V, Key.V);
        KEYS.put(Keyboard.KEY_X, Key.X);
        KEYS.put(Keyboard.KEY_Y, Key.Y);
        int[] digits = {Keyboard.KEY_1, Keyboard.KEY_2, Keyboard.KEY_3, Keyboard.KEY_4, Keyboard.KEY_5,
            Keyboard.KEY_6, Keyboard.KEY_7, Keyboard.KEY_8, Keyboard.KEY_9};
        for (int i = 0; i < digits.length; i++) KEYS.put(digits[i], Key.values()[Key.DIGIT_1.ordinal() + i]);
    }

    private LegacyKeys() {
    }

    static KeyPress press(int keyCode, KeyBinding openKey) {
        Key key = keyCode == openKey.getKeyCode() ? Key.OPEN_PANEL : KEYS.getOrDefault(keyCode, Key.OTHER);
        int modifiers = 0;
        if (down(Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT)) modifiers |= KeyPress.SHIFT;
        if (down(Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL)) modifiers |= KeyPress.CONTROL;
        if (down(Keyboard.KEY_LMENU, Keyboard.KEY_RMENU)) modifiers |= KeyPress.ALT;
        if (down(Keyboard.KEY_LMETA, Keyboard.KEY_RMETA)) modifiers |= KeyPress.SUPER;
        return new KeyPress(key, modifiers);
    }

    private static boolean down(int left, int right) {
        return Keyboard.isKeyDown(left) || Keyboard.isKeyDown(right);
    }
}
