package dev.flightblock.mixin;

import dev.flightblock.FlightBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Older Fabric API has no AFTER_DEATH event. */
@Mixin(ServerPlayer.class)
public abstract class DeathMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void flightblock$death(DamageSource damage, CallbackInfo ci) {
        if (FlightBlock.INSTANCE != null) FlightBlock.INSTANCE.flight.clear((ServerPlayer)(Object)this, false);
    }
}
