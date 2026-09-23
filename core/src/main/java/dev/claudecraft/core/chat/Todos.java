package dev.claudecraft.core.chat;

import dev.claudecraft.agent.json.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Todos {
    private static final Pattern CREATED = Pattern.compile("Task #(\\w+) created");

    public enum State { PENDING, ACTIVE, DONE }

    public static final class Item {
        private final String id;
        private String subject;
        private String activeForm;
        private State state = State.PENDING;

        Item(String id, String subject, String activeForm) {
            this.id = id;
            this.subject = subject;
            this.activeForm = activeForm;
        }

        public String subject() {
            return subject;
        }

        public String label() {
            return state == State.ACTIVE && activeForm != null && !activeForm.isEmpty() ? activeForm : subject;
        }

        public State state() {
            return state;
        }
    }

    private final List<Item> items = new ArrayList<>();

    public List<Item> items() {
        return Collections.unmodifiableList(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int done() {
        int done = 0;
        for (Item item : items) if (item.state == State.DONE) done++;
        return done;
    }

    public Item active() {
        for (Item item : items) if (item.state == State.ACTIVE) return item;
        return null;
    }

    static boolean tracks(String toolName) {
        return "TodoWrite".equals(toolName) || "TaskCreate".equals(toolName) || "TaskUpdate".equals(toolName);
    }

    void apply(String toolName, Json input, Json details, String output) {
        switch (toolName) {
            case "TodoWrite":
                items.clear();
                int index = 0;
                for (Json todo : input.get("todos").items()) {
                    Item item = new Item(String.valueOf(++index), todo.get("content").asString(""), todo.get("activeForm").asString(null));
                    item.state = state(todo.get("status").asString(""));
                    items.add(item);
                }
                break;
            case "TaskCreate":
                String id = details.get("task").get("id").asString(createdId(output));
                if (id != null) items.add(new Item(id, input.get("subject").asString(""), input.get("activeForm").asString(null)));
                break;
            case "TaskUpdate":
                update(input);
                break;
            default:
                break;
        }
    }

    private void update(Json input) {
        Item item = find(input.get("taskId").asString(""));
        if (item == null) return;
        String status = input.get("status").asString("");
        if ("deleted".equals(status)) {
            items.remove(item);
            return;
        }
        if (!status.isEmpty()) item.state = state(status);
        item.subject = input.get("subject").asString(item.subject);
        item.activeForm = input.get("activeForm").asString(item.activeForm);
    }

    private Item find(String id) {
        for (Item item : items) if (item.id.equals(id)) return item;
        return null;
    }

    private static String createdId(String output) {
        Matcher matcher = CREATED.matcher(output);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static State state(String status) {
        if ("completed".equals(status)) return State.DONE;
        if ("in_progress".equals(status)) return State.ACTIVE;
        return State.PENDING;
    }
}
