package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * Parity audit T55, issue #486: upstream 1.12's {@code addFlintRecipe} (default on) -- 3 gravel
 * crafts into a flint. {@link #gravelCraftsIntoFlint} is the direct regression, failing on the
 * pre-fix code where no such recipe is registered at all. {@link #turningTheOptionOffStopsTheRecipe}
 * covers the config toggle itself, matching every other Forgeweave option's "checked at match time,
 * no restart needed" convention (see {@link ForgeweaveConfig#ADD_FLINT_RECIPE}).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class GravelFlintGameTests {

    @GameTest(template = "empty")
    public static void gravelCraftsIntoFlint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(3, 1,
                List.of(new ItemStack(Items.GRAVEL), new ItemStack(Items.GRAVEL), new ItemStack(Items.GRAVEL)));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(Items.FLINT) && crafted.getCount() == 1,
                "expected 3 gravel to craft 1 flint, got " + crafted);

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void turningTheOptionOffStopsTheRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(3, 1,
                List.of(new ItemStack(Items.GRAVEL), new ItemStack(Items.GRAVEL), new ItemStack(Items.GRAVEL)));

        ForgeweaveConfig.ADD_FLINT_RECIPE.set(false);
        try {
            boolean matched = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).isPresent();
            helper.assertFalse(matched, "expected no flint recipe to match with addFlintRecipe off");
        } finally {
            ForgeweaveConfig.ADD_FLINT_RECIPE.set(true);
        }

        helper.succeed();
    }
}
