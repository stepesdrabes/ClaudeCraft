package dev.claudecraft.agent.tool;

import dev.claudecraft.agent.json.Json;

public final class Schema {
    private final Json properties = Json.object();
    private final Json required = Json.array();

    private Schema() {
    }

    public static Schema object() {
        return new Schema();
    }

    public Schema required(String name, String type, String description) {
        required.add(name);
        return optional(name, type, description);
    }

    public Schema optional(String name, String type, String description) {
        properties.put(name, Json.object().put("type", type).put("description", description));
        return this;
    }

    public Json toJson() {
        return Json.object().put("type", "object").put("properties", properties.copy()).put("required", required.copy());
    }
}
