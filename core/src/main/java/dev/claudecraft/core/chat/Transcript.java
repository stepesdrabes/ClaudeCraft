package dev.claudecraft.core.chat;

import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.ToolUse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Transcript implements SessionListener {
    private final List<Entry> entries = new ArrayList<>();

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public void onUserMessage(String text) {
        finishStreaming();
        entries.add(new MessageEntry(MessageEntry.Role.USER, text, false));
    }

    @Override
    public void onText(String delta) {
        Entry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        if (last instanceof MessageEntry && ((MessageEntry) last).role() == MessageEntry.Role.ASSISTANT) {
            ((MessageEntry) last).append(delta);
        } else if (!delta.trim().isEmpty()) {
            entries.add(new MessageEntry(MessageEntry.Role.ASSISTANT, delta.replaceFirst("^\\s+", ""), true));
        }
    }

    @Override
    public void onToolUse(ToolUse use) {
        finishStreaming();
        String[] label = ToolLabels.describe(use.name(), use.input());
        entries.add(new ToolEntry(use.id(), label[0], label[1]));
    }

    @Override
    public void onToolResult(String toolUseId, String output, boolean error) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry instanceof ToolEntry && ((ToolEntry) entry).id().equals(toolUseId)) {
                ((ToolEntry) entry).finish(output, error);
                return;
            }
        }
    }

    void notice(String text, int color) {
        finishStreaming();
        entries.add(new NoticeEntry(text, color));
    }

    void settle() {
        finishStreaming();
        for (Entry entry : entries) if (entry instanceof ToolEntry) ((ToolEntry) entry).abandon();
    }

    private void finishStreaming() {
        for (Entry entry : entries) if (entry instanceof MessageEntry) ((MessageEntry) entry).finish();
    }
}
