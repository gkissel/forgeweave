package dev.gkissel.forgeweave.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
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
            Codec.STRING.optionalFieldOf("group", "").forGetter(GravelFlintRecipe::getGroup),
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(GravelFlintRecipe::category),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(GravelFlintRecipe::result),
            Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap(GravelFlintRecipe::toIngredientList, DataResult::success)
                    .forGetter(GravelFlintRecipe::getIngredients))
            .apply(instance, GravelFlintRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GravelFlintRecipe> STREAM_CODEC =
            StreamCodec.of(GravelFlintRecipe::toNetwork, GravelFlintRecipe::fromNetwork);

    /** {@link RetexturedShapedRecipe#result}'s reasoning: the superclass's own field is private. */
    private final ItemStack result;

    public GravelFlintRecipe(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
        this.result = result;
    }

    private ItemStack result() {
        return result;
    }

    private static DataResult<NonNullList<Ingredient>> toIngredientList(java.util.List<Ingredient> ingredients) {
        if (ingredients.isEmpty()) {
            return DataResult.error(() -> "No ingredients for shapeless recipe");
        }
        return DataResult.success(NonNullList.of(Ingredient.EMPTY, ingredients.toArray(Ingredient[]::new)));
    }

    private static GravelFlintRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
        String group = buf.readUtf();
        CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
        int size = buf.readVarInt();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
        ingredients.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
        ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
        return new GravelFlintRecipe(group, category, result, ingredients);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buf, GravelFlintRecipe recipe) {
        buf.writeUtf(recipe.getGroup());
        buf.writeEnum(recipe.category());
        buf.writeVarInt(recipe.getIngredients().size());
        for (Ingredient ingredient : recipe.getIngredients()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
        }
        ItemStack.STREAM_CODEC.encode(buf, recipe.result());
    }

    /** Upstream's config check, run every match instead of baked into the datapack load. */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return ForgeweaveConfig.ADD_FLINT_RECIPE.get() && super.matches(input, level);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ForgeweaveRecipeSerializers.GRAVEL_FLINT.get();
    }
}
