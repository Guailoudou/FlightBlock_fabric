package dev.flightblock;

import dev.flightblock.mixin.ChunkMapAccessor;
import dev.flightblock.mixin.TrackedEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.*;

/** Ownership and cleanup shared by display entities and the legacy outline helpers. */
public final class GlowTracker {
    public static final String TAG = "flightblock:glow_v1";
    private static final String ANCHOR = "flightblock:anchor:";
    private GlowTracker() {}

    public static void mark(Entity entity, String id) {
        entity.addTag(TAG);
        entity.addTag(ANCHOR + id);
    }
    static String anchorId(Set<String> tags) {
        if (!tags.contains(TAG)) return null;
        String id = null;
        for (String tag : tags) if (tag.startsWith(ANCHOR)) {
            if (id != null) return null;
            id = tag.substring(ANCHOR.length());
        }
        return id;
    }
    static boolean shouldKeep(WorldState.Anchor anchor, boolean bound, long now, String dimension, long position) {
        return anchor != null && bound && anchor.valid(now)
            && anchor.dimension().equals(dimension) && anchor.position() == position;
    }
    public static boolean valid(FlightBlock mod, ServerLevel world, Entity entity, Map<String, ? extends Entity> active) {
        if (mod == null || mod.state == null || mod.server != world.getServer() || entity.isRemoved()) return false;
        String id = anchorId(entity.getTags());
        WorldState.Anchor anchor = mod.state.byId.get(id);
        if (active.get(id) != entity || world.getEntity(entity.getId()) != entity
            || !shouldKeep(anchor, mod.flight.owner(id) != null, mod.clock.millis(), FlightBlock.dimension(world), entity.blockPosition().asLong())) return false;
        LevelChunk chunk = world.getChunkSource().getChunkNow(entity.blockPosition().getX() >> 4, entity.blockPosition().getZ() >> 4);
        return chunk != null && chunk.getBlockState(entity.blockPosition()).is(Blocks.TARGET);
    }
    public static void remove(ServerLevel world, Entity entity) {
        try {
            if (world.getScoreboard().getPlayersTeam(entity.getStringUUID()) != null)
                world.getScoreboard().removePlayerFromTeam(entity.getStringUUID());
            entity.discard();
        } finally {
            // Also evict a stranded network tracker when the level callback is already detached.
            world.getChunkSource().removeEntity(entity);
        }
    }
    public static String audit(FlightBlock mod, Map<String, ? extends Entity> active, boolean repair) {
        int loaded = 0, tracked = 0, stale = 0, removed = 0, unmarked = 0;
        for (ServerLevel world : mod.server.getAllLevels()) {
            Set<Entity> owned = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
            for (Entity entity : world.getAllEntities()) if (entity.getTags().contains(TAG)) {
                loaded++;
                owned.add(entity);
            }
            for (Object entry : ((ChunkMapAccessor)world.getChunkSource().chunkMap).flightblock$trackedEntities().values()) {
                Entity entity = ((TrackedEntityAccessor)entry).flightblock$entity();
                if (entity.getTags().contains(TAG)) { tracked++; owned.add(entity); }
                else if (entity.getType().toString().contains("block_display")) unmarked++;
            }
            for (Entity entity : owned) if (!valid(mod, world, entity, active)) {
                stale++;
                if (repair) {
                    remove(world, entity);
                    active.values().removeIf(value -> value == entity);
                    removed++;
                }
            }
        }
        return "发光记录 " + active.size() + "；已加载辅助实体 " + loaded + "；同步辅助实体 " + tracked
            + "；异常 " + stale + "；未标记展示实体 " + unmarked + "（不自动处理）；已清理 " + removed + "。";
    }
}
