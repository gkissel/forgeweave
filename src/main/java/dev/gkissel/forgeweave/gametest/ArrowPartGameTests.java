package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;

/**
 * Issue #626's Part Builder verification for the three arrow parts, the same shape
 * {@link BowPartGameTests} gave #393's bow pair: the SHAFT and FLETCHING stat gates are what keep
 * an arrow shaft from being stamped out of feathers, and the six arrow-only materials
 * (blaze/reed/ice/endrod shafts, feather/leaf fletchings -- {@code
 * TinkerMaterials#registerProjectileMaterialStats} at the pinned commit) are the first shipped
 * materials that can tell those gates from absent ones.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ArrowPartGameTests {

    private static PartBuilderMenu openMenu(GameTestHelper helper, BlockPos pos, Player player) {
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos);
        return new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    /** Loads the pattern and material slots and returns whatever the output slot resolved to. */
    private static ItemStack craft(GameTestHelper helper, BlockPos pos, Player player, Item pattern, ItemStack material) {
        PartBuilderMenu menu = openMenu(helper, pos, player);
        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(pattern));
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(material);
        menu.broadcastChanges();
        return menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
    }

    /** Wood shards, the cheapest exact-value payment for any cost (half an ingot each). */
    private static ItemStack woodShards(int count) {
        ItemStack shards = new ItemStack(ForgeweaveItems.SHARD.get(), count);
        shards.set(ForgeweaveDataComponents.MATERIAL.get(), materialId("wood"));
        return shards;
    }

    /**
     * The arrow head's casting loop, the same three rows {@link
     * BowPartGameTests#theBowLimbHasItsGoldAndClayCastingEntriesAndTheStringHasNone} pins for the
     * bow limb: mould the gold cast from a crafted head, pour a metal through it, and the clay
     * counterpart. The shaft and fletching deliberately have none -- upstream only reaches
     * {@code registerToolpartMeltingCasting} through a material with a molten fluid, and no molten
     * material carries a SHAFT or FLETCHING stat block (the bow string's situation exactly).
     */
    @GameTest(template = "empty")
    public static void theArrowHeadHasItsCastingEntriesAndTheShaftAndFletchingHaveNone(GameTestHelper helper) {
        var recipes = helper.getLevel().registryAccess().registryOrThrow(CastingRecipe.REGISTRY);

        helper.assertTrue(recipes.stream().anyMatch(recipe ->
                        recipe.station() == CastingRecipe.Station.TABLE
                                && recipe.result().is(ForgeweaveItems.CAST_ARROW_HEAD.get())),
                "no casting recipe moulds the gold arrow head cast");
        helper.assertTrue(recipes.stream().anyMatch(recipe ->
                        recipe.station() == CastingRecipe.Station.TABLE
                                && recipe.result().is(ForgeweaveItems.PART_ARROW_HEAD.get())
                                && recipe.cast().isPresent()
                                && recipe.cast().get().test(new ItemStack(ForgeweaveItems.CAST_ARROW_HEAD.get()))),
                "no casting recipe pours a metal through the gold arrow head cast");
        helper.assertTrue(recipes.stream().anyMatch(recipe ->
                        recipe.result().is(ForgeweaveItems.PART_ARROW_HEAD.get())
                                && recipe.cast().isPresent()
                                && recipe.cast().get().test(
                                        new ItemStack(ForgeweaveItems.CLAY_CASTS.get("cast_arrow_head").get()))),
                "no casting recipe pours a metal through the clay arrow head cast");
        helper.assertFalse(recipes.stream().anyMatch(recipe ->
                        recipe.result().is(ForgeweaveItems.PART_ARROW_SHAFT.get())
                                || recipe.result().is(ForgeweaveItems.PART_FLETCHING.get())),
                "the shaft and fletching cast from nothing upstream -- see this test's javadoc");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arrowHeadCraftsFromAHeadMaterial(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack output = craft(helper, pos, player, ForgeweaveItems.PATTERN_ARROW_HEAD.get(), woodShards(4));

        helper.assertTrue(output.is(ForgeweaveItems.PART_ARROW_HEAD.get()), "expected an arrow head, got " + output);
        helper.assertTrue(materialId("wood").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the arrow head's material to be forgeweave:wood, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));
        helper.succeed();
    }

    /**
     * Each shaft material's crafting item, one part per {@code TinkerMaterials:367-381} row:
     * blaze rods, sugar cane, packed ice, end rods -- all at {@code VALUE_Ingot} each, so two pay
     * the {@code VALUE_Ingot * 2} shaft exactly.
     */
    @GameTest(template = "empty")
    public static void arrowShaftCraftsFromEveryShaftMaterial(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        record Row(String material, Item item) {}
        for (Row row : new Row[] {
                new Row("blaze", Items.BLAZE_ROD),
                new Row("reed", Items.SUGAR_CANE),
                new Row("ice", Items.PACKED_ICE),
                new Row("endrod", Items.END_ROD)}) {
            ItemStack output = craft(helper, pos, player, ForgeweaveItems.PATTERN_ARROW_SHAFT.get(),
                    new ItemStack(row.item(), 2));

            helper.assertTrue(output.is(ForgeweaveItems.PART_ARROW_SHAFT.get()),
                    "expected an arrow shaft from " + row.material() + ", got " + output);
            helper.assertTrue(materialId(row.material()).equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the arrow shaft's material to be forgeweave:" + row.material() + ", got "
                            + output.get(ForgeweaveDataComponents.MATERIAL.get()));
        }
        helper.succeed();
    }

    /**
     * Feather at {@code VALUE_Ingot}, leaf at {@code VALUE_Shard} through the {@code treeLeaves}
     * oredict -- {@code #minecraft:leaves} here, so any vanilla leaf block pays 72.
     */
    @GameTest(template = "empty")
    public static void fletchingCraftsFromFeatherAndLeaf(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack fromFeather = craft(helper, pos, player, ForgeweaveItems.PATTERN_FLETCHING.get(),
                new ItemStack(Items.FEATHER, 2));
        helper.assertTrue(fromFeather.is(ForgeweaveItems.PART_FLETCHING.get()),
                "expected a fletching from feathers, got " + fromFeather);
        helper.assertTrue(materialId("feather").equals(fromFeather.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected forgeweave:feather, got " + fromFeather.get(ForgeweaveDataComponents.MATERIAL.get()));

        ItemStack fromLeaves = craft(helper, pos, player, ForgeweaveItems.PATTERN_FLETCHING.get(),
                new ItemStack(Items.OAK_LEAVES, 4));
        helper.assertTrue(fromLeaves.is(ForgeweaveItems.PART_FLETCHING.get()),
                "expected a fletching from oak leaves, got " + fromLeaves);
        helper.assertTrue(materialId("leaf").equals(fromLeaves.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected forgeweave:leaf, got " + fromLeaves.get(ForgeweaveDataComponents.MATERIAL.get()));
        helper.succeed();
    }

    /** The SHAFT/FLETCHING stat gates, and the arrow-only materials refusing every other part. */
    @GameTest(template = "empty")
    public static void partBuilderRefusesAMaterialWithoutThePartsStatBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.assertTrue(
                craft(helper, pos, player, ForgeweaveItems.PATTERN_ARROW_SHAFT.get(),
                        new ItemStack(Items.FEATHER, 2)).isEmpty(),
                "feather has no shaft stats, so the part builder must stamp nothing");

        helper.assertTrue(
                craft(helper, pos, player, ForgeweaveItems.PATTERN_FLETCHING.get(),
                        new ItemStack(Items.BLAZE_ROD, 2)).isEmpty(),
                "blaze has no fletching stats, so the part builder must stamp nothing");

        helper.assertTrue(
                craft(helper, pos, player, ForgeweaveItems.PATTERN_ARROW_HEAD.get(),
                        new ItemStack(Items.BLAZE_ROD, 2)).isEmpty(),
                "blaze has no head stats, so the part builder must stamp nothing");

        helper.assertTrue(
                craft(helper, pos, player, ForgeweaveItems.PATTERN_PICKAXE_HEAD.get(),
                        new ItemStack(Items.PACKED_ICE, 2)).isEmpty(),
                "ice has no head stats, so the part builder must stamp nothing");

        helper.succeed();
    }
}
