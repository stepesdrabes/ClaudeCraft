package dev.claudecraft.agent.json;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {
    @Test
    void parsesNestedDocuments() {
        Json json = Json.parse("{\"type\":\"control_request\",\"request\":{\"subtype\":\"can_use_tool\",\"input\":{\"n\":[1,2.5,-3e2]}},\"ok\":true,\"none\":null}");

        assertEquals("control_request", json.get("type").asString());
        assertEquals("can_use_tool", json.get("request").get("subtype").asString());
        assertEquals(2.5, json.get("request").get("input").get("n").get(1).asDouble(0), 1e-9);
        assertEquals(-300, json.get("request").get("input").get("n").get(2).asLong(0));
        assertTrue(json.get("ok").asBoolean(false));
        assertTrue(json.get("none").isNull());
    }

    @Test
    void missingPathsAreNullNotErrors() {
        Json json = Json.parse("{\"a\":1}");

        assertTrue(json.get("b").get("c").get(3).isNull());
        assertEquals("fallback", json.get("b").asString("fallback"));
        assertEquals(0, json.get("a").items().size());
    }

    @Test
    void roundTripsEscapesAndUnicode() {
        String text = "quote \" backslash \\ newline \n tab \t emoji \uD83E\uDDF1 control \u0001";
        Json json = Json.object().put("text", text);

        assertEquals(text, Json.parse(json.toString()).get("text").asString());
        assertEquals("\u00e9", Json.parse("\"\\u00e9\"").asString());
    }

    @Test
    void writesCompactJson() {
        Json json = Json.object()
            .put("id", 7)
            .put("ratio", 0.5)
            .put("whole", 3.0)
            .put("list", Arrays.asList("a", null, true))
            .put("map", Collections.singletonMap("k", "v"));

        assertEquals("{\"id\":7,\"ratio\":0.5,\"whole\":3,\"list\":[\"a\",null,true],\"map\":{\"k\":\"v\"}}", json.toString());
    }

    @Test
    void numbersCompareByValue() {
        assertEquals(Json.parse("1"), Json.of(1.0));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{} extra"));
    }
}
