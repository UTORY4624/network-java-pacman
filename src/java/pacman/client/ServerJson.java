package pacman.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ServerJson {
    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private final String input;
    private int position;

    private ServerJson(String input) {
        this.input = input;
    }

    static Map<String, Object> parse(String input) {
        ServerJson parser = new ServerJson(input);
        Map<String, Object> object = parser.object(0);
        parser.skipWhitespace();
        if (parser.position != input.length()) throw invalid();
        return object;
    }

    private Map<String, Object> object(int depth) {
        expect('{');
        Map<String, Object> fields = new LinkedHashMap<>();
        if (!take('}')) {
            do {
                String key = string();
                expect(':');
                fields.put(key, value(depth + 1));
            } while (take(','));
            expect('}');
        }
        return fields;
    }

    private List<Object> array(int depth) {
        expect('[');
        List<Object> items = new ArrayList<>();
        if (!take(']')) {
            do {
                items.add(value(depth + 1));
            } while (take(','));
            expect(']');
        }
        return items;
    }

    private Object value(int depth) {
        skipWhitespace();
        if (depth > 16 || position == input.length()) throw invalid();
        switch (input.charAt(position)) {
            case '{': return object(depth);
            case '[': return array(depth);
            case '"': return string();
            case 't': return literal("true", true);
            case 'f': return literal("false", false);
            case 'n': return literal("null", null);
            default: return number();
        }
    }

    private Object literal(String token, Object value) {
        if (!input.startsWith(token, position)) throw invalid();
        position += token.length();
        return value;
    }

    private Number number() {
        Matcher matcher = NUMBER.matcher(input).region(position, input.length());
        if (!matcher.lookingAt()) throw invalid();
        position = matcher.end();
        String token = matcher.group();
        if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) {
            return Long.valueOf(token);
        }
        return Double.valueOf(token);
    }

    private String string() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (position < input.length()) {
            char ch = input.charAt(position++);
            if (ch == '"') return result.toString();
            if (ch < ' ') throw invalid();
            result.append(ch == '\\' ? escaped() : ch);
        }
        throw invalid();
    }

    private char escaped() {
        if (position == input.length()) throw invalid();
        char ch = input.charAt(position++);
        switch (ch) {
            case '"': case '\\': case '/': return ch;
            case 'b': return '\b';
            case 'f': return '\f';
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            case 'u':
                if (position + 4 > input.length()) throw invalid();
                int code = 0;
                for (int i = 0; i < 4; i++) {
                    int digit = Character.digit(input.charAt(position++), 16);
                    if (digit < 0) throw invalid();
                    code = code * 16 + digit;
                }
                return (char) code;
            default: throw invalid();
        }
    }

    private boolean take(char ch) {
        skipWhitespace();
        if (position == input.length() || input.charAt(position) != ch) return false;
        position++;
        return true;
    }

    private void expect(char ch) {
        if (!take(ch)) throw invalid();
    }

    private void skipWhitespace() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("잘못된 서버 JSON 응답");
    }
}
