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
import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.client.book.BookContent;
import dev.gkissel.forgeweave.client.book.BookPage;
import dev.gkissel.forgeweave.client.book.BookSection;
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

    /**
     * M3.5 issue #401: the book's tools section is driven by {@link BookContent#TOOLS}, so the three
     * bows get a page each with no new page type -- upstream lists them the same way, as three more
     * {@code "type": "tool"} entries in one flat {@code book/sections/tools.json} rather than a
     * section of their own. This is the "registry-driven {@code ToolPage} picks them up" the ticket
     * asks to verify, checked against the real, level-bound registry access {@code BookScreen} uses.
     */
    @GameTest(template = "empty")
    public static void theBookListsAPageForEachOfTheThreeBows(GameTestHelper helper) {
        List<BookPage> toolPages = pagesOf(helper, "book.forgeweave.section.tools");

        for (var bow : List.of(ForgeweaveItems.TOOL_SHORTBOW.get(), ForgeweaveItems.TOOL_LONGBOW.get(),
                ForgeweaveItems.TOOL_CROSSBOW.get())) {
            helper.assertTrue(toolPages.contains(new BookPage.ToolPage(bow)),
                    "the guide book's tools section has no page for " + bow);
        }
        helper.succeed();
    }

    /**
     * M3.5 issue #401: the bow limb's and bow string's contributions reach the book through the
     * material pages, which render {@code StationText#bowStats}/{@code #bowstringStats} live off the
     * datapack registry -- so {@code string}, a material with the BOWSTRING block and nothing else
     * (issue #392), still gets a page and that page still has a row on it.
     */
    @GameTest(template = "empty")
    public static void theBookMaterialPagesCarryTheBowAndBowstringStatBlocks(GameTestHelper helper) {
        List<BookPage> materialPages = pagesOf(helper, "book.forgeweave.section.materials");

        BookPage.MaterialPage string = materialPage(helper, materialPages, "string");
        helper.assertFalse(StationText.bowstringStats(string.material()).isEmpty(),
                "string carries the BOWSTRING block, so its book page must show a bowstring row");
        helper.assertTrue(StationText.bowStats(string.material()).isEmpty(),
                "string has no BOW block, so its page must show no bow limb rows");

        BookPage.MaterialPage iron = materialPage(helper, materialPages, "iron");
        helper.assertFalse(StationText.bowStats(iron.material()).isEmpty(),
                "iron carries the BOW block, so its book page must show the bow limb rows");
        helper.succeed();
    }

    private static List<BookPage> pagesOf(GameTestHelper helper, String sectionKey) {
        return BookContent.sections(helper.getLevel().registryAccess()).stream()
                .filter(section -> section.titleKey().equals(sectionKey))
                .findFirst()
                .map(BookSection::pages)
                .orElseThrow(() -> new AssertionError("the guide book has no " + sectionKey + " section"));
    }

    private static BookPage.MaterialPage materialPage(GameTestHelper helper, List<BookPage> pages, String path) {
        return pages.stream()
                .filter(BookPage.MaterialPage.class::isInstance)
                .map(BookPage.MaterialPage.class::cast)
                .filter(page -> page.id().getPath().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the guide book has no material page for " + path));
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
