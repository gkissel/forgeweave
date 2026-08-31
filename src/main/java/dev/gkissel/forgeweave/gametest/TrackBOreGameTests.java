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
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.trackb.TrackBOre;
import dev.gkissel.forgeweave.worldgen.TrackBOrePlacement;

/**
 * Track B's ore family (issue #839, epic #824): every ore's own harvest-tier gate (per the M6 tier
 * scaffold, research doc §7.1) and drop, plus the config-aware group toggle and worldgen JSON's own
 * wiring -- the same split {@link NetherOreGameTests} already draws between "provable without a real
 * chunk" and "needs an actual generated world, left to the manual release checklist" (this class's
 * javadoc there applies here too: only "does a chunk actually contain the ore" is out of reach).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class TrackBOreGameTests {

    /**
     * #839's own "grouped, not per-ore" toggle: every Track B placed feature must route its vein
     * count through {@link TrackBOrePlacement}, and switching {@link ForgeweaveConfig#GEN_TRACK_B_ORES}
     * off must zero every ore's count in the same tick -- the group-toggle test the issue's own "Test
     * strategy" section asks for ("an ore group with its toggle off generates nothing").
     */
    @GameTest(template = "empty")
    public static void trackBOreGroupToggleGatesEveryOre(GameTestHelper helper) {
        TrackBOrePlacement onePerFeature = new TrackBOrePlacement(5);
        helper.assertValueEqual((int) onePerFeature.getPositions(null, null, BlockPos.ZERO).count(),
                5, "positions while genTrackBOres is on");

        ForgeweaveConfig.GEN_TRACK_B_ORES.set(false);
        try {
            helper.assertValueEqual((int) onePerFeature.getPositions(null, null, BlockPos.ZERO).count(),
                    0, "positions while genTrackBOres is off");
        } finally {
            ForgeweaveConfig.GEN_TRACK_B_ORES.set(true);
        }

        helper.succeed();
    }

    /**
     * Every Track B placed feature JSON parses and references a registered block, and routes its
     * count through {@link TrackBOrePlacement} rather than a fixed {@code minecraft:count} -- the
     * same "the hand-written JSON still resolves against the registered modifier type" check
     * {@link NetherOreGameTests#assertPlacedThroughTheConfigModifier} makes for cobalt/ardite.
     */
    @GameTest(template = "empty")
    public static void everyTrackBOrePlacedFeatureRoutesThroughTheGroupModifier(GameTestHelper helper) {
        for (TrackBOre ore : TrackBOre.ALL) {
            PlacedFeature feature = getPlacedFeature(helper, ore.oreBlockId());
            helper.assertTrue(feature.placement().stream().anyMatch(TrackBOrePlacement.class::isInstance),
                    ore.oreBlockId() + " must take its vein count from TrackBOrePlacement");
        }
        helper.succeed();
    }

    private static PlacedFeature getPlacedFeature(GameTestHelper helper, String name) {
        ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name));
        PlacedFeature feature = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(key);
        helper.assertTrue(feature != null, "expected a placed feature registered as " + key.location());
        return feature;
    }

    /**
     * Every ore's harvest-tier gate matches the M6 tier scaffold (research doc §7.1): the ten
     * netherite-tier ores take cobalt/ardite's exact needs_diamond_tool + incorrect_for_diamond_tool
     * combo (netherite pickaxe only), the one diamond-tier ore (fulmenite) accepts an iron pickaxe
     * but refuses wood, and the one stone-tier ore (cinderstone) accepts any pickaxe including wood.
     * Each also drops exactly one raw item, same unconditional self-drop as cobalt/ardite.
     */
    @GameTest(template = "empty")
    public static void everyTrackBOreHasTheRightTierGateAndDrop(GameTestHelper helper) {
        for (TrackBOre ore : TrackBOre.ALL) {
            Block oreBlock = ForgeweaveBlocks.trackBOre(ore.id()).get();
            Item rawItem = ForgeweaveItems.trackBRawItem(ore.id()).get();
            switch (ore.tier()) {
                case NETHERITE -> assertMinimumTier(helper, oreBlock, rawItem, Items.DIAMOND_PICKAXE, false);
                case DIAMOND -> assertMinimumTier(helper, oreBlock, rawItem, Items.IRON_PICKAXE, true);
                case STONE -> assertMinimumTier(helper, oreBlock, rawItem, Items.WOODEN_PICKAXE, true);
            }
        }
        helper.succeed();
    }

    /**
     * Mines {@code oreBlock} with {@code belowThreshold} (a diamond pickaxe for the netherite rung, an
     * iron pickaxe for the diamond rung, a wood pickaxe for the stone rung) and asserts whether that
     * tool is correct, then always confirms a netherite pickaxe both is correct and yields exactly one
     * {@code rawItem}.
     */
    private static void assertMinimumTier(GameTestHelper helper, Block oreBlock, Item rawItem, Item belowThreshold, boolean shouldAccept) {
        BlockState state = oreBlock.defaultBlockState();
        ItemStack below = new ItemStack(belowThreshold);
        ItemStack netherite = new ItemStack(Items.NETHERITE_PICKAXE);

        if (shouldAccept) {
            helper.assertTrue(below.isCorrectToolForDrops(state), oreBlock + " must accept " + belowThreshold);
        } else {
            helper.assertFalse(below.isCorrectToolForDrops(state), oreBlock + " must refuse " + belowThreshold);
        }
        helper.assertTrue(netherite.isCorrectToolForDrops(state), oreBlock + " must accept a netherite pickaxe");

        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pos);
        helper.setBlock(pos, state);
        List<ItemStack> drops = Block.getDrops(state, level, absolute, level.getBlockEntity(absolute), player, netherite);
        helper.setBlock(pos, Blocks.AIR);

        helper.assertTrue(drops.size() == 1, "expected exactly one item stack of drops for " + oreBlock + ", got " + drops.size());
        ItemStack drop = drops.get(0);
        helper.assertTrue(drop.is(rawItem), "expected " + rawItem + " from " + oreBlock + ", got " + drop);
        helper.assertTrue(drop.getCount() == 1, "expected a single raw item, no fortune bonus, got " + drop.getCount());
    }
}
