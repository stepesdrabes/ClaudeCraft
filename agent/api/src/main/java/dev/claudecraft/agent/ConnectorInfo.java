package dev.claudecraft.agent;

import java.util.List;

public final class ConnectorInfo {
    private final List<Model> models;
    private final List<Mode> modes;
    private final List<Command> commands;
    private final String account;
    private final String version;
    private final String executable;

    public ConnectorInfo(List<Model> models, List<Mode> modes, List<Command> commands, String account, String version, String executable) {
        this.models = models;
        this.modes = modes;
        this.commands = commands;
        this.account = account;
        this.version = version;
        this.executable = executable;
    }

    public List<Model> models() {
        return models;
    }

    public List<Mode> modes() {
        return modes;
    }

    public List<Command> commands() {
        return commands;
    }

    public String account() {
        return account;
    }

    public String version() {
        return version;
    }

    public String executable() {
        return executable;
    }

    public Model model(String id) {
        for (Model model : models) if (model.id().equals(id)) return model;
        return null;
    }

    public static final class Model {
        private final String id;
        private final String label;
        private final String description;
        private final List<String> effortLevels;
        private final boolean available;

        public Model(String id, String label, String description, List<String> effortLevels, boolean available) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.effortLevels = effortLevels;
            this.available = available;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }

        public List<String> effortLevels() {
            return effortLevels;
        }

        public boolean available() {
            return available;
        }
    }

    public static final class Mode {
        private final String id;
        private final String label;

        public Mode(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }
    }

    public static final class Command {
        private final String name;
        private final String hint;
        private final String description;

        public Command(String name, String hint, String description) {
            this.name = name;
            this.hint = hint;
            this.description = description;
        }

        public String name() {
            return name;
        }

        public String hint() {
            return hint;
        }

        public String description() {
            return description;
        }
    }
}
