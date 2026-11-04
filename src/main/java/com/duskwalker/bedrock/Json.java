package com.duskwalker.bedrock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JS JSON.parse / JSON.stringify karsiligi, bagimliliksiz. Degerler:
 *   nesne -> LinkedHashMap&lt;String,Object&gt;, dizi -> ArrayList&lt;Object&gt;, sayi -> Double, metin -> String, boolean -> Boolean, null -> null.
 * Kalici oyuncu/dunya verisi (dinamik ozellikler) bu bicimde JSON metni olarak saklanir; boylece
 * Bedrock surumuyle ayni veri bicimi korunur.
 */
public final class Json {
    private Json() {}

    // ------------------------------------------------------------ yardimcilar
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) { return o instanceof Map ? (Map<String, Object>) o : null; }
    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) { return o instanceof List ? (List<Object>) o : null; }
    public static double num(Object o, double def) { return o instanceof Number n ? n.doubleValue() : def; }
    public static int intv(Object o, int def) { return o instanceof Number n ? (int) Math.round(n.doubleValue()) : def; }
    public static String str(Object o, String def) { return o instanceof String s ? s : def; }
    public static boolean bool(Object o, boolean def) { return o instanceof Boolean b ? b : def; }
    public static Map<String, Object> newMap() { return new LinkedHashMap<>(); }
    public static List<Object> newList() { return new ArrayList<>(); }

    // ------------------------------------------------------------ stringify
    public static String stringify(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String s) { quote(sb, s); return; }
        if (v instanceof Boolean b) { sb.append(b ? "true" : "false"); return; }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) { sb.append("null"); return; }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d); else sb.append(d);
            return;
        }
        if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o);
            }
            sb.append(']');
            return;
        }
        if (v instanceof Object[] arr) {
            sb.append('[');
            for (int i = 0; i < arr.length; i++) { if (i > 0) sb.append(','); write(sb, arr[i]); }
            sb.append(']');
            return;
        }
        quote(sb, String.valueOf(v));
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------ parse
    /** Gecersiz JSON'da BedrockException firlatir (JS'te SyntaxError gibi). */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != text.length()) throw new BedrockException("JSON: trailing characters");
        return v;
    }

    private static final class Parser {
        final String s; int i = 0;
        Parser(String s) { this.s = s; }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        char peek() { if (i >= s.length()) throw new BedrockException("JSON: unexpected end"); return s.charAt(i); }

        Object value() {
            char c = peek();
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                if (peek() != ':') throw new BedrockException("JSON: expected ':'");
                i++; ws();
                m.put(k, value());
                ws();
                char c = peek();
                i++;
                if (c == ',') continue;
                if (c == '}') return m;
                throw new BedrockException("JSON: expected ',' or '}'");
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value());
                ws();
                char c = peek();
                i++;
                if (c == ',') continue;
                if (c == ']') return l;
                throw new BedrockException("JSON: expected ',' or ']'");
            }
        }

        String string() {
            if (peek() != '"') throw new BedrockException("JSON: expected string");
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                i++;
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = peek();
                    i++;
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> sb.append(e);
                    }
                } else sb.append(c);
            }
        }

        Double number() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new BedrockException("JSON: unexpected token at " + i);
            return Double.valueOf(s.substring(st, i));
        }
    }
}
