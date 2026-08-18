package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.config.ForgeweaveConfig; // #276
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.worldgen.NetherOrePlacement; // #276

/**
 * docs/SCOPE.md M2 issue #104's ore-block verification: cobalt and ardite ore gate on the netherite
 * tier that issue #433 maps upstream 1.12's {@code HarvestLevels.COBALT} onto
 * ({@code ForgeweaveBlockTagsProvider} javadoc), and each drops exactly one raw item
 * -- upstream's own unconditional, non-fortuned self-drop (BlockOre, NOTICE.md), adapted to
 * Forgeweave's raw-ore item split (#103) instead of dropping the block.
 *
 * <p>World generation itself was left entirely to the manual release checklist here (locate the ore
 * in a live Nether world), because the configured/placed feature + biome modifier JSON had no code
 * seam to assert against. Issue #276 gave the vein count one ({@link NetherOrePlacement}), so
 * {@link #netherOreVeinCountsFollowTheConfig} now covers the parts that do not need a real chunk.
 * Issue #509 (T78) gave the height distribution a seam too -- {@code PlacementModifier} runs against
 * a real {@code PlacementContext} without needing an actual generated chunk, so
 * {@link #netherOreVeinsSplitAcrossTheUpstreamHeightBands} and
 * {@link #netherOrePlacementModifierHalvesTheRatePerBand} cover that the two placed features per ore
 * sample from the right y-ranges and split the configured rate the way upstream's loop does; only
 * "does a generated Nether chunk actually contain the ore" is still left to the manual checklist.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class NetherOreGameTests {

    /**
     * Issue #276, upstream 1.12's {@code genCobalt}/{@code cobaltRate}/{@code genArdite}/{@code
     * arditeRate}. Two halves, both of which the class javadoc above used to call untestable:
     *
     * <ul>
     *   <li>that each placed feature really routes its vein count through {@link NetherOrePlacement}
     *       rather than a fixed {@code minecraft:count} -- which is also the only check that the
     *       hand-written placed-feature JSON still parses against the registered modifier type;
     *   <li>that the modifier answers with the configured rate, zero while its ore is switched off,
     *       and that the two ores stay independent.
     * </ul>
     *
     * <p>What it still cannot do is watch a chunk generate: that needs a real Nether chunk, which no
     * GameTest template provides. Placing the veins from the returned count is vanilla's own
     * {@code PlacementModifier} contract, unchanged here.
     *
     * <p>Synchronous on purpose -- this mutates global config values, and GameTests in one batch tick
     * concurrently, so set/assert/restore has to complete inside a single test method.
     */
    @GameTest(template = "empty")
    public static void netherOreVeinCountsFollowTheConfig(GameTestHelper helper) {
        assertPlacedThroughTheConfigModifier(helper, "cobalt_ore");
        assertPlacedThroughTheConfigModifier(helper, "ardite_ore");

        helper.assertValueEqual(NetherOrePlacement.Ore.COBALT.veinsPerChunk(), 20, "default cobalt veins per chunk");
        helper.assertValueEqual(NetherOrePlacement.Ore.ARDITE.veinsPerChunk(), 20, "default ardite veins per chunk");

        ForgeweaveConfig.GEN_COBALT.set(false);
        try {
            helper.assertValueEqual(NetherOrePlacement.Ore.COBALT.veinsPerChunk(), 0, "cobalt veins while genCobalt is off");
            helper.assertValueEqual(NetherOrePlacement.Ore.ARDITE.veinsPerChunk(), 20, "ardite veins, unaffected by genCobalt");
        } finally {
            ForgeweaveConfig.GEN_COBALT.set(true);
        }

        ForgeweaveConfig.ARDITE_RATE.set(7);
        try {
            helper.assertValueEqual(NetherOrePlacement.Ore.ARDITE.veinsPerChunk(), 7, "ardite veins at arditeRate 7");
            helper.assertValueEqual(NetherOrePlacement.Ore.COBALT.veinsPerChunk(), 20, "cobalt veins, unaffected by arditeRate");
        } finally {
            ForgeweaveConfig.ARDITE_RATE.set(20);
        }

        helper.succeed();
    }

    /**
     * Each of the two height-band placed features (see {@link #netherOreVeinsSplitAcrossTheUpstreamHeightBands})
     * draws from the <em>same</em> {@link NetherOrePlacement} instance for its ore, so the modifier
     * itself has to halve {@link NetherOrePlacement.Ore#veinsPerChunk()} per call -- ceiling, not
     * floor, matching upstream's {@code for (i = 0; i < rate; i += 2)} iteration count -- rather than
     * emitting the full configured rate twice.
     */
    @GameTest(template = "empty")
    public static void netherOrePlacementModifierHalvesTheRatePerBand(GameTestHelper helper) {
        assertPositionsPerCall(helper, NetherOrePlacement.Ore.COBALT, 10);

        ForgeweaveConfig.ARDITE_RATE.set(7);
        try {
            assertPositionsPerCall(helper, NetherOrePlacement.Ore.ARDITE, 4);
        } finally {
            ForgeweaveConfig.ARDITE_RATE.set(20);
        }

        ForgeweaveConfig.GEN_ARDITE.set(false);
        try {
            assertPositionsPerCall(helper, NetherOrePlacement.Ore.ARDITE, 0);
        } finally {
            ForgeweaveConfig.GEN_ARDITE.set(true);
        }

        helper.succeed();
    }

    private static void assertPositionsPerCall(GameTestHelper helper, NetherOrePlacement.Ore ore, int expected) {
        NetherOrePlacement modifier = new NetherOrePlacement(ore);
        long count = modifier.getPositions(null, null, BlockPos.ZERO).count();
        helper.assertValueEqual((int) count, expected, ore + " positions per band call");
    }

    private static void assertPlacedThroughTheConfigModifier(GameTestHelper helper, String name) {
        PlacedFeature feature = getPlacedFeature(helper, name);
        helper.assertTrue(feature.placement().stream().anyMatch(NetherOrePlacement.class::isInstance),
                name + " must take its vein count from the config-aware placement modifier");
    }

    private static PlacedFeature getPlacedFeature(GameTestHelper helper, String name) {
        ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name));
        PlacedFeature feature = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(key);
        helper.assertTrue(feature != null, "expected a placed feature registered as " + key.location());
        return feature;
    }

    /**
     * Upstream's {@code generateNetherOre} (issue #276's NOTICE.md row) runs two loop bodies per
     * {@code i < rate; i += 2} iteration -- one vein at {@code y 32 + [0,64)} (i.e. y32-95), one at
     * {@code y 0 + [0,128)} (the full column) -- so half the veins concentrate in the narrower y32-95
     * band and the other half spread across the whole Nether. T78 (parity audit 2026-08-18) found
     * the previous port had collapsed both loop bodies into one {@code minecraft:height_range}
     * uniform across 0-127, losing that concentration. This asserts both placed features exist per
     * ore, each still routes its count through {@link NetherOrePlacement}, and each really samples
     * from its own upstream-matched band -- not just that the JSON parses.
     */
    @GameTest(template = "empty")
    public static void netherOreVeinsSplitAcrossTheUpstreamHeightBands(GameTestHelper helper) {
        assertBandedPlacement(helper, "cobalt_ore", "cobalt_ore_band");
        assertBandedPlacement(helper, "ardite_ore", "ardite_ore_band");
        helper.succeed();
    }

    private static void assertBandedPlacement(GameTestHelper helper, String fullRangeName, String bandName) {
        assertPlacedThroughTheConfigModifier(helper, fullRangeName);
        assertPlacedThroughTheConfigModifier(helper, bandName);
        assertHeightRangeSamplesWithin(helper, fullRangeName, 0, 127);
        assertHeightRangeSamplesWithin(helper, bandName, 32, 95);
    }

    /** Samples the placed feature's {@link HeightRangePlacement} many times and checks every result lands in [min, max]. */
    private static void assertHeightRangeSamplesWithin(GameTestHelper helper, String name, int min, int max) {
        PlacedFeature feature = getPlacedFeature(helper, name);
        PlacementModifier modifier = feature.placement().stream()
                .filter(HeightRangePlacement.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " must carry a minecraft:height_range placement"));

        ServerLevel level = helper.getLevel();
        PlacementContext context = new PlacementContext(level, level.getChunkSource().getGenerator(), java.util.Optional.empty());
        RandomSource random = RandomSource.create(42L);
        BlockPos origin = BlockPos.ZERO;

        final int samples = 200;
        for (int i = 0; i < samples; i++) {
            int y = modifier.getPositions(context, random, origin).findFirst().orElseThrow().getY();
            helper.assertTrue(y >= min && y <= max,
                    name + " sampled y=" + y + ", expected within [" + min + ", " + max + "]");
        }
    }

    @GameTest(template = "empty")
    public static void cobaltOreNeedsANetheriteTierPickaxe(GameTestHelper helper) {
        assertTierGate(helper, ForgeweaveBlocks.COBALT_ORE.get(), ForgeweaveItems.RAW_COBALT.get());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arditeOreNeedsANetheriteTierPickaxe(GameTestHelper helper) {
        assertTierGate(helper, ForgeweaveBlocks.ARDITE_ORE.get(), ForgeweaveItems.RAW_ARDITE.get());
        helper.succeed();
    }

    /**
     * Upstream {@code BlockOre:30} gates both ores at {@code HarvestLevels.COBALT} (4), which issue
     * #433 established is the netherite tier -- so a netherite pickaxe is the correct tool and mines
     * the block for one raw item, and a <em>diamond</em> one is not. Vanilla has no
     * {@code needs_netherite_tool} tag, so {@code ForgeweaveBlockTagsProvider} spells the gate as
     * {@code needs_diamond_tool} plus a listing in {@code incorrect_for_diamond_tool}; this pins both
     * halves.
     */
    private static void assertTierGate(GameTestHelper helper, Block ore, Item rawItem) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState state = ore.defaultBlockState();

        ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
        ItemStack diamondPick = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack netherite = new ItemStack(Items.NETHERITE_PICKAXE);
        helper.assertFalse(iron.isCorrectToolForDrops(state),
                ore + " must refuse an iron pickaxe (needs_diamond_tool)");
        helper.assertFalse(diamondPick.isCorrectToolForDrops(state),
                ore + " must refuse a diamond pickaxe (incorrect_for_diamond_tool -- upstream's COBALT level)");
        helper.assertTrue(netherite.isCorrectToolForDrops(state),
                ore + " must accept a netherite pickaxe");

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pos);
        helper.setBlock(pos, state);
        List<ItemStack> drops = Block.getDrops(state, level, absolute, level.getBlockEntity(absolute), player, netherite);
        helper.setBlock(pos, Blocks.AIR);

        helper.assertTrue(drops.size() == 1, "expected exactly one item stack of drops, got " + drops.size());
        ItemStack drop = drops.get(0);
        helper.assertTrue(drop.is(rawItem), "expected " + rawItem + ", got " + drop);
        helper.assertTrue(drop.getCount() == 1, "expected a single raw item, no fortune bonus, got " + drop.getCount());
    }
}
