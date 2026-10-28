package dev.gkissel.forgeweave.recipe;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * "3 gravel -&gt; 1 flint", behind {@link ForgeweaveConfig#ADD_FLINT_RECIPE} (parity audit T55,
 * issue #486). Ported from upstream 1.12's {@code recipes/common/flint.json}, which is a plain
 * ore-dict shapeless recipe (three {@code gravel} ore-dict entries -- vanilla gravel is the only
 * item ever registered under it) gated by a {@code tconstruct:is_option_enabled} load-time
 * condition reading {@code Config.gravelFlintRecipe} (NOTICE.md).
 *
 * <p>It <em>extends</em> {@link ShapelessRecipe} rather than reimplementing {@code CraftingRecipe}
 * (the same {@link RetexturedShapedRecipe} reasoning: a plain vanilla shapeless recipe otherwise,
 * needing only its match check changed), and overrides only {@link #matches} to add the config
 * check -- every other consumer (the recipe book, JEI, the crafting-table matcher itself) gets an
 * ordinary shapeless recipe for free. See {@link ForgeweaveConfig#ADD_FLINT_RECIPE} for why that
 * check runs at match time instead of upstream's load-time condition.
 */
public class GravelFlintRecipe extends ShapelessRecipe {
    public static final MapCodec<GravelFlintRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(ShapelessRecipe::result),
            Ingredient.CODEC.listOf(1, ShapedRecipePattern.getMaxWidth() * ShapedRecipePattern.getMaxHeight())
                    .fieldOf("ingredients").forGetter(GravelFlintRecipe::ingredients))
            .apply(instance, GravelFlintRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GravelFlintRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
            ItemStackTemplate.STREAM_CODEC, ShapelessRecipe::result,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), GravelFlintRecipe::ingredients,
            GravelFlintRecipe::new);

    /** The superclass's own ingredient list is private; kept here for {@link #CODEC}. */
    private final List<Ingredient> ingredients;

    public GravelFlintRecipe(String group, CraftingBookCategory category, ItemStackTemplate result, List<Ingredient> ingredients) {
        this(new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(category, group), result, ingredients);
    }

    public GravelFlintRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
            ItemStackTemplate result, List<Ingredient> ingredients) {
        super(commonInfo, bookInfo, result, ingredients);
        this.ingredients = List.copyOf(ingredients);
    }

    private List<Ingredient> ingredients() {
        return ingredients;
    }

    /** Upstream's config check, run every match instead of baked into the datapack load. */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return ForgeweaveConfig.read(ForgeweaveConfig.ADD_FLINT_RECIPE) && super.matches(input, level);
    }

    /** The vanilla parent pins its own serializer type; ours encodes this subclass, which is all dispatch needs. */
    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<ShapelessRecipe> getSerializer() {
        return (RecipeSerializer<ShapelessRecipe>) (RecipeSerializer<?>) ForgeweaveRecipeSerializers.GRAVEL_FLINT.get();
    }
}
