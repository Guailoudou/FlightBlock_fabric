package dev.flightblock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.flightblock.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @WrapMethod(method = "setBlockState")
    private BlockState flightblock$change(BlockPos pos, BlockState next, int flags, Operation<BlockState> original) {
        LevelChunk chunk = (LevelChunk)(Object)this;
        FlightBlock mod = FlightBlock.INSTANCE;
        WorldState.Anchor anchor = mod != null && chunk.getLevel() instanceof ServerLevel ? mod.at(chunk.getLevel(), pos) : null;
        BlockState old = original.call(pos, next, flags);
        if (old != null && anchor != null && !next.is(Blocks.TARGET)) mod.invalidate(anchor);
        return old;
    }
}
