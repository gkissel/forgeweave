package dev.gkissel.forgeweave.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * The energized tank's crafting recipe, behind {@link ForgeweaveConfig#ENERGIZED_TANK} (issue #972,
 * M8 D-M8-11). A plain shaped recipe with one line changed, exactly as
 * {@link GravelFlintRecipe} is a plain shapeless one: the config is read at match time rather than
 * as a datapack load condition, so a pack operator flipping the toggle needs no restart and no
 * reload. See {@link ForgeweaveConfig#ADD_FLINT_RECIPE} for the full reasoning, which is this
 * option's too.
 *
 * <p>It <em>extends</em> {@link ShapedRecipe} for {@link RetexturedShapedRecipe}'s reason: every
 * other consumer -- the recipe book, JEI, the recipe-transfer button, the matcher itself -- gets an
 * ordinary shaped recipe with a real width and height for free.
 *
 * <p>{@link #result} duplicates the superclass's own private field, purely so {@link #CODEC} has
 * something to read it back from.
 */
public class EnergizedTankRecipe extends ShapedRecipe {
    public static final MapCodec<EnergizedTankRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(EnergizedTankRecipe::getGroup),
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                    .forGetter(EnergizedTankRecipe::category),
            ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(EnergizedTankRecipe::result))
            .apply(instance, EnergizedTankRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnergizedTankRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, EnergizedTankRecipe::getGroup,
            CraftingBookCategory.STREAM_CODEC, EnergizedTankRecipe::category,
            ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
            ItemStack.STREAM_CODEC, EnergizedTankRecipe::result,
            EnergizedTankRecipe::new);

    private final ItemStack result;

    public EnergizedTankRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
            ItemStack result) {
        super(group, category, pattern, result);
        this.result = result;
    }

    private ItemStack result() {
        return result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.ENERGIZED_TANK) && super.matches(input, level);
    }

    @Override
    public RecipeSerializer<? extends EnergizedTankRecipe> getSerializer() {
        return ForgeweaveRecipeSerializers.ENERGIZED_TANK.get();
    }

    /** Serializer for {@link EnergizedTankRecipe}; carries no data of its own beyond the recipe. */
    public static class Serializer implements RecipeSerializer<EnergizedTankRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public MapCodec<EnergizedTankRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, EnergizedTankRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
