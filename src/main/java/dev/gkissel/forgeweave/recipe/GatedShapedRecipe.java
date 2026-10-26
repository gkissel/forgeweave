package dev.gkissel.forgeweave.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.menu.ContentFamilies;

/**
 * A vanilla shaped recipe that stops matching while its result's content family is off. The
 * Slimesling is the one family item a crafting table makes, so this is how the {@code gadgets}
 * toggle reaches it: read at match time, so flipping the config needs no reload, the same as every
 * station gate in {@link ContentFamilies}. The JSON is a plain {@code minecraft:crafting_shaped}
 * body under another type.
 */
public class GatedShapedRecipe extends ShapedRecipe {

    public static final MapCodec<GatedShapedRecipe> CODEC =
            ShapedRecipe.Serializer.CODEC.xmap(GatedShapedRecipe::new, recipe -> recipe);
    public static final StreamCodec<RegistryFriendlyByteBuf, GatedShapedRecipe> STREAM_CODEC =
            ShapedRecipe.Serializer.STREAM_CODEC.map(GatedShapedRecipe::new, recipe -> recipe);

    public GatedShapedRecipe(ShapedRecipe recipe) {
        // ponytail: getResultItem ignores its registries argument for a shaped recipe.
        super(recipe.getGroup(), recipe.category(), recipe.pattern,
                recipe.getResultItem(RegistryAccess.EMPTY), recipe.showNotification());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return ContentFamilies.itemEnabled(getResultItem(RegistryAccess.EMPTY)) && super.matches(input, level);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ForgeweaveRecipeSerializers.GATED_SHAPED.get();
    }

    /** Serializer for {@link GatedShapedRecipe}. */
    public static final class Serializer implements RecipeSerializer<GatedShapedRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public MapCodec<GatedShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, GatedShapedRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
