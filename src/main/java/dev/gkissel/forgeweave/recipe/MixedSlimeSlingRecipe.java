package dev.gkissel.forgeweave.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.common.Tags;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SlimeColour;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The Slimesling shape over mixed slime colours into the pink sling (issue #649, parity audit T57).
 * Upstream 1.12's {@code recipes/gadgets/slimesling/fallback.json} is a
 * {@code tconstruct:shaped_fallback}: the sling pattern ({@code SCS / B B / _B_}) over the generic
 * {@code slimeball} and {@code blockSlimeCongealed} ore-dict entries, whose {@code ignore} list
 * pairs each pure colour's ball with its congealed block at {@code need: 4} -- with exactly four
 * slime ingredients in the shape, that refuses precisely the grids where all four are one colour
 * (that colour's own recipe crafts those) and yields the pink sling ({@code data: 5}) for every
 * other combination. Pink itself has no entry in the ignore list, so an all-pink grid is the
 * fallback's own too (NOTICE.md).
 *
 * <p>Like {@link MixedSlimeBlockRecipe}, this ports the fallback as a {@link CustomRecipe} with the
 * shape checked by hand rather than as a data-carrying recipe type: it has no data of its own, and
 * the pure-colour "ignore" cannot be said in a vanilla shaped recipe.
 */
public class MixedSlimeSlingRecipe extends CustomRecipe {

    public MixedSlimeSlingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }
        // The sling shape: string, congealed, string / ball, empty, ball / empty, ball, empty.
        if (!input.getItem(0).is(Items.STRING) || !input.getItem(2).is(Items.STRING)
                || !input.getItem(4).isEmpty() || !input.getItem(6).isEmpty() || !input.getItem(8).isEmpty()) {
            return false;
        }
        SlimeColour congealed = congealedColour(input.getItem(1));
        if (congealed == null) {
            return false;
        }
        // Upstream's ignore list covers the five colours with a recipe of their own; pink is not in
        // it, so an all-pink grid is this recipe's to craft.
        boolean pure = congealed != SlimeColour.PINK;
        for (int slot : new int[] { 3, 5, 7 }) {
            ItemStack ball = input.getItem(slot);
            if (!ball.is(Tags.Items.SLIMEBALLS)) {
                return false;
            }
            // A modded slime ball of no Forgeweave colour never makes a pure grid, exactly as a
            // foreign `slimeball` ore-dict entry never trips upstream's ignore list.
            pure &= ballColour(ball) == congealed;
        }
        return !pure;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return getResultItem(registries);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(ForgeweaveItems.slimeSling(SlimeColour.PINK).get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ForgeweaveRecipeSerializers.MIXED_SLIME_SLING.get();
    }

    /** Which colour's congealed slime block this is, or null for anything else. */
    private static SlimeColour congealedColour(ItemStack stack) {
        for (ForgeweaveBlocks.SlimeFamily family : ForgeweaveBlocks.slimeFamilies()) {
            if (stack.is(family.congealed().get().asItem())) {
                return family.colour();
            }
        }
        return null;
    }

    /** Which colour's slime ball this is (green is vanilla's), or null for a foreign one. */
    private static SlimeColour ballColour(ItemStack stack) {
        for (SlimeColour colour : SlimeColour.values()) {
            if (stack.is(ForgeweaveItems.slimeBall(colour).asItem())) {
                return colour;
            }
        }
        return null;
    }
}
