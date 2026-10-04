package dev.flightblock;

import net.minecraft.server.level.ServerPlayer;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Forge/NeoForge do not load the Fabric PAL library. Track only our own grants. */
public final class NativeFlightAccess implements FlightAccess {
    private final Set<UUID> granted = new HashSet<>();
    @Override public void grant(ServerPlayer player) {
        if (!player.getAbilities().mayfly) {
            granted.add(player.getUUID());
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
    }
    @Override public boolean revoke(ServerPlayer player) {
        if (!granted.remove(player.getUUID())) return false;
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        return true;
    }
    @Override public void save(ServerPlayer player, Runnable save) {
        boolean owned = granted.contains(player.getUUID()) && !player.isCreative() && !player.isSpectator();
        boolean mayfly = player.getAbilities().mayfly, flying = player.getAbilities().flying;
        if (owned) { player.getAbilities().mayfly = false; player.getAbilities().flying = false; }
        try { save.run(); }
        finally { if (owned) { player.getAbilities().mayfly = mayfly; player.getAbilities().flying = flying; } }
    }
}
