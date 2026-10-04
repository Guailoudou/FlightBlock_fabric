package dev.flightblock;

import com.google.gson.JsonObject;
import java.util.*;

/** Java 8 equivalents for the shared sources' immutable collection factories. */
public final class LegacyJava {
    private LegacyJava() { }
    @SafeVarargs public static <T> List<T> list(T... values) {
        List<T> result = new ArrayList<>();
        for (T value : values) result.add(Objects.requireNonNull(value));
        return Collections.unmodifiableList(result);
    }
    @SafeVarargs public static <T> Set<T> set(T... values) { return Collections.unmodifiableSet(new HashSet<>(list(values))); }
    public static <T> Set<T> copySet(Collection<T> values) { return Collections.unmodifiableSet(new HashSet<>(values)); }
    public static Set<String> keys(JsonObject object) {
        Set<String> keys = new HashSet<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : object.entrySet()) keys.add(entry.getKey());
        return keys;
    }
}
