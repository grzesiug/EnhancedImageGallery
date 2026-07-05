package org.sleuthkit.autopsy.enhancedgallery.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimalny, samodzielny parser JSON (bez zewnetrznych zaleznosci) do
 * odczytu odpowiedzi z {@code /classify}.
 *
 * <p>Powstal jako zastepstwo dla Gson: ladowanie Gson z zewnetrznego jara
 * dolaczonego przez recznwy wpis Class-Path w manifescie zawieszalo Autopsy
 * (podejrzenie deadlocku w {@code ProxyClassLoader} NetBeans przy ladowaniu
 * spoza standardowego mechanizmu modulow, z EDT). Schemat JSON jest prosty
 * i kontrolowany przez nas po obu stronach (service/app/schemas.py), wiec
 * pelnoprawna biblioteka nie jest tu potrzebna.</p>
 */
final class MiniJson {

    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    /**
     * Serializuje Map/List/String/Number/Boolean/null do sformatowanego JSON
     * (2-spacjowe wciecie), do zapisu configu z panelu Global Settings.
     * Wspiera tylko typy jakich uzywamy w config/*.json - nie jest to
     * pelnoprawny writer.
     */
    static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(s, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            writeMap((Map<String, Object>) value, sb, indent);
        } else if (value instanceof List) {
            writeList((List<Object>) value, sb, indent);
        } else {
            writeString(String.valueOf(value), sb);
        }
    }

    private static void writeMap(Map<String, Object> map, StringBuilder sb, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            indent(sb, indent + 1);
            writeString(entry.getKey(), sb);
            sb.append(": ");
            writeValue(entry.getValue(), sb, indent + 1);
            if (++i < map.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append('}');
    }

    private static void writeList(List<Object> list, StringBuilder sb, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(sb, indent + 1);
            writeValue(list.get(i), sb, indent + 1);
            if (i < list.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append('"');
    }

    private static void indent(StringBuilder sb, int level) {
        sb.append("  ".repeat(level));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String json) {
        MiniJson parser = new MiniJson(json);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expected JSON object at top level");
        }
        return (Map<String, Object>) value;
    }

    private Object parseValue() {
        skipWhitespace();
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return parseMap();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return parseNumber();
        }
    }

    private Map<String, Object> parseMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expectChar(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            char next = text.charAt(pos++);
            if (next == '}') {
                break;
            }
            if (next != ',') {
                throw new IllegalArgumentException("Expected ',' or '}' at position " + (pos - 1));
            }
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            char next = text.charAt(pos++);
            if (next == ']') {
                break;
            }
            if (next != ',') {
                throw new IllegalArgumentException("Expected ',' or ']' at position " + (pos - 1));
            }
        }
        return list;
    }

    private String parseString() {
        expectChar('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = text.charAt(pos++);
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                char esc = text.charAt(pos++);
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        String hex = text.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown escape: \\" + esc);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Double parseNumber() {
        int start = pos;
        while (pos < text.length() && "-+.0123456789eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        return Double.parseDouble(text.substring(start, pos));
    }

    private void expect(String literal) {
        if (!text.regionMatches(pos, literal, 0, literal.length())) {
            throw new IllegalArgumentException("Expected '" + literal + "' at position " + pos);
        }
        pos += literal.length();
    }

    private void expectChar(char expected) {
        char actual = text.charAt(pos++);
        if (actual != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' but got '" + actual + "' at position " + (pos - 1));
        }
    }

    private char peek() {
        return text.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }
}
