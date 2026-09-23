package dev.claudecraft.core.chat;

import dev.claudecraft.agent.json.Json;
import dev.claudecraft.core.ui.Picture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ToolEntry extends Entry {
    public enum State { RUNNING, SUCCEEDED, FAILED }

    private static final int KEPT_CHILDREN = 3;

    private final String id;
    private final String name;
    private final Json input;
    private final String label;
    private final String detail;
    private final List<String> children = new ArrayList<>();
    private int childCount;
    private State state = State.RUNNING;
    private String output = "";
    private List<Picture> images = Collections.emptyList();
    private boolean expanded;

    public ToolEntry(String id, String name, Json input) {
        this.id = id;
        this.name = name;
        this.input = input;
        String[] described = ToolLabels.describe(name, input);
        this.label = described[0];
        this.detail = described[1];
        this.expanded = "ExitPlanMode".equals(name);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Json input() {
        return input;
    }

    public String label() {
        return label;
    }

    public String detail() {
        return detail;
    }

    public State state() {
        return state;
    }

    public String output() {
        return output;
    }

    public List<Picture> images() {
        return images;
    }

    public List<String> children() {
        return Collections.unmodifiableList(children);
    }

    public int childCount() {
        return childCount;
    }

    public boolean expanded() {
        return expanded;
    }

    public void toggle() {
        expanded = !expanded;
        changed();
    }

    void addChild(String line) {
        children.add(line);
        if (children.size() > KEPT_CHILDREN) children.remove(0);
        childCount++;
        changed();
    }

    void finish(String output, List<Picture> images, boolean failed) {
        this.output = output;
        this.images = images;
        this.state = failed ? State.FAILED : State.SUCCEEDED;
        changed();
    }

    void abandon() {
        if (state != State.RUNNING) return;
        state = State.FAILED;
        changed();
    }
}
