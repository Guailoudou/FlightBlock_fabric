package dev.flightblock.mixin;

import dev.flightblock.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMixin {
    @Inject(method = "broadcastFullState", at = @At("HEAD"))
    private void flightblock$open(CallbackInfo ci) {
        FlightBlock mod = FlightBlock.INSTANCE;
        if (mod == null || mod.server == null || !mod.server.isSameThread()) return;
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
        for (Slot slot : menu.slots) FlightItems.refresh(slot.getItem(), mod.config);
    }
    @Inject(method = "clicked", at = @At("TAIL"))
    private void flightblock$click(int slot, int button, ContainerInput type, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer p && FlightBlock.INSTANCE != null) FlightBlock.INSTANCE.refresh(p);
    }
}
