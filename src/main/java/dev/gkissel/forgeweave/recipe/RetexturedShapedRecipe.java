package dev.gkissel.forgeweave.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * A shaped recipe that also copies whichever {@link BlockItem} ingredient was used into the
 * result's {@link ForgeweaveDataComponents#TEXTURE} component (issue #43: a table station retains
 * the wood it was crafted from). Modern Tinkers' Construct does this with a shaped-recipe subclass
 * that copies the input block onto the result's components; that real implementation lives in
 * Mantle, which is not in the 1.20.1 reference clone (see {@code PartBuilderBlock} javadoc), so this
 * is a fresh implementation of the same idea. No NOTICE.md row: nothing here is copied from either
 * reference clone.
 *
 * <p>It <em>extends</em> {@link ShapedRecipe} rather than reimplementing {@code CraftingRecipe}
 * (issue #68 fix 7). The earlier standalone implementation matched fine in a crafting table, but
 * nothing outside the recipe manager could tell it was shaped: recipe viewers type-test for {@code
 * ShapedRecipe} to get a width and height, and JEI, finding none, laid the Stencil Table out as a
 * shapeless left-to-right row -- blank pattern <em>beside</em> planks, where the recipe actually
 * wants upstream's pattern-<em>above</em>-planks column. A maintainer copying the displayed
 * arrangement into a crafting table therefore got nothing. Subclassing hands every consumer
 * (viewers, the recipe book, the recipe-transfer [+] button) the same shape the matcher uses. Only
 * {@link #getSerializer()} and {@link #assemble} differ from a plain shaped recipe; {@code
 * gametest.RecipeShapeGameTests} keeps display and reality in agreement.
 *
 * <p>{@link #result} duplicates the superclass's own (package-private) result field, purely so
 * {@link #CODEC} has something to read it back from.
 */
public class RetexturedShapedRecipe extends ShapedRecipe {
    public static final MapCodec<RetexturedShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(RetexturedShapedRecipe::getGroup),
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(RetexturedShapedRecipe::category),
            ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(RetexturedShapedRecipe::result))
            .apply(instance, RetexturedShapedRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RetexturedShapedRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RetexturedShapedRecipe::getGroup,
            CraftingBookCategory.STREAM_CODEC, RetexturedShapedRecipe::category,
            ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
            ItemStack.STREAM_CODEC, RetexturedShapedRecipe::result,
            RetexturedShapedRecipe::new);

    private final ItemStack result;

    public RetexturedShapedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result) {
        super(group, category, pattern, result);
        this.result = result;
    }

    private ItemStack result() {
        return result;
    }

    @Override
    public RecipeSerializer<? extends RetexturedShapedRecipe> getSerializer() {
        return ForgeweaveRecipeSerializers.RETEXTURED_SHAPED.get();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack crafted = result.copy();
        for (int i = 0; i < input.size(); i++) {
            ItemStack ingredient = input.getItem(i);
            if (!ingredient.isEmpty() && ingredient.getItem() instanceof BlockItem blockItem) {
                crafted.set(ForgeweaveDataComponents.TEXTURE.get(), BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()));
                break;
            }
        }
        return crafted;
    }
}
