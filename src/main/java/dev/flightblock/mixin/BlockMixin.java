package dev.flightblock.mixin;

import dev.flightblock.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.List;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "playerDestroy", at = @At("HEAD"), cancellable = true)
    private void flightblock$mine(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, BlockEntity entity, ItemStack tool, CallbackInfo ci) {
        WorldState.Anchor a = FlightBlock.MINING.get();
        if (a != null && a.position() == pos.asLong() && a.dimension().equals(FlightBlock.dimension(level))) {
            if (!a.item().expired(FlightBlock.INSTANCE.clock.millis()))
                Block.popResource(level, pos, FlightItems.create(a.item(), FlightBlock.INSTANCE.config));
            ci.cancel();
        }
    }
    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private static void flightblock$noVanillaDrop(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemInstance tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        FlightBlock mod = FlightBlock.INSTANCE;
        if (mod != null && mod.at(level, pos) != null) cir.setReturnValue(List.of());
    }
}
