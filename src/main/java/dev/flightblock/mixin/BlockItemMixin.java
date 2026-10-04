package dev.flightblock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.flightblock.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @WrapMethod(method = "placeBlock")
    private boolean flightblock$place(BlockPlaceContext ctx, BlockState state, Operation<Boolean> original) {
        FlightBlock mod = FlightBlock.INSTANCE;
        if (!(ctx.getLevel() instanceof ServerLevel level) || mod == null || mod.state == null) return original.call(ctx, state);
        ItemStack snapshot = ctx.getItemInHand().copy();
        if (!mod.canPlace(ctx.getItemInHand(), level, ctx.getClickedPos(), ctx.getPlayer() instanceof ServerPlayer p ? p : null)) return false;
        boolean success = original.call(ctx, state);
        if (success) mod.placed(snapshot, level, ctx.getClickedPos());
        return success;
    }
}
