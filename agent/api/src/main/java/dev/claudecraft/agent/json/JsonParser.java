package dev.claudecraft.agent.json;

final class JsonParser {
    private final String text;
    private int pos;

    JsonParser(String text) {
        this.text = text;
    }

    Json parseDocument() {
        skipWhitespace();
        Json value = parseValue();
        skipWhitespace();
        if (pos < text.length()) throw error("Unexpected trailing characters");
        return value;
    }

    private Json parseValue() {
        if (pos >= text.length()) throw error("Unexpected end of input");
        char c = text.charAt(pos);
        switch (c) {
            case '{': return parseObject();
            case '[': return parseArray();
            case '"': return Json.raw(parseString());
            case 't': expectWord("true"); return Json.TRUE;
            case 'f': expectWord("false"); return Json.FALSE;
            case 'n': expectWord("null"); return Json.NULL;
            default:
                if (c == '-' || isDigit(c)) return parseNumber();
                throw error("Unexpected character '" + c + "'");
        }
    }

    private Json parseObject() {
        Json object = Json.object();
        pos++;
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("Expected property name");
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, parseValue());
            skipWhitespace();
            if (peek() == ',') {
                pos++;
                continue;
            }
            expect('}');
            return object;
        }
    }

    private Json parseArray() {
        Json array = Json.array();
        pos++;
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(parseValue());
            skipWhitespace();
            if (peek() == ',') {
                pos++;
                continue;
            }
            expect(']');
            return array;
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) throw error("Unterminated string");
            char c = text.charAt(pos++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) throw error("Unterminated escape");
            char escape = text.charAt(pos++);
            switch (escape) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u': out.append(parseUnicodeEscape()); break;
                default: throw error("Invalid escape '\\" + escape + "'");
            }
        }
    }

    private char parseUnicodeEscape() {
        if (pos + 4 > text.length()) throw error("Truncated unicode escape");
        try {
            char c = (char) Integer.parseInt(text.substring(pos, pos + 4), 16);
            pos += 4;
            return c;
        } catch (NumberFormatException e) {
            throw error("Invalid unicode escape");
        }
    }

    private Json parseNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < text.length() && isDigit(text.charAt(pos))) pos++;
        boolean integral = true;
        if (peek() == '.') {
            integral = false;
            pos++;
            while (pos < text.length() && isDigit(text.charAt(pos))) pos++;
        }
        if (peek() == 'e' || peek() == 'E') {
            integral = false;
            pos++;
            if (peek() == '+' || peek() == '-') pos++;
            while (pos < text.length() && isDigit(text.charAt(pos))) pos++;
        }
        String literal = text.substring(start, pos);
        try {
            if (integral && literal.length() < 19) return Json.raw(Long.parseLong(literal));
            return Json.raw(Double.parseDouble(literal));
        } catch (NumberFormatException e) {
            throw error("Invalid number '" + literal + "'");
        }
    }

    private void expectWord(String word) {
        if (!text.startsWith(word, pos)) throw error("Expected '" + word + "'");
        pos += word.length();
    }

    private void expect(char c) {
        if (peek() != c) throw error("Expected '" + c + "'");
        pos++;
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + pos);
    }
}
