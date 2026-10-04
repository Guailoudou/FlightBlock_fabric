package dev.flightblock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.flightblock.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin extends Slot {
    @Shadow @Final private CraftingContainer craftSlots;
    protected ResultSlotMixin(Container c, int slot, int x, int y) { super(c, slot, x, y); }
    @Override public boolean mayPickup(Player player) {
        ItemStack output = getItem();
        if (FlightItems.marked(output)) {
            Rules.ItemState item = FlightItems.read(output);
            return item != null && !item.activated() && player.containerMenu instanceof CraftingMenu
                && FlightRecipes.matches(craftSlots.asCraftInput(), item.level());
        }
        return super.mayPickup(player);
    }
    @WrapMethod(method = "onTake")
    private void flightblock$take(Player player, ItemStack stack, Operation<Void> original) {
        boolean previous = FlightRecipes.WORKBENCH.get();
        FlightRecipes.WORKBENCH.set(player.containerMenu instanceof CraftingMenu);
        try { original.call(player, stack); }
        finally { FlightRecipes.WORKBENCH.set(previous); }
    }
}
