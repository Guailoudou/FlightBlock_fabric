package dev.flightblock;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

/** 1.21.11 stores a recipe result as an ItemStack, before template APIs existed. */
public final class RecipeMarkerCheck21 {
    public static void run() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (int tier = 1; tier <= 3; tier++) {
            ItemStack result = new ItemStack(Items.TARGET);
            CompoundTag tag = new CompoundTag();
            tag.putInt("flightblock_recipe", tier);
            result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            if (FlightRecipes.level(result) != tier) throw new AssertionError("Recipe marker lost");
        }
        ItemStack ordinary = new ItemStack(Items.TARGET);
        if (FlightRecipes.level(ordinary) != 0) throw new AssertionError("Ordinary recipe misidentified");
        ordinary.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
        if (FlightRecipes.level(ordinary) != 0) throw new AssertionError("Unrelated data misidentified");
        System.out.println("FLIGHTBLOCK_RECIPE_MARKER_OK: 5 checks passed.");
    }
}
