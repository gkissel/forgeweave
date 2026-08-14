package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.config.ForgeweaveConfig; // #276
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.worldgen.NetherOrePlacement; // #276

/**
 * docs/SCOPE.md M2 issue #104's ore-block verification: cobalt and ardite ore gate on the
 * {@code minecraft:needs_diamond_tool} tier this PR maps upstream 1.12's above-diamond
 * {@code HarvestLevels.COBALT} onto (ForgeweaveBlocks javadoc), and each drops exactly one raw item
 * -- upstream's own unconditional, non-fortuned self-drop (BlockOre, NOTICE.md), adapted to
 * Forgeweave's raw-ore item split (#103) instead of dropping the block.
 *
 * <p>World generation itself was left entirely to the manual release checklist here (locate the ore
 * in a live Nether world), because the configured/placed feature + biome modifier JSON had no code
 * seam to assert against. Issue #276 gave the vein count one ({@link NetherOrePlacement}), so
 * {@link #netherOreVeinCountsFollowTheConfig} now covers the parts that do not need a real chunk.
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

    private static void assertPlacedThroughTheConfigModifier(GameTestHelper helper, String name) {
        ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name));
        PlacedFeature feature = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(key);
        helper.assertTrue(feature != null, "expected a placed feature registered as " + key.location());
        helper.assertTrue(feature.placement().stream().anyMatch(NetherOrePlacement.class::isInstance),
                name + " must take its vein count from the config-aware placement modifier");
    }

    @GameTest(template = "empty")
    public static void cobaltOreNeedsADiamondTierPickaxe(GameTestHelper helper) {
        assertTierGate(helper, ForgeweaveBlocks.COBALT_ORE.get(), ForgeweaveItems.RAW_COBALT.get());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arditeOreNeedsADiamondTierPickaxe(GameTestHelper helper) {
        assertTierGate(helper, ForgeweaveBlocks.ARDITE_ORE.get(), ForgeweaveItems.RAW_ARDITE.get());
        helper.succeed();
    }

    /**
     * A diamond (or better) pickaxe is the correct tool and mines the block for one raw item; an
     * iron pickaxe is not the correct tool, matching {@code minecraft:needs_diamond_tool}.
     */
    private static void assertTierGate(GameTestHelper helper, Block ore, Item rawItem) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState state = ore.defaultBlockState();

        ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
        ItemStack diamond = new ItemStack(Items.DIAMOND_PICKAXE);
        helper.assertFalse(iron.isCorrectToolForDrops(state),
                ore + " must refuse an iron pickaxe (needs_diamond_tool)");
        helper.assertTrue(diamond.isCorrectToolForDrops(state),
                ore + " must accept a diamond pickaxe");

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pos);
        helper.setBlock(pos, state);
        List<ItemStack> drops = Block.getDrops(state, level, absolute, level.getBlockEntity(absolute), player, diamond);
        helper.setBlock(pos, Blocks.AIR);

        helper.assertTrue(drops.size() == 1, "expected exactly one item stack of drops, got " + drops.size());
        ItemStack drop = drops.get(0);
        helper.assertTrue(drop.is(rawItem), "expected " + rawItem + ", got " + drop);
        helper.assertTrue(drop.getCount() == 1, "expected a single raw item, no fortune bonus, got " + drop.getCount());
    }
}
