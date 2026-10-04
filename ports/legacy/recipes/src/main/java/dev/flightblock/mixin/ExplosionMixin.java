package dev.flightblock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.flightblock.FlightBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import java.util.List;

/** Older explosions collect loot through BlockBehaviour instead of onExplosionHit. */
@Mixin(BlockBehaviour.class)
public abstract class ExplosionMixin {
    @WrapMethod(method = "getDrops")
    private List<ItemStack> flightblock$explode(BlockState state, LootParams.Builder builder,
                                               Operation<List<ItemStack>> original) {
        FlightBlock mod = FlightBlock.INSTANCE;
        Vec3 origin = builder.getOptionalParameter(LootContextParams.ORIGIN);
        if (mod != null && origin != null && mod.at(builder.getLevel(), BlockPos.containing(origin)) != null)
            return List.of();
        return original.call(state, builder);
    }
}
