package dev.flightblock;

/** Closes Gson's documented LEGACY_STRICT exceptions without replacing its parser. */
public final class LegacyJson {
    private LegacyJson() {}
    public static void validate(String json) {
        boolean quoted = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (c < 32) throw new IllegalArgumentException("Unescaped JSON control character");
                if (c == '\\') {
                    if (++i == json.length() || "\"\\/bfnrtu".indexOf(json.charAt(i)) < 0)
                        throw new IllegalArgumentException("Invalid JSON escape");
                } else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (Character.isLetter(c)) {
                int start = i;
                while (i + 1 < json.length() && Character.isLetter(json.charAt(i + 1))) i++;
                String word = json.substring(start, i + 1);
                for (String literal : new String[]{"true", "false", "null"})
                    if (word.equalsIgnoreCase(literal) && !word.equals(literal))
                        throw new IllegalArgumentException("JSON literals must be lowercase");
            }
        }
        if (quoted) throw new IllegalArgumentException("Unterminated JSON string");
    }
    public static void main(String[] args) {
        validate("{\"v\":[true,false,null,1e2,\"True\\n\\u0000\\\"\\\\\"]}");
        String[] invalid = {"{\"v\":True}", "{\"v\":fAlSe}", "{\"v\":NULL}",
            "{\"v\":\"a\nb\"}", "{\"v\":\"a" + (char)0 + "b\"}",
            "{\"v\":\"\\'\"}", "{\"v\":\"\\\n\"}"};
        for (String json : invalid) {
            try { validate(json); } catch (IllegalArgumentException expected) { continue; }
            throw new AssertionError("Accepted nonstandard JSON: " + json);
        }
        System.out.println("FLIGHTBLOCK_LEGACY_JSON_OK: 8 lexical checks passed.");
    }
}
