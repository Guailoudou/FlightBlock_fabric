package dev.flightblock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.flightblock.FlightBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void flightblock$logout(ServerPlayer player, CallbackInfo ci) {
        if (FlightBlock.INSTANCE != null) FlightBlock.INSTANCE.flight.clear(player, false);
    }
    @WrapMethod(method = "save")
    private void flightblock$save(ServerPlayer player, Operation<Void> original) {
        FlightBlock mod = FlightBlock.INSTANCE;
        if (mod == null) original.call(player);
        else mod.flight.save(player, () -> original.call(player));
    }
}
