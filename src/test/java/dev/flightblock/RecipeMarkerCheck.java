package dev.flightblock;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

/** Recreates the pre-binding recipe-loading phase without launching a server. */
public final class RecipeMarkerCheck {
    public static void run() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (Items.TARGET.builtInRegistryHolder().areComponentsBound())
            throw new AssertionError("Regression check must run before item components are bound");
        int checks = 1;
        for (int tier = 1; tier <= 3; tier++) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("flightblock_recipe", tier);
            DataComponentPatch patch = DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build();
            ItemStackTemplate result = new ItemStackTemplate(Items.TARGET, patch);
            try {
                result.get(DataComponents.CUSTOM_DATA);
                throw new AssertionError("Old access path should reproduce the reported crash");
            } catch (NullPointerException expected) {
                if (!"Components not bound yet".equals(expected.getMessage())) throw expected;
            }
            if (FlightRecipes.level(result) != tier) throw new AssertionError("Recipe marker lost before component binding");
            checks += 2;
        }
        ItemStackTemplate ordinary = new ItemStackTemplate(Items.TARGET);
        if (FlightRecipes.level(ordinary) != 0) throw new AssertionError("Ordinary recipe misidentified");
        checks++;
        ItemStackTemplate removed = new ItemStackTemplate(Items.TARGET,
            DataComponentPatch.builder().remove(DataComponents.CUSTOM_DATA).build());
        if (FlightRecipes.level(removed) != 0) throw new AssertionError("Removed marker misidentified");
        checks++;
        ItemStackTemplate unrelated = new ItemStackTemplate(Items.TARGET,
            DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).build());
        if (FlightRecipes.level(unrelated) != 0) throw new AssertionError("Unrelated custom data misidentified");
        checks++;
        System.out.println("FLIGHTBLOCK_RECIPE_MARKER_OK: " + checks + " checks passed before component binding.");
    }
}
