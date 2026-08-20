package dev.gkissel.forgeweave.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.ItemLike;

import net.neoforged.neoforge.common.Tags;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SlimeColour;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #649 (parity audit T57): the match half of upstream's
 * {@code recipes/gadgets/slimesling/fallback.json}, a {@code tconstruct:shaped_fallback} of the
 * sling shape over the generic {@code slimeball}/{@code blockSlimeCongealed} ore-dict entries. Its
 * {@code ignore} list pairs each of the five pure colours' ball with its congealed block at
 * {@code need: 4} -- with exactly four slime ingredients in the shape, that refuses precisely the
 * grids where all four are one colour, which that colour's own recipe crafts. Pink has no entry of
 * its own in the ignore list, so an all-pink grid falls through here too. The output is always the
 * pink sling ({@code data: 5}).
 */
class MixedSlimeSlingRecipeTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // `c:slimeballs` is a datapack tag; bind it by hand as MixedSlimeBlockRecipeTest does.
        List<Holder<Item>> slimeballs = new ArrayList<>();
        slimeballs.add(BuiltInRegistries.ITEM.wrapAsHolder(Items.SLIME_BALL));
        for (SlimeColour colour : SlimeColour.values()) {
            if (colour != SlimeColour.GREEN) {
                slimeballs.add(BuiltInRegistries.ITEM.wrapAsHolder(ball(colour).asItem()));
            }
        }
        BuiltInRegistries.ITEM.bindTags(Map.of(Tags.Items.SLIMEBALLS, slimeballs));
    }

    private final MixedSlimeSlingRecipe recipe = new MixedSlimeSlingRecipe(CraftingBookCategory.EQUIPMENT);

    @Test
    void mixedColoursInTheSlingShapeMatchAndCraftThePinkSling() {
        CraftingInput mixed = sling(congealed(SlimeColour.GREEN),
                ball(SlimeColour.BLUE), Items.SLIME_BALL, ball(SlimeColour.MAGMA));
        assertTrue(recipe.matches(mixed, null));
        assertTrue(recipe.assemble(mixed, null)
                .is(ForgeweaveItems.slimeSling(SlimeColour.PINK).get()), "the fallback's output is the pink sling");
    }

    /** Upstream's {@code ignore} list: a single-colour grid belongs to that colour's own recipe. */
    @Test
    void aPureColourDoesNotMatch() {
        for (SlimeColour colour : SlimeColour.values()) {
            if (colour == SlimeColour.PINK) {
                continue;
            }
            ItemLike ball = ball(colour);
            assertFalse(recipe.matches(sling(congealed(colour), ball, ball, ball), null),
                    colour + " should be left to its own recipe");
        }
    }

    /** Pink is absent from upstream's ignore list: an all-pink grid is the fallback's own. */
    @Test
    void allPinkStillMatches() {
        ItemLike ball = ball(SlimeColour.PINK);
        assertTrue(recipe.matches(sling(congealed(SlimeColour.PINK), ball, ball, ball), null));
    }

    @Test
    void anythingOffTheSlingShapeDoesNotMatch() {
        // A non-slime stand-in for the congealed block.
        assertFalse(recipe.matches(sling(Items.CLAY, ball(SlimeColour.BLUE), Items.SLIME_BALL,
                ball(SlimeColour.MAGMA)), null), "no congealed slime block");
        // A ball where the shape has a hole.
        assertFalse(recipe.matches(grid(
                Items.STRING, congealed(SlimeColour.GREEN), Items.STRING,
                ball(SlimeColour.BLUE), Items.SLIME_BALL, ball(SlimeColour.MAGMA),
                null, Items.SLIME_BALL, null), null), "an extra ball in the middle");
        // The 2x2 inventory grid cannot hold the shape at all.
        assertFalse(recipe.matches(CraftingInput.of(2, 2, List.of(
                new ItemStack(Items.STRING), new ItemStack(Items.STRING),
                new ItemStack(Items.SLIME_BALL), new ItemStack(Items.SLIME_BALL))), null), "a 2x2 grid");
    }

    private static ItemLike ball(SlimeColour colour) {
        return ForgeweaveItems.slimeBall(colour);
    }

    private static ItemLike congealed(SlimeColour colour) {
        return ForgeweaveBlocks.slimeFamily(colour).congealed().get();
    }

    /** The sling shape: {@code SCS / B B / _B_} with the given congealed centre and three balls. */
    private static CraftingInput sling(ItemLike congealed, ItemLike left, ItemLike right, ItemLike bottom) {
        return grid(
                Items.STRING, congealed, Items.STRING,
                left, null, right,
                null, bottom, null);
    }

    private static CraftingInput grid(ItemLike... items) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemLike item : items) {
            stacks.add(item == null ? ItemStack.EMPTY : new ItemStack(item));
        }
        return CraftingInput.of(3, 3, stacks);
    }
}
