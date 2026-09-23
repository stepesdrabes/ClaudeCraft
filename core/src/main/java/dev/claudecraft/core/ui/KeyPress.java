package dev.claudecraft.core.ui;

public final class KeyPress {
    public static final int SHIFT = 1;
    public static final int CONTROL = 2;
    public static final int ALT = 4;
    public static final int SUPER = 8;
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    private final Key key;
    private final int modifiers;

    public KeyPress(Key key, int modifiers) {
        this.key = key;
        this.modifiers = modifiers;
    }

    public Key key() {
        return key;
    }

    public boolean shift() {
        return (modifiers & SHIFT) != 0;
    }

    public boolean control() {
        return (modifiers & CONTROL) != 0;
    }

    public boolean alt() {
        return (modifiers & ALT) != 0;
    }

    public boolean shortcut() {
        return (modifiers & (MAC ? SUPER : CONTROL)) != 0;
    }

    public boolean wordJump() {
        return (modifiers & (MAC ? ALT : CONTROL)) != 0;
    }

    public boolean is(Key key) {
        return this.key == key;
    }
}
