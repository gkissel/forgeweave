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
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;

/**
 * Issue #393's Part Builder verification for the two bow parts. {@link M3PartGameTests} already
 * sweeps "every part crafts from its own pattern", but it pays for every craft in <em>wood</em>
 * shards, and wood has no {@code bowstring} stat block -- so the bow string cannot join that sweep,
 * and the reason it cannot is exactly what this file pins.
 *
 * <p>Upstream refuses the same two crafts from {@code ToolBuilder#tryBuildToolPart}, which asks
 * {@code material.hasStats(part's stat type)} before stamping; issue #392 ported that check into
 * {@code PartBuilderRecipes#resolve}. Before the bow parts there was no shipped pair of materials
 * that could tell a passing check from an absent one -- every material had every stat block -- so
 * this is the first test that can actually fail if the check is dropped.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class BowPartGameTests {

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
     * M3.5 issue #401: the bow limb's casting entries -- what {@code jei.CastingRecipes} splits into
     * the Casting Table category. Three rows have to exist for JEI to show the full loop: pour gold
     * over a crafted limb to mould its cast, pour a metal through that cast to get a limb, and the
     * single-use clay counterpart of the second (issue #292's {@code clay_cast_bow_limb}). All three
     * are {@code Station.TABLE}: upstream never routes a tool part through the basin
     * ({@code TinkerRegistry#registerToolPart} always calls {@code addCastForItem}, which only lands
     * in {@code tableCastRegistry}) -- see {@link M3CastingGameTests}' class javadoc.
     *
     * <p>The bow <em>string</em> deliberately has none: upstream only reaches
     * {@code registerToolpartMeltingCasting} through a {@code MaterialIntegration}, i.e. a material
     * with a molten fluid, and the only BOWSTRING materials are string and vine, neither of which
     * melts. A cast no fluid could fill would be a JEI entry no player could ever use.
     */
    @GameTest(template = "empty")
    public static void theBowLimbHasItsGoldAndClayCastingEntriesAndTheStringHasNone(GameTestHelper helper) {
        var recipes = helper.getLevel().registryAccess().registryOrThrow(CastingRecipe.REGISTRY);

        helper.assertTrue(recipes.stream().anyMatch(recipe ->
                        recipe.station() == CastingRecipe.Station.TABLE
                                && recipe.result().is(ForgeweaveItems.CAST_BOW_LIMB.get())),
                "no casting recipe moulds the gold bow limb cast");
        helper.assertTrue(recipes.stream().anyMatch(recipe ->
                        recipe.station() == CastingRecipe.Station.TABLE
                                && recipe.result().is(ForgeweaveItems.PART_BOW_LIMB.get())
                                && recipe.cast().isPresent()
                                && recipe.cast().get().test(new ItemStack(ForgeweaveItems.CAST_BOW_LIMB.get()))),
                "no casting recipe pours a metal through the gold bow limb cast");
        helper.assertTrue(recipes.stream().anyMatch(recipe ->
                        recipe.result().is(ForgeweaveItems.PART_BOW_LIMB.get())
                                && recipe.cast().isPresent()
                                && recipe.cast().get().test(
                                        new ItemStack(ForgeweaveItems.CLAY_CASTS.get("cast_bow_limb").get()))),
                "no casting recipe pours a metal through the clay bow limb cast");
        helper.assertFalse(recipes.stream().anyMatch(recipe ->
                        recipe.result().is(ForgeweaveItems.PART_BOW_STRING.get())),
                "the bow string casts from nothing upstream -- see this test's javadoc");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bowLimbCraftsFromABowMaterial(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // 3 ingots = 6 shards (TinkerTools.java:210, Material.VALUE_Ingot * 3).
        ItemStack output = craft(helper, pos, player, ForgeweaveItems.PATTERN_BOW_LIMB.get(), woodShards(6));

        helper.assertTrue(output.is(ForgeweaveItems.PART_BOW_LIMB.get()), "expected a bow limb, got " + output);
        helper.assertTrue(materialId("wood").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the bow limb's material to be forgeweave:wood, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bowStringCraftsFromStringAndVine(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // 1 ingot, and one string/vine is worth exactly that (their material JSONs).
        for (String material : new String[] {"string", "vine"}) {
            Item item = material.equals("string") ? Items.STRING : Items.VINE;
            ItemStack output = craft(helper, pos, player, ForgeweaveItems.PATTERN_BOW_STRING.get(), new ItemStack(item));

            helper.assertTrue(output.is(ForgeweaveItems.PART_BOW_STRING.get()),
                    "expected a bow string from " + material + ", got " + output);
            helper.assertTrue(materialId(material).equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the bow string's material to be forgeweave:" + material + ", got "
                            + output.get(ForgeweaveDataComponents.MATERIAL.get()));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partBuilderRefusesAMaterialWithoutThePartsStatBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // Wood pays the cost twice over but has no `bowstring` block, so no bow string is stamped.
        helper.assertTrue(
                craft(helper, pos, player, ForgeweaveItems.PATTERN_BOW_STRING.get(), woodShards(6)).isEmpty(),
                "wood has no bowstring stats, so the part builder must stamp nothing");

        // String pays the cost but has no `bow` block, so no bow limb is stamped either.
        helper.assertTrue(
                craft(helper, pos, player, ForgeweaveItems.PATTERN_BOW_LIMB.get(),
                        new ItemStack(Items.STRING, 3)).isEmpty(),
                "string has no bow stats, so the part builder must stamp nothing");

        // ... and the same string still stamps a pickaxe head no better: no head stats either.
        helper.assertTrue(
                craft(helper, pos, player, ForgeweaveItems.PATTERN_PICKAXE_HEAD.get(),
                        new ItemStack(Items.STRING, 2)).isEmpty(),
                "string has no head stats, so the part builder must stamp nothing");

        helper.succeed();
    }
}
