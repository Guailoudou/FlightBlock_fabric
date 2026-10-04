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
    private final Map<String, Glow> displays = new HashMap<>();
    public void ensure(ServerLevel level, WorldState.Anchor anchor, long now) {
        if (!anchor.valid(now) || level.getChunkSource().getChunkNow(anchor.pos().getX() >> 4, anchor.pos().getZ() >> 4) == null) return;
        Glow old = displays.get(anchor.id());
        if (old != null && !old.isRemoved()) return;
        Glow glow = new Glow(level, anchor);
        if (level.addFreshEntity(glow)) displays.put(anchor.id(), glow);
    }
    public void remove(String id) {
        Glow glow = displays.remove(id);
        if (glow != null) glow.discard();
    }
    public void clear() {
        displays.values().forEach(Entity::discard);
        displays.clear();
    }
    private static final class Glow extends Display.BlockDisplay {
        Glow(ServerLevel level, WorldState.Anchor anchor) {
            super(EntityTypes.BLOCK_DISPLAY, level);
            CompoundTag data = new CompoundTag();
            data.put("block_state", NbtUtils.writeBlockState(Blocks.TARGET.defaultBlockState()));
            data.putInt("glow_color_override", TextColor.fromLegacyFormat(FlightItems.levelColor(anchor.level())).getValue());
            CompoundTag brightness = new CompoundTag();
            brightness.putInt("block", 15);
            brightness.putInt("sky", 15);
            data.put("brightness", brightness);
            // Slightly larger than the real block, avoiding overlapping surfaces.
            data.put("transformation", Transformation.CODEC.encodeStart(NbtOps.INSTANCE,
                new Transformation(new Vector3f(-.002f), null, new Vector3f(1.004f), null)).getOrThrow());
            readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
            setPos(anchor.pos().getX(), anchor.pos().getY(), anchor.pos().getZ());
            setGlowingTag(true);
        }
        @Override public boolean shouldBeSaved() { return false; }
    }
}
