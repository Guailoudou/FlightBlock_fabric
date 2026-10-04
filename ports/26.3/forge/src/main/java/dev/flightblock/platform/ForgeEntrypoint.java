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
import net.minecraftforge.event.TickEvent;

@Mod("flightblock")
public final class ForgeEntrypoint {
    public ForgeEntrypoint() {
        FlightBlock mod = new FlightBlock();
        mod.initialize(FMLPaths.CONFIGDIR.get(), new NativeFlightAccess());
        ServerStartedEvent.BUS.addListener(event -> mod.started(event.getServer()));
        ServerStoppingEvent.BUS.addListener(event -> mod.stopping(event.getServer()));
        ServerStoppedEvent.BUS.addListener(event -> mod.stopped(event.getServer()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> mod.tick(event.server()));
        RegisterCommandsEvent.BUS.addListener(event -> mod.registerCommands(event.getDispatcher()));
        ChunkEvent.Load.BUS.addListener(event -> { if (event.getLevel() instanceof ServerLevel level) mod.chunkLoaded(level, event.getChunk()); });
        ChunkEvent.Unload.BUS.addListener(event -> { if (event.getLevel() instanceof ServerLevel level) mod.chunkUnloaded(level, event.getChunk()); });
        PlayerInteractEvent.RightClickBlock.BUS.addListener(event -> { var result = mod.useBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos()); if (result != InteractionResult.PASS) { event.setCancellationResult(result); event.setCanceled(true); } });
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> { if (event.getEntity() instanceof ServerPlayer player) mod.refresh(player); });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> { if (event.getEntity() instanceof ServerPlayer player) mod.flight.clear(player, false); });
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(event -> { if (event.getEntity() instanceof ServerPlayer player) { mod.flight.clear(player, false); mod.refresh(player); } });
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(event -> { if (event.getEntity() instanceof ServerPlayer player) mod.flight.clear(player, true); });
        LivingDeathEvent.BUS.addListener(event -> { if (event.getEntity() instanceof ServerPlayer player) mod.flight.clear(player, false); });
    }
}
