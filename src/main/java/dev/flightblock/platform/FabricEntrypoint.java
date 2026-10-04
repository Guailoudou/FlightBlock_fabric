package dev.flightblock.platform;

import dev.flightblock.FlightBlock;
import dev.flightblock.PalFlightAccess;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/** Fabric hooks kept separate from Minecraft gameplay and persistence. */
public final class FabricEntrypoint implements ModInitializer {
    @Override public void onInitialize() {
        FlightBlock mod = new FlightBlock();
        mod.initialize(FabricLoader.getInstance().getConfigDir(), new PalFlightAccess());
        ServerLifecycleEvents.SERVER_STARTED.register(mod::started);
        ServerLifecycleEvents.SERVER_STOPPING.register(mod::stopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(mod::stopped);
        ServerTickEvents.END_SERVER_TICK.register(mod::tick);
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> mod.chunkLoaded(level, chunk));
        ServerChunkEvents.CHUNK_UNLOAD.register(mod::chunkUnloaded);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> mod.useBlock(player, level, hand, hit.getBlockPos()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> mod.refresh(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> mod.flight.clear(handler.player, false));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damage) -> {
            if (entity instanceof ServerPlayer player) mod.flight.clear(player, false);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> { mod.flight.clear(old, false); mod.refresh(player); });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> mod.flight.clear(player, true));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> mod.registerCommands(dispatcher));
    }
}
