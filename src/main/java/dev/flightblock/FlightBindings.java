package dev.flightblock;

import java.util.*;

/** Server-thread-only bindings: each block has at most one user. */
public final class FlightBindings {
    private final Map<UUID, Set<String>> byPlayer = new HashMap<>();
    private final Map<String, UUID> owners = new HashMap<>();
    public Set<String> get(UUID player) { return Set.copyOf(byPlayer.getOrDefault(player, Set.of())); }
    public boolean bind(UUID player, String id) {
        UUID owner = owners.get(id);
        if (owner != null && !owner.equals(player)) return false;
        owners.put(id, player);
        byPlayer.computeIfAbsent(player, k -> new HashSet<>()).add(id);
        return true;
    }
    public void clear(UUID player) {
        Set<String> ids = byPlayer.remove(player);
        if (ids != null) for (String id : ids) owners.remove(id, player);
    }
    public boolean unbind(UUID player, String id) {
        if (!player.equals(owners.get(id))) return false;
        forget(id);
        return true;
    }
    public void forget(String id) {
        UUID owner = owners.remove(id);
        if (owner == null) return;
        Set<String> ids = byPlayer.get(owner);
        ids.remove(id);
        if (ids.isEmpty()) byPlayer.remove(owner);
    }
}
