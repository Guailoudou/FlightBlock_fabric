package dev.flightblock.platform;

import dev.flightblock.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("flightblock")
public final class NeoForgeEntrypoint {
    public NeoForgeEntrypoint() {
        FlightBlock mod = new FlightBlock();
        mod.initialize(FMLPaths.CONFIGDIR.get(), new NativeFlightAccess());
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> mod.started(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> mod.stopping(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> mod.stopped(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> mod.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> mod.registerCommands(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> { if (event.getLevel() instanceof ServerLevel level) mod.chunkLoaded(level, event.getChunk()); });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> { if (event.getLevel() instanceof ServerLevel level) mod.chunkUnloaded(level, event.getChunk()); });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> { var result = mod.useBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos()); if (result != InteractionResult.PASS) { event.setCancellationResult(result); event.setCanceled(true); } });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> { if (event.getEntity() instanceof ServerPlayer player) mod.refresh(player); });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> { if (event.getEntity() instanceof ServerPlayer player) mod.flight.clear(player, false); });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> { if (event.getEntity() instanceof ServerPlayer player) { mod.flight.clear(player, false); mod.refresh(player); } });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> { if (event.getEntity() instanceof ServerPlayer player) mod.flight.clear(player, true); });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> { if (event.getEntity() instanceof ServerPlayer player) mod.flight.clear(player, false); });
    }
}
