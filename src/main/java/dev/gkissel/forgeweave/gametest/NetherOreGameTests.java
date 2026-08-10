package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * docs/SCOPE.md M2 issue #104's ore-block verification: cobalt and ardite ore gate on the
 * {@code minecraft:needs_diamond_tool} tier this PR maps upstream 1.12's above-diamond
 * {@code HarvestLevels.COBALT} onto (ForgeweaveBlocks javadoc), and each drops exactly one raw item
 * -- upstream's own unconditional, non-fortuned self-drop (BlockOre, NOTICE.md), adapted to
 * Forgeweave's raw-ore item split (#103) instead of dropping the block. World generation itself
 * (configured/placed feature + biome modifier JSON) has no headless GameTest surface -- verified by
 * datagen JSON correctness/parsing and left to the manual release checklist (locate in a live
 * Nether world).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class NetherOreGameTests {

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
