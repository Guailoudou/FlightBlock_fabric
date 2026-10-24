package dev.flightblock;

import com.mojang.math.Transformation;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import org.joml.Vector3f;
import java.util.*;

/** Temporary vanilla display entities: no world edits or entity registrations. */
public final class BlockGlow {
    private static final Transformation OUTLINE_TRANSFORM = new Transformation(new Vector3f(-.002f), null, new Vector3f(1.004f), null);
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
        if (old != null && GlowTracker.valid(FlightBlock.INSTANCE, level, old, displays)) return;
        remove(anchor.id());
        Glow glow = new Glow(level, anchor);
        if (level.addFreshEntity(glow)) displays.put(anchor.id(), glow);
    }
    public void remove(String id) {
        Glow glow = displays.remove(id);
        if (glow != null) GlowTracker.remove(glow.world, glow);
    }
    public void clear() {
        for (String id : new ArrayList<>(displays.keySet())) remove(id);
    }
    public String audit(FlightBlock mod, boolean repair) { return GlowTracker.audit(mod, displays, repair); }
    private final class Glow extends Display.BlockDisplay {
        private final ServerLevel world;
        private final String anchorId;
        private final CompoundTag visualData;
        Glow(ServerLevel level, WorldState.Anchor anchor) {
            super(EntityTypes.BLOCK_DISPLAY, level);
            world = level;
            anchorId = anchor.id();
            GlowTracker.mark(this, anchorId);
            CompoundTag data = new CompoundTag();
            data.put("block_state", NbtUtils.writeBlockState(Blocks.TARGET.defaultBlockState()));
            data.putInt("glow_color_override", TextColor.fromLegacyFormat(FlightItems.levelColor(anchor.level())).getValue());
            CompoundTag brightness = new CompoundTag();
            brightness.putInt("block", 15);
            brightness.putInt("sky", 15);
            data.put("brightness", brightness);
            // Slightly larger than the real block, avoiding overlapping surfaces.
            data.put("transformation", Transformation.CODEC.encodeStart(NbtOps.INSTANCE,
                OUTLINE_TRANSFORM).getOrThrow());
            visualData = data;
            applyAppearance(level, data);
            setPos(anchor.pos().getX(), anchor.pos().getY(), anchor.pos().getZ());
            setGlowingTag(true);
            setPermanentlyInvulnerable(true);
            setNoGravity(true);
        }
        @Override public void tick() {
            super.tick();
            if (!GlowTracker.valid(FlightBlock.INSTANCE, world, this, displays)) {
                displays.remove(anchorId, this);
                GlowTracker.remove(world, this);
            } else if (tickCount % 20 == 0) applyAppearance(world, visualData);
        }
        private void applyAppearance(ServerLevel level, CompoundTag data) {
            readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
        }
        @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
        @Override public boolean broadcastToPlayer(net.minecraft.server.level.ServerPlayer player) { return !isRemoved(); }
        @Override public boolean shouldBeSaved() { return false; }
    }
}
