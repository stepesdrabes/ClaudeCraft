package dev.claudecraft.agent.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Json {
    public static final Json NULL = new Json(null);
    public static final Json TRUE = new Json(Boolean.TRUE);
    public static final Json FALSE = new Json(Boolean.FALSE);

    private final Object value;

    private Json(Object value) {
        this.value = value;
    }

    public static Json object() {
        return new Json(new LinkedHashMap<String, Json>());
    }

    public static Json array() {
        return new Json(new ArrayList<Json>());
    }

    public static Json parse(String text) {
        return new JsonParser(text).parseDocument();
    }

    public static Json of(Object value) {
        if (value == null) return NULL;
        if (value instanceof Json) return (Json) value;
        if (value instanceof Boolean) return (Boolean) value ? TRUE : FALSE;
        if (value instanceof String || value instanceof Number) return new Json(value);
        if (value instanceof Character) return new Json(value.toString());
        if (value instanceof Map) {
            Json object = object();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return object;
        }
        if (value instanceof Collection) {
            Json array = array();
            for (Object item : (Collection<?>) value) array.add(item);
            return array;
        }
        throw new IllegalArgumentException("Not representable as JSON: " + value.getClass().getName());
    }

    static Json raw(Object value) {
        return new Json(value);
    }

    public Json put(String key, Object value) {
        map().put(key, of(value));
        return this;
    }

    public Json add(Object value) {
        list().add(of(value));
        return this;
    }

    public Json get(String key) {
        if (!isObject()) return NULL;
        Json found = map().get(key);
        return found == null ? NULL : found;
    }

    public Json get(int index) {
        if (!isArray() || index < 0 || index >= list().size()) return NULL;
        return list().get(index);
    }

    public boolean has(String key) {
        return isObject() && map().containsKey(key);
    }

    public boolean isNull() {
        return value == null;
    }

    public boolean isObject() {
        return value instanceof Map;
    }

    public boolean isArray() {
        return value instanceof List;
    }

    public boolean isString() {
        return value instanceof String;
    }

    public boolean isNumber() {
        return value instanceof Number;
    }

    public boolean isBoolean() {
        return value instanceof Boolean;
    }

    public String asString() {
        return isString() ? (String) value : null;
    }

    public String asString(String fallback) {
        return isString() ? (String) value : fallback;
    }

    public long asLong(long fallback) {
        return isNumber() ? ((Number) value).longValue() : fallback;
    }

    public int asInt(int fallback) {
        return isNumber() ? ((Number) value).intValue() : fallback;
    }

    public double asDouble(double fallback) {
        return isNumber() ? ((Number) value).doubleValue() : fallback;
    }

    public boolean asBoolean(boolean fallback) {
        return isBoolean() ? (Boolean) value : fallback;
    }

    public List<Json> items() {
        return isArray() ? Collections.unmodifiableList(list()) : Collections.<Json>emptyList();
    }

    public Set<Map.Entry<String, Json>> entries() {
        return isObject() ? Collections.unmodifiableMap(map()).entrySet() : Collections.<String, Json>emptyMap().entrySet();
    }

    public int size() {
        if (isArray()) return list().size();
        if (isObject()) return map().size();
        return 0;
    }

    public Json copy() {
        return parse(toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Json> map() {
        if (!isObject()) throw new IllegalStateException("Not a JSON object: " + this);
        return (Map<String, Json>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Json> list() {
        if (!isArray()) throw new IllegalStateException("Not a JSON array: " + this);
        return (List<Json>) value;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        write(out);
        return out.toString();
    }

    private void write(StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            writeString((String) value, out);
        } else if (value instanceof Number) {
            writeNumber((Number) value, out);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (isArray()) {
            out.append('[');
            List<Json> items = list();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) out.append(',');
                items.get(i).write(out);
            }
            out.append(']');
        } else {
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Json> entry : map().entrySet()) {
                if (!first) out.append(',');
                first = false;
                writeString(entry.getKey(), out);
                out.append(':');
                entry.getValue().write(out);
            }
            out.append('}');
        }
    }

    private static void writeNumber(Number number, StringBuilder out) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) out.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) out.append((long) d);
            else out.append(d);
        } else {
            out.append(number);
        }
    }

    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        out.append('"');
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Json)) return false;
        Object otherValue = ((Json) other).value;
        if (value instanceof Number && otherValue instanceof Number) {
            return ((Number) value).doubleValue() == ((Number) otherValue).doubleValue();
        }
        return Objects.equals(value, otherValue);
    }

    @Override
    public int hashCode() {
        return value instanceof Number ? Double.hashCode(((Number) value).doubleValue()) : Objects.hashCode(value);
    }
}
