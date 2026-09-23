package dev.claudecraft.core.chat;

import dev.claudecraft.agent.ImageData;
import dev.claudecraft.agent.SessionListener;
import dev.claudecraft.agent.ToolOutput;
import dev.claudecraft.agent.ToolUse;
import dev.claudecraft.core.ui.Picture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Transcript implements SessionListener {
    private final List<Entry> entries = new ArrayList<>();
    private final Todos todos = new Todos();

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public Todos todos() {
        return todos;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public void onUserMessage(String text, List<ImageData> images) {
        addUser(text, pictures(images));
    }

    void addUser(String text, List<Picture> images) {
        finishStreaming();
        entries.add(new MessageEntry(MessageEntry.Role.USER, text, images, false));
    }

    @Override
    public void onText(String delta) {
        Entry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        if (last instanceof MessageEntry && ((MessageEntry) last).role() == MessageEntry.Role.ASSISTANT) {
            ((MessageEntry) last).append(delta);
        } else if (!delta.trim().isEmpty()) {
            entries.add(new MessageEntry(MessageEntry.Role.ASSISTANT, delta.replaceFirst("^\\s+", ""), Collections.<Picture>emptyList(), true));
        }
    }

    @Override
    public void onToolUse(ToolUse use) {
        finishStreaming();
        entries.add(new ToolEntry(use.id(), use.name(), use.input()));
    }

    @Override
    public void onSubagentToolUse(String parentToolUseId, ToolUse use) {
        ToolEntry parent = tool(parentToolUseId);
        if (parent == null) return;
        String[] label = ToolLabels.describe(use.name(), use.input());
        parent.addChild(label[1].isEmpty() ? label[0] : label[0] + " " + label[1]);
    }

    @Override
    public void onToolResult(ToolOutput output) {
        ToolEntry entry = tool(output.toolUseId());
        if (entry == null) return;
        entry.finish(output.text(), pictures(output.images()), output.error());
        if (!output.error() && Todos.tracks(entry.name())) todos.apply(entry.name(), entry.input(), output.details(), output.text());
    }

    public ToolEntry tool(String id) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry instanceof ToolEntry && ((ToolEntry) entry).id().equals(id)) return (ToolEntry) entry;
        }
        return null;
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

    private static List<Picture> pictures(List<ImageData> images) {
        if (images.isEmpty()) return Collections.emptyList();
        List<Picture> pictures = new ArrayList<>();
        for (ImageData image : images) pictures.add(new Picture(image));
        return pictures;
    }
}
