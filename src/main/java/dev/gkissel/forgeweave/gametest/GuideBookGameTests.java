package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The guide book's registration and crafting paths (issue #273). Upstream 1.12 crafts its book
 * from a vanilla book plus a blank pattern ({@code recipes/tools/book.json}) and lets 3 paper +
 * string + 2 blank patterns stand in for the vanilla book itself
 * ({@code recipes/common/book.json}); both arrangements must resolve through the real
 * {@code RecipeManager} (NOTICE.md).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class GuideBookGameTests {

    @GameTest(template = "empty")
    public static void guideBookItemIsRegistered(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "guide_book");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "forgeweave:guide_book is not registered");
        helper.assertTrue(new ItemStack(ForgeweaveItems.GUIDE_BOOK.get()).getMaxStackSize() == 1,
                "the guide book should not stack");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bookPlusBlankPatternCraftsTheGuideBook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(2, 1, List.of(
                new ItemStack(Items.BOOK),
                new ItemStack(ForgeweaveItems.PATTERN_BLANK.get())));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveItems.GUIDE_BOOK.get()),
                "book + blank pattern should craft the guide book, got " + crafted);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void paperStringAndPatternsCraftAVanillaBook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(3, 2, List.of(
                new ItemStack(Items.PAPER),
                new ItemStack(Items.PAPER),
                new ItemStack(Items.PAPER),
                new ItemStack(Items.STRING),
                new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()),
                new ItemStack(ForgeweaveItems.PATTERN_BLANK.get())));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(Items.BOOK),
                "3 paper + string + 2 blank patterns should craft a vanilla book, got " + crafted);
        helper.succeed();
    }
}
