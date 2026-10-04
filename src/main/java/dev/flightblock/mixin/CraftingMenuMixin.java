package dev.flightblock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.flightblock.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @WrapMethod(method = "slotChangedCraftingGrid")
    private static void flightblock$preview(AbstractContainerMenu menu, ServerLevel level, Player player,
                                           CraftingContainer input, ResultContainer result, RecipeHolder<CraftingRecipe> hint, Operation<Void> original) {
        boolean previous = FlightRecipes.WORKBENCH.get();
        FlightRecipes.WORKBENCH.set(menu instanceof CraftingMenu);
        try { original.call(menu, level, player, input, result, hint); }
        finally { FlightRecipes.WORKBENCH.set(previous); }
    }
    @WrapMethod(method = "quickMoveStack")
    private ItemStack flightblock$shift(Player player, int index, Operation<ItemStack> original) {
        CraftingMenu menu = (CraftingMenu)(Object)this;
        if (index == 0 && !menu.getResultSlot().mayPickup(player)) return ItemStack.EMPTY;
        return original.call(player, index);
    }
}
