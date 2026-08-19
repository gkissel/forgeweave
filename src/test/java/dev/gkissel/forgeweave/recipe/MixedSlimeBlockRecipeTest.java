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

import net.neoforged.neoforge.common.Tags;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.block.SlimeColour;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #635 (parity audit T57): the match half of upstream's {@code ShapedFallbackRecipe} for slime
 * blocks ({@code TinkerCommons#registerRecipes}) -- a full 3x3 of slime balls matches, and nine of
 * one colour does not, because that colour's own recipe owns the grid. The output side reads the
 * {@code matchVanillaSlimeblock} config and so belongs to a running game, not here.
 */
class MixedSlimeBlockRecipeTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // `c:slimeballs` is a datapack tag, and no datapack is loaded here -- bind it by hand to the
        // six items ForgeweaveItemTagsProvider and NeoForge between them put in it.
        List<Holder<Item>> slimeballs = new ArrayList<>();
        slimeballs.add(BuiltInRegistries.ITEM.wrapAsHolder(Items.SLIME_BALL));
        for (SlimeColour colour : SlimeColour.values()) {
            if (colour != SlimeColour.GREEN) {
                slimeballs.add(BuiltInRegistries.ITEM.wrapAsHolder(ball(colour)));
            }
        }
        BuiltInRegistries.ITEM.bindTags(Map.of(Tags.Items.SLIMEBALLS, slimeballs));
    }

    private final MixedSlimeBlockRecipe recipe = new MixedSlimeBlockRecipe(CraftingBookCategory.BUILDING);

    @Test
    void nineSlimeBallsOfMixedColoursMatch() {
        assertTrue(recipe.matches(grid(
                Items.SLIME_BALL, Items.SLIME_BALL, Items.SLIME_BALL,
                ball(SlimeColour.BLUE), ball(SlimeColour.BLUE), ball(SlimeColour.PURPLE),
                ball(SlimeColour.BLOOD), ball(SlimeColour.MAGMA), ball(SlimeColour.PINK)), null));
        // Two colours is still "mixed" -- upstream only skips a grid that is entirely one of them.
        assertTrue(recipe.matches(grid(
                Items.SLIME_BALL, Items.SLIME_BALL, Items.SLIME_BALL,
                Items.SLIME_BALL, Items.SLIME_BALL, Items.SLIME_BALL,
                Items.SLIME_BALL, Items.SLIME_BALL, ball(SlimeColour.MAGMA)), null));
    }

    /** Upstream's {@code ignore} list: a single-colour grid belongs to that colour's own recipe. */
    @Test
    void nineOfOneColourDoNotMatch() {
        for (SlimeColour colour : SlimeColour.values()) {
            Object ball = colour == SlimeColour.GREEN ? Items.SLIME_BALL : ball(colour);
            assertFalse(recipe.matches(grid(ball, ball, ball, ball, ball, ball, ball, ball, ball), null),
                    colour + " should be left to its own recipe");
        }
    }

    @Test
    void anythingButAFullGridOfSlimeBallsDoesNotMatch() {
        assertFalse(recipe.matches(grid(
                Items.SLIME_BALL, Items.SLIME_BALL, Items.SLIME_BALL,
                Items.SLIME_BALL, Items.CLAY_BALL, ball(SlimeColour.BLUE),
                Items.SLIME_BALL, Items.SLIME_BALL, ball(SlimeColour.PINK)), null), "a non-slimeball in the grid");
        assertFalse(recipe.matches(CraftingInput.of(2, 2, List.of(
                new ItemStack(Items.SLIME_BALL), new ItemStack(ball(SlimeColour.BLUE)),
                new ItemStack(Items.SLIME_BALL), new ItemStack(ball(SlimeColour.PINK)))), null),
                "the inventory's 2x2 grid, which upstream's canFit also refuses");
    }

    private static Item ball(SlimeColour colour) {
        return ForgeweaveItems.slimeBall(colour).asItem();
    }

    private static CraftingInput grid(Object... items) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Object item : items) {
            stacks.add(new ItemStack((Item) item));
        }
        return CraftingInput.of(3, 3, stacks);
    }
}
