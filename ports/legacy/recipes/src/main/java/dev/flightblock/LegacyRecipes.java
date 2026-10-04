package dev.flightblock;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Decode the packaged vanilla shaped JSON on releases before recipe codecs. */
public final class LegacyRecipes {
    private LegacyRecipes() { }
    public static DataResult<Recipe<?>> parse(DynamicOps<JsonElement> ops, JsonElement json) {
        return DataResult.success(RecipeSerializer.SHAPED_RECIPE.fromJson(
            new ResourceLocation("flightblock", "check"), json.getAsJsonObject()));
    }
}
