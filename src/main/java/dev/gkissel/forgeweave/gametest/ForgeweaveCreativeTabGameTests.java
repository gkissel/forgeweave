package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveCreativeTab;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Covers issue #506 (T75, parity audit): upstream {@code listAllTables}. A bare unit test's
 * {@code Bootstrap.bootStrap()} never binds real item tags ({@code
 * ForgeweaveCreativeTabTest} only exercises the "tag absent" fallback), so this drives {@link
 * ForgeweaveCreativeTab#addGeneralItems} from a real GameTest server, which has actually loaded
 * {@code #minecraft:planks}/{@code #minecraft:logs}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ForgeweaveCreativeTabGameTests {

    private static List<ItemStack> build(GameTestHelper helper, boolean listAllTableVariants) {
        CreativeModeTab.ItemDisplayParameters parameters =
                new CreativeModeTab.ItemDisplayParameters(FeatureFlags.VANILLA_SET, true, helper.getLevel().registryAccess());
        List<ItemStack> displayed = new ArrayList<>();
        ForgeweaveCreativeTab.addGeneralItems(parameters, (stack, visibility) -> displayed.add(stack), listAllTableVariants);
        return displayed;
    }

    private static List<ItemStack> stacksOf(List<ItemStack> displayed, Item item) {
        return displayed.stream().filter(stack -> stack.is(item)).toList();
    }

    @GameTest(template = "empty")
    public static void listAllTableVariantsListsOneStencilTableAndPartBuilderPerPlankAndLog(GameTestHelper helper) {
        List<ItemStack> displayed = build(helper, true);

        int planks = BuiltInRegistries.ITEM.getTag(ItemTags.PLANKS).map(tag -> tag.size()).orElse(0);
        int logs = BuiltInRegistries.ITEM.getTag(ItemTags.LOGS).map(tag -> tag.size()).orElse(0);
        helper.assertTrue(planks > 1, "expected the game to have more than one plank type loaded");
        helper.assertTrue(logs > 1, "expected the game to have more than one log type loaded");

        helper.assertValueEqual(stacksOf(displayed, ForgeweaveItems.STENCIL_TABLE.get()).size(), planks,
                "expected one Stencil Table variant per plank");
        helper.assertValueEqual(stacksOf(displayed, ForgeweaveItems.PART_BUILDER.get()).size(), logs,
                "expected one Part Builder variant per log");

        boolean everyVariantHasATexture = stacksOf(displayed, ForgeweaveItems.STENCIL_TABLE.get()).stream()
                .allMatch(stack -> stack.get(ForgeweaveDataComponents.TEXTURE.get()) != null);
        helper.assertTrue(everyVariantHasATexture, "expected every listed Stencil Table variant to carry a TEXTURE component");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void listAllTableVariantsOffListsOnlyOneStencilTableAndPartBuilder(GameTestHelper helper) {
        List<ItemStack> displayed = build(helper, false);

        helper.assertValueEqual(stacksOf(displayed, ForgeweaveItems.STENCIL_TABLE.get()).size(), 1,
                "expected exactly one Stencil Table with listAllTableVariants off");
        helper.assertValueEqual(stacksOf(displayed, ForgeweaveItems.PART_BUILDER.get()).size(), 1,
                "expected exactly one Part Builder with listAllTableVariants off");

        helper.succeed();
    }

    /** Upstream never expands the Crafting Station or Tool Station: both stay a single boring entry. */
    @GameTest(template = "empty")
    public static void craftingStationAndToolStationAreNeverExpanded(GameTestHelper helper) {
        List<ItemStack> displayed = build(helper, true);

        helper.assertValueEqual(stacksOf(displayed, ForgeweaveItems.CRAFTING_STATION.get()).size(), 1,
                "expected the Crafting Station to stay a single entry even with listAllTableVariants on");
        helper.assertValueEqual(stacksOf(displayed, ForgeweaveItems.TOOL_STATION.get()).size(), 1,
                "expected the Tool Station to stay a single entry even with listAllTableVariants on");

        helper.succeed();
    }
}
