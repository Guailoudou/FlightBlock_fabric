package dev.flightblock.mixin;

import dev.flightblock.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShapedRecipe.class)
public abstract class ShapedRecipeMixin {
    @Shadow @Final private ItemStackTemplate result;
    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"), cancellable = true)
    private void flightblock$matches(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        int tier = FlightRecipes.level(result);
        if (tier != 0) cir.setReturnValue(FlightRecipes.WORKBENCH.get() && FlightRecipes.matches(input, tier));
    }
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void flightblock$assemble(CraftingInput input, CallbackInfoReturnable<ItemStack> cir) {
        int tier = FlightRecipes.level(result);
        if (tier != 0) cir.setReturnValue(FlightRecipes.WORKBENCH.get() && FlightRecipes.matches(input, tier)
            ? FlightItems.create(new Rules.ItemState(tier, null, 0, 0), FlightBlock.INSTANCE.config) : ItemStack.EMPTY);
    }
    // Vanilla skips special recipes when building the recipe book's learnable list.
    public boolean isSpecial() { return FlightRecipes.level(result) != 0; }
}
