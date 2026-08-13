package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ToolItem;

/**
 * Covers docs/SCOPE.md M1 issue #11's verification for an assembled tool in the world: mining costs
 * durability, a tool at zero durability is Broken -- unusable but still there (CONTEXT.md's hard
 * invariant) -- and tool tier comes from the head material's vanilla tier tag. Repair, being a Tool
 * Station recipe, is covered in {@link ToolStationGameTests}.
 *
 * <p>Every tool here is built by driving the real Tool Station menu
 * ({@link ToolAssembly#pickaxe}), so these tests break if assembly stops writing the components
 * behavior depends on.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ToolBehaviorGameTests {

    /**
     * Upstream 1.12 charges 1 durability for a block the tool is meant for and 2 for anything else
     * ({@code ToolCore#onBlockDestroyed}); a pickaxe on stone costs 1, the same pickaxe on dirt
     * costs 2.
     */
    @GameTest(template = "empty")
    public static void miningCostsDurability(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        helper.assertTrue(pickaxe.getMaxDamage() == 128, "expected the 128-durability pickaxe, got " + pickaxe.getMaxDamage());
        helper.assertTrue(pickaxe.getDamageValue() == 0, "a freshly assembled tool must be undamaged");

        mine(helper, player, pickaxe, pos, Blocks.STONE.defaultBlockState());
        helper.assertTrue(pickaxe.getDamageValue() == 1,
                "expected 1 damage after mining stone with a pickaxe, got " + pickaxe.getDamageValue());

        mine(helper, player, pickaxe, pos, Blocks.DIRT.defaultBlockState());
        helper.assertTrue(pickaxe.getDamageValue() == 3,
                "expected 2 more damage mining dirt with a pickaxe (total 3), got " + pickaxe.getDamageValue());

        helper.succeed();
    }

    /**
     * The CONTEXT.md invariant: at zero durability the tool is Broken, which means bare-hand mining
     * speed, no drops, and no attack modifiers -- but the stack itself survives every further use.
     */
    @GameTest(template = "empty")
    public static void brokenToolIsUnusableButNeverDestroyed(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        BlockState stone = Blocks.STONE.defaultBlockState();

        float sharpSpeed = pickaxe.getDestroySpeed(stone);
        helper.assertTrue(sharpSpeed > 1.0F, "a working pickaxe should mine stone faster than a bare hand");
        helper.assertFalse(pickaxe.getAttributeModifiers().modifiers().isEmpty(),
                "a working pickaxe should carry attack damage and speed modifiers");

        // Far more damage than the tool has durability, through the same choke point every vanilla
        // caller uses.
        pickaxe.hurtAndBreak(1_000, helper.getLevel(), player, brokenItem -> {});

        helper.assertFalse(pickaxe.isEmpty(), "a Forgeweave tool must never be destroyed by durability loss");
        helper.assertTrue(pickaxe.getCount() == 1, "expected the broken tool to still be a single item");
        helper.assertTrue(ToolItem.isBroken(pickaxe), "expected the tool to be flagged Broken");
        helper.assertTrue(pickaxe.getDamageValue() == pickaxe.getMaxDamage() - 1,
                "a Broken tool should rest one point short of destruction, got " + pickaxe.getDamageValue());
        helper.assertTrue(pickaxe.getDestroySpeed(stone) == 0.3F,
                "a Broken tool should mine at the 0.3 penalty speed, got " + pickaxe.getDestroySpeed(stone));
        helper.assertFalse(pickaxe.isCorrectToolForDrops(stone), "a Broken tool should not harvest drops");
        helper.assertTrue(pickaxe.getAttributeModifiers().modifiers().isEmpty(),
                "a Broken tool should carry no attack modifiers");
        helper.assertFalse(pickaxe.getItem().isBarVisible(pickaxe),
                "a Broken tool should not show a durability bar");

        // Keep using it: still no destruction, and no further damage to take.
        mine(helper, player, pickaxe, pos, stone);
        pickaxe.hurtAndBreak(50, helper.getLevel(), player, brokenItem -> {});
        helper.assertFalse(pickaxe.isEmpty(), "a Broken tool must survive continued use");
        helper.assertTrue(pickaxe.getDamageValue() == pickaxe.getMaxDamage() - 1,
                "a Broken tool should not accumulate further damage, got " + pickaxe.getDamageValue());

        helper.succeed();
    }

    /**
     * Tool tier is the head material's vanilla {@code incorrect_for_*_tool} block tag (CONTEXT.md:
     * never a numeric harvest level), and the tier each material maps to is upstream 1.12's harvest
     * level for it ({@code TinkerMaterials#registerToolMaterialStats}, issue #79): wood is
     * {@code HarvestLevels.STONE} and stone/flint/bone are {@code HarvestLevels.IRON}. So a
     * wood-headed pickaxe harvests iron ore but not diamond ore, and a stone-headed one harvests
     * both.
     */
    @GameTest(template = "empty")
    public static void headMaterialDecidesTier(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState ironOre = Blocks.IRON_ORE.defaultBlockState();
        BlockState diamondOre = Blocks.DIAMOND_ORE.defaultBlockState();

        ItemStack wooden = ToolAssembly.pickaxe(helper, player, pos, "wood", "wood", "wood");
        helper.assertTrue(wooden.isCorrectToolForDrops(stone), "a wood-headed pickaxe should harvest stone");
        helper.assertTrue(wooden.isCorrectToolForDrops(ironOre),
                "wood is upstream's STONE harvest level, so a wood-headed pickaxe should harvest iron ore");
        helper.assertFalse(wooden.isCorrectToolForDrops(diamondOre),
                "a wood-headed pickaxe must not harvest diamond ore (minecraft:incorrect_for_stone_tool)");

        ItemStack stony = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        helper.assertTrue(stony.isCorrectToolForDrops(diamondOre),
                "stone is upstream's IRON harvest level, so a stone-headed pickaxe should harvest diamond ore");

        helper.succeed();
    }

    /** Breaks {@code state} at {@code pos} the way a player's block break does. */
    private static void mine(GameTestHelper helper, Player player, ItemStack tool, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state);
        tool.mineBlock(helper.getLevel(), state, helper.absolutePos(pos), player);
        helper.setBlock(pos, Blocks.AIR);
    }
}
