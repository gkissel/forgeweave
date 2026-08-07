package dev.gkissel.forgeweave.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * A shaped recipe that also copies whichever {@link BlockItem} ingredient was used into the
 * result's {@link ForgeweaveDataComponents#TEXTURE} component (issue #43: a table station retains
 * the wood it was crafted from). Modern Tinkers' Construct does this with a shaped-recipe subclass
 * that copies the input block onto the result's components; that real implementation lives in
 * Mantle, which is not in the 1.20.1 reference clone (see {@code PartBuilderBlock} javadoc), so this
 * is a fresh implementation of the same idea -- it duplicates {@code ShapedRecipe}'s fields directly
 * (rather than extending it) because the fields it would need to re-serialize are package-private
 * there. No NOTICE.md row: nothing here is copied from either reference clone.
 */
public class RetexturedShapedRecipe implements CraftingRecipe {
    public static final MapCodec<RetexturedShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(RetexturedShapedRecipe::group),
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(RetexturedShapedRecipe::category),
            ShapedRecipePattern.MAP_CODEC.forGetter(RetexturedShapedRecipe::pattern),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(RetexturedShapedRecipe::result))
            .apply(instance, RetexturedShapedRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RetexturedShapedRecipe> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, RetexturedShapedRecipe::group,
            CraftingBookCategory.STREAM_CODEC, RetexturedShapedRecipe::category,
            ShapedRecipePattern.STREAM_CODEC, RetexturedShapedRecipe::pattern,
            ItemStack.STREAM_CODEC, RetexturedShapedRecipe::result,
            RetexturedShapedRecipe::new);

    private final String group;
    private final CraftingBookCategory category;
    private final ShapedRecipePattern pattern;
    private final ItemStack result;

    public RetexturedShapedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result) {
        this.group = group;
        this.category = category;
        this.pattern = pattern;
        this.result = result;
    }

    private String group() {
        return group;
    }

    private ShapedRecipePattern pattern() {
        return pattern;
    }

    private ItemStack result() {
        return result;
    }

    @Override
    public RecipeSerializer<? extends RetexturedShapedRecipe> getSerializer() {
        return ForgeweaveRecipeSerializers.RETEXTURED_SHAPED.get();
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public CraftingBookCategory category() {
        return category;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return pattern.ingredients();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= pattern.width() && height >= pattern.height();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return pattern.matches(input);
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
