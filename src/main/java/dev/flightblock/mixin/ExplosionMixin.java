package dev.flightblock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.flightblock.FlightBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.*;
import org.spongepowered.asm.mixin.Mixin;
import java.util.function.BiConsumer;

@Mixin(BlockBehaviour.class)
public abstract class ExplosionMixin {
    @WrapMethod(method = "onExplosionHit")
    private void flightblock$explode(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
                                     BiConsumer<ItemStack, BlockPos> collector, Operation<Void> original) {
        FlightBlock mod = FlightBlock.INSTANCE;
        BiConsumer<ItemStack, BlockPos> drop = mod != null && mod.at(level, pos) != null ? (stack, location) -> { } : collector;
        original.call(state, level, pos, explosion, drop);
    }
}
