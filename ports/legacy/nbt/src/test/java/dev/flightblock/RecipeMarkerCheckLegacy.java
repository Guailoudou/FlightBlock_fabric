package dev.flightblock;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;

/** Vanilla shaped serializers use a namespaced group to identify our old recipes. */
public final class RecipeMarkerCheckLegacy {
    public static void run() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Config config = Config.defaults();
        try {
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
            for (int tier = 1; tier <= 3; tier++) {
                try (var stream = RecipeMarkerCheckLegacy.class.getResourceAsStream("/data/flightblock/recipes/level_" + tier + ".json")) {
                    var json = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    var recipe = (ShapedRecipe)Recipe.CODEC.parse(ops, json).getOrThrow(false, error -> { throw new AssertionError(error); });
                    if (FlightRecipes.level(recipe.getGroup()) != tier) throw new AssertionError("Recipe tier lost");
                }
                var state = new Rules.ItemState(tier, null, 0, 0);
                ItemStack item = FlightItems.create(state, config);
                if (!state.equals(FlightItems.read(item)) || !item.hasFoil()) throw new AssertionError("Legacy item data/glint lost");
            }
            ItemStack ordinary = new ItemStack(Items.TARGET);
            if (FlightItems.marked(ordinary) || ordinary.hasTag()) throw new AssertionError("Ordinary item modified or misidentified");
            if (FlightRecipes.level("ordinary") != 0) throw new AssertionError("Ordinary recipe misidentified");
            System.out.println("FLIGHTBLOCK_LEGACY_RECIPE_OK: recipes, NBT and item glint passed.");
        } catch (Exception e) { throw new AssertionError("Legacy recipe check failed", e); }
    }
}
