package dev.claudecraft.core.chat;

public abstract class Entry {
    private int revision;

    public int revision() {
        return revision;
    }

    protected void changed() {
        revision++;
    }
}
