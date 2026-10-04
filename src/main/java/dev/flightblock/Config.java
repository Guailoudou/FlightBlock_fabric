package dev.flightblock;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.Strictness;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.Map;

public record Config(long first, long second, long third, int firstRadius, int secondRadius, int thirdRadius, int interval, int slowSeconds, ZoneId zone) {
    public static final String DEFAULT_JSON = """
        {
          "configVersion": 1,
          "durationsSeconds": {"1": 300, "2": 900, "3": 21600},
          "radiiBlocks": {"1": 64, "2": 128, "3": 256},
          "flightCheckIntervalTicks": 5,
          "slowFallingSeconds": 10,
          "displayTimeZone": "Asia/Shanghai"
        }
        """;
    public static Config defaults() { return parse(DEFAULT_JSON); }
    public int radius(int level) {
        return switch (level) { case 1 -> firstRadius; case 2 -> secondRadius; case 3 -> thirdRadius;
            default -> throw new IllegalArgumentException("level"); };
    }
    public long seconds(int level) {
        return switch (level) { case 1 -> first; case 2 -> second; case 3 -> third;
            default -> throw new IllegalArgumentException("level"); };
    }
    public static Config read(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }
    public static Config parse(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(Strictness.STRICT);
            JsonElement root = JsonParser.parseReader(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("JSON 末尾有额外内容");
            JsonObject obj = root.getAsJsonObject();
            number(obj, "configVersion", 1, 1);
            JsonObject durations = obj.getAsJsonObject("durationsSeconds");
            if (durations == null || !durations.keySet().equals(java.util.Set.of("1", "2", "3")))
                throw new IllegalArgumentException("durationsSeconds 必须恰好包含 1、2、3");
            // Older config files omit radiiBlocks; they use the new tier defaults.
            JsonObject radii = obj.has("radiiBlocks") ? obj.getAsJsonObject("radiiBlocks")
                : JsonParser.parseString("{\"1\":64,\"2\":128,\"3\":256}").getAsJsonObject();
            if (radii == null || !radii.keySet().equals(java.util.Set.of("1", "2", "3")))
                throw new IllegalArgumentException("radiiBlocks 必须恰好包含 1、2、3");
            JsonElement tz = obj.get("displayTimeZone");
            if (tz == null || !tz.isJsonPrimitive() || !tz.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("displayTimeZone 必须是字符串");
            return new Config(number(durations, "1", 1, 31536000), number(durations, "2", 1, 31536000),
                number(durations, "3", 1, 31536000), (int) number(radii, "1", 1, 4096),
                (int) number(radii, "2", 1, 4096), (int) number(radii, "3", 1, 4096), (int) number(obj, "flightCheckIntervalTicks", 1, 20),
                (int) number(obj, "slowFallingSeconds", 0, 60), ZoneId.of(tz.getAsString()));
        } catch (Exception e) {
            throw new IllegalArgumentException("配置无效：" + e.getMessage(), e);
        }
    }
    private static long number(JsonObject obj, String key, long min, long max) {
        JsonElement value = obj.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
            || !value.getAsString().matches("-?(0|[1-9][0-9]*)"))
            throw new IllegalArgumentException(key + " 必须是整数");
        long n;
        try { n = Long.parseLong(value.getAsString()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(key + " 数字溢出"); }
        if (n < min || n > max) throw new IllegalArgumentException(key + " 必须为 " + min + "～" + max);
        return n;
    }
}
