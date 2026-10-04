package dev.flightblock;

import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import java.util.List;

/** Container-based input used by optional recipe checks before CraftingInput existed. */
public final class LegacyCrafting {
    private LegacyCrafting() {}
    public static CraftingContainer input(int width, int height, List<ItemStack> stacks) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        };
        TransientCraftingContainer input = new TransientCraftingContainer(menu, width, height);
        for (int i = 0; i < stacks.size(); i++) input.setItem(i, stacks.get(i));
        return input;
    }
}
