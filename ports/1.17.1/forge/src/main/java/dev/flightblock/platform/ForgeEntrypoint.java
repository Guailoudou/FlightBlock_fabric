package dev.flightblock.platform;

import dev.flightblock.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.server.*;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

@Mod("flightblock")
public final class ForgeEntrypoint {
    public ForgeEntrypoint() {
        FlightBlock mod = new FlightBlock();
        mod.initialize(FMLPaths.CONFIGDIR.get(), new NativeFlightAccess());
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> mod.started(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> mod.stopping(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> mod.stopped(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) -> mod.tick(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> mod.registerCommands(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> { if (event.getLevel() instanceof ServerLevel) mod.chunkLoaded((ServerLevel)event.getLevel(), event.getChunk()); });
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> { if (event.getLevel() instanceof ServerLevel) mod.chunkUnloaded((ServerLevel)event.getLevel(), event.getChunk()); });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> { InteractionResult result = mod.useBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos()); if (result != InteractionResult.PASS) { event.setCancellationResult(result); event.setCanceled(true); } });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> { if (event.getEntity() instanceof ServerPlayer) mod.refresh((ServerPlayer)event.getEntity()); });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> { if (event.getEntity() instanceof ServerPlayer) mod.flight.clear((ServerPlayer)event.getEntity(), false); });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> { if (event.getEntity() instanceof ServerPlayer) { mod.flight.clear((ServerPlayer)event.getEntity(), false); mod.refresh((ServerPlayer)event.getEntity()); } });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> { if (event.getEntity() instanceof ServerPlayer) mod.flight.clear((ServerPlayer)event.getEntity(), true); });
        MinecraftForge.EVENT_BUS.addListener((LivingDeathEvent event) -> { if (event.getEntity() instanceof ServerPlayer) mod.flight.clear((ServerPlayer)event.getEntity(), false); });
    }
}
