package dev.claudecraft.core;

import dev.claudecraft.agent.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public final class Config {
    private final Path file;
    private final Json values;

    private Config(Path file, Json values) {
        this.file = file;
        this.values = values;
    }

    public static Config load(Path file) {
        Json values = Json.object();
        try {
            if (Files.exists(file)) values = Json.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException ignored) {
        }
        Config config = new Config(file, values.isObject() ? values : Json.object());
        config.fillDefaults();
        config.save();
        return config;
    }

    private void fillDefaults() {
        putIfMissing("claudePath", "");
        putIfMissing("workspace", "");
        putIfMissing("model", "");
        putIfMissing("permissionMode", "default");
        putIfMissing("mcpServer", true);
        putIfMissing("mcpPort", 25595);
        putIfMissing("sounds", true);
        putIfMissing("toasts", true);
    }

    private void putIfMissing(String key, Object value) {
        if (!values.has(key)) values.put(key, value);
    }

    public String claudePath() {
        return values.get("claudePath").asString("");
    }

    public Path workspace(Path fallback) {
        String path = values.get("workspace").asString("");
        return path.isEmpty() || !Files.isDirectory(Paths.get(path)) ? fallback : Paths.get(path);
    }

    public void setWorkspace(Path workspace) {
        values.put("workspace", workspace.toString());
        save();
    }

    public String model() {
        String model = values.get("model").asString("");
        return model.isEmpty() ? null : model;
    }

    public void setModel(String model) {
        values.put("model", model);
        save();
    }

    public String permissionMode() {
        return values.get("permissionMode").asString("default");
    }

    public void setPermissionMode(String mode) {
        values.put("permissionMode", mode);
        save();
    }

    public boolean mcpServer() {
        return values.get("mcpServer").asBoolean(true);
    }

    public int mcpPort() {
        return values.get("mcpPort").asInt(25595);
    }

    public boolean sounds() {
        return values.get("sounds").asBoolean(true);
    }

    public boolean toasts() {
        return values.get("toasts").asBoolean(true);
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, pretty(values).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private static String pretty(Json object) {
        StringBuilder out = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, Json> entry : object.entries()) {
            out.append("  ").append(Json.of(entry.getKey())).append(": ").append(entry.getValue());
            out.append(++i < object.size() ? ",\n" : "\n");
        }
        return out.append("}\n").toString();
    }
}
