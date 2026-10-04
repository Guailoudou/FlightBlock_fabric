package dev.flightblock;

import net.minecraft.server.level.ServerPlayer;

/** Ability ownership differs between the loaders; gameplay uses the same lifecycle. */
public interface FlightAccess {
    void grant(ServerPlayer player);
    boolean revoke(ServerPlayer player);
    void save(ServerPlayer player, Runnable save);
}
