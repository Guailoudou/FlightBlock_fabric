package dev.flightblock;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;

public final class FlightRecipes {
    // This context exists only while a real workbench computes or takes a result.
    public static final ThreadLocal<Boolean> WORKBENCH = ThreadLocal.withInitial(() -> false);
    private FlightRecipes() {}
    public static int level(ItemStackTemplate result) {
        // RecipeManager calls isSpecial before item defaults are bound in 26.3.
        // The marker is explicit recipe data, so never consult the item prototype.
        CustomData data = result.components().get(DataComponentMap.EMPTY, DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getIntOr("flightblock_recipe", 0);
    }
    public static boolean matches(CraftingInput input, int level) {
        if (input.width() != 3 || input.height() != 3 || level < 1 || level > 3) return false;
        Item[] pattern = switch (level) {
            case 1 -> new Item[]{Items.FEATHER, Items.GOLD_INGOT, Items.FEATHER,
                Items.GOLD_INGOT, Items.TARGET, Items.GOLD_INGOT, Items.FEATHER, Items.GOLD_INGOT, Items.FEATHER};
            case 2 -> new Item[]{Items.FEATHER, Items.DIAMOND, Items.FEATHER,
                Items.DIAMOND, Items.TARGET, Items.DIAMOND, Items.FEATHER, Items.DIAMOND, Items.FEATHER};
            default -> new Item[]{Items.DIAMOND, Items.NETHER_STAR, Items.DIAMOND,
                Items.NETHER_STAR, Items.TARGET, Items.NETHER_STAR, Items.DIAMOND, Items.NETHERITE_INGOT, Items.DIAMOND};
        };
        for (int i = 0; i < 9; i++) if (!input.getItem(i).is(pattern[i])) return false;
        ItemStack center = input.getItem(4);
        return Rules.acceptsCenter(level, FlightItems.marked(center), FlightItems.read(center));
    }
    public static int match(CraftingInput input) {
        for (int level = 1; level <= 3; level++) if (matches(input, level)) return level;
        return 0;
    }
}
