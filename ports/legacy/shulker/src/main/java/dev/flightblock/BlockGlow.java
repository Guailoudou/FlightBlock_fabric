package dev.flightblock;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.scores.PlayerTeam;
import java.util.HashMap;
import java.util.Map;

/** Vanilla invisible shulkers provide a block-sized outline before display entities existed. */
public final class BlockGlow {
    private final Map<String, Glow> displays = new HashMap<>();
    public boolean isGlowing(String id) {
        Glow glow = displays.get(id);
        return glow != null && !glow.isRemoved() && glow.isCurrentlyGlowing();
    }
    static boolean shouldGlow(WorldState.Anchor anchor, boolean bound, long now) {
        return bound && anchor.valid(now);
    }
    public void ensure(ServerLevel level, WorldState.Anchor anchor, boolean bound, long now) {
        if (!shouldGlow(anchor, bound, now)) { remove(anchor.id()); return; }
        if (level.getChunkSource().getChunkNow(anchor.pos().getX() >> 4, anchor.pos().getZ() >> 4) == null) return;
        Glow old = displays.get(anchor.id());
        if (old != null && !old.isRemoved()) return;
        remove(anchor.id());
        Glow glow = new Glow(level, anchor);
        if (level.addFreshEntity(glow)) displays.put(anchor.id(), glow);
        else glow.cleanup();
    }
    public void remove(String id) {
        Glow glow = displays.remove(id);
        if (glow != null) { glow.cleanup(); glow.discard(); }
    }
    public void clear() {
        for (String id : new java.util.ArrayList<>(displays.keySet())) remove(id);
    }
    private static final class Glow extends Shulker {
        private final ServerLevel world;
        Glow(ServerLevel level, WorldState.Anchor anchor) {
            super(EntityType.SHULKER, level);
            world = level;
            setPos(anchor.pos().getX() + .5, anchor.pos().getY(), anchor.pos().getZ() + .5);
            setInvisible(true);
            setGlowingTag(true);
            setNoAi(true);
            setNoGravity(true);
            setInvulnerable(true);
            setSilent(true);
            noPhysics = true;
            String teamName = "flightblock_" + anchor.level();
            PlayerTeam team = level.getScoreboard().getPlayerTeam(teamName);
            if (team == null) team = level.getScoreboard().addPlayerTeam(teamName);
            team.setColor(FlightItems.levelColor(anchor.level()));
            team.setCollisionRule(net.minecraft.world.scores.Team.CollisionRule.NEVER);
            level.getScoreboard().addPlayerToTeam(getStringUUID(), team);
        }
        void cleanup() { world.getScoreboard().removePlayerFromTeam(getStringUUID()); }
        // No mob logic, movement, projectiles, opening animation or teleportation.
        @Override public void tick() { tickCount++; }
        @Override public boolean isPickable() { return false; }
        @Override public boolean isPushable() { return false; }
        @Override public boolean canBeCollidedWith() { return false; }
        @Override public boolean shouldBeSaved() { return false; }
    }
}
