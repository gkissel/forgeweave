package dev.gkissel.forgeweave.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
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

    public static final MapCodec<GatedShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
            ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result))
            .apply(instance, GatedShapedRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, GatedShapedRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
            ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
            ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
            GatedShapedRecipe::new);

    /** The superclass's own result field is private; kept here for {@link #CODEC} and the gate. */
    private final ItemStackTemplate result;

    public GatedShapedRecipe(ShapedRecipe recipe) {
        // ponytail: a shaped recipe's assemble ignores its input and returns a fresh copy of its result.
        this(new Recipe.CommonInfo(recipe.showNotification()),
                new CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group()), recipe.pattern,
                ItemStackTemplate.fromNonEmptyStack(recipe.assemble(CraftingInput.EMPTY)));
    }

    public GatedShapedRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
            ShapedRecipePattern pattern, ItemStackTemplate result) {
        super(commonInfo, bookInfo, pattern, result);
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return ContentFamilies.itemEnabled(result.item().value()) && super.matches(input, level);
    }

    /** The vanilla parent pins its own serializer type; ours encodes this subclass, which is all dispatch needs. */
    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) ForgeweaveRecipeSerializers.GATED_SHAPED.get();
    }

    /** Serializer for {@link GatedShapedRecipe}. */
    public static final class Serializer {
        public static final RecipeSerializer<GatedShapedRecipe> INSTANCE = new RecipeSerializer<>(CODEC, STREAM_CODEC);

        private Serializer() {}
    }
}
