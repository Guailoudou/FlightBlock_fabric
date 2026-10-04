package dev.flightblock.mixin;

import dev.flightblock.FlightBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public abstract class PistonMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void flightblock$immovable(BlockState state, Level level, BlockPos pos, Direction push, boolean destroy, Direction from, CallbackInfoReturnable<Boolean> cir) {
        if (FlightBlock.INSTANCE != null && FlightBlock.INSTANCE.at(level, pos) != null) cir.setReturnValue(false);
    }
}
