package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * docs/SCOPE.md M2 issue #103's verification: the seven metal materials assembled through the real
 * Tool Station ({@code ToolAssembly.pickaxe}) -- clone-exact stats, repair with the head material's
 * ingot, rose gold's {@code quick}, netherite's {@code reinforced_core}, and the netherite-ingot
 * application of the existing {@code extra_slot} modifier. Magnitudes are cited in {@code ForgeweaveTraits} and the
 * material JSONs themselves; deviations are recorded in NOTICE.md and the PR.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MetalMaterialGameTests {

    /**
     * Manyullyn -- upstream's richest metal stat line (issue #103), on a tool assembled entirely from
     * it. {@code ToolStats#compute}: durability = round((820 + 50) * 0.5) + 250 = 685, mining speed
     * and attack damage read straight off the head material.
     */
    @GameTest(template = "empty")
    public static void manyullynProducesUpstreamsExactStats(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "manyullyn", "manyullyn", "manyullyn");

        ToolStats.Stats stats = pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null, "an assembled tool must carry its computed stats");
        helper.assertTrue(stats.durability() == 685,
                "expected round((820 + 50) * 0.5) + 250 = 685 durability, got " + stats.durability());
        helper.assertTrue(Math.abs(stats.miningSpeed() - 7.02F) < 0.001F,
                "expected manyullyn's 7.02 mining speed, got " + stats.miningSpeed());
        helper.assertTrue(Math.abs(stats.attackDamage() - 8.72F) < 0.001F,
                "expected manyullyn's 8.72 attack damage, got " + stats.attackDamage());

        helper.succeed();
    }

    /**
     * Repair with the head material's own ingot (issue #103): a cobalt pickaxe's head material is
     * cobalt, so {@code cobalt_ingot} is what {@code cobalt.json}'s {@code repair_item} (tag
     * {@code c:ingots/cobalt}) matches. {@code ToolRepair#repairIncrement}: one ingot restores
     * {@code max(780, maxDurability / 64)} = cobalt's own 780 head durability (repair count 0, no
     * diminishing returns yet).
     */
    @GameTest(template = "empty")
    public static void repairsWithTheHeadMaterialsOwnIngot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "cobalt", "cobalt", "cobalt");
        pickaxe.setDamageValue(1000);

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(ForgeweaveItems.INGOT_COBALT.get(), 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().getDamageValue() == 220,
                "expected 1000 - 780 = 220 damage left after repairing with one cobalt ingot, got "
                        + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().getDamageValue());

        helper.succeed();
    }

    /** Rose gold -&gt; {@code forgeweave:quick}: +25% mining speed, +20% attack speed. */
    @GameTest(template = "empty")
    public static void quickBoostsMiningAndAttackSpeed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "rose_gold", "rose_gold", "rose_gold");

        float speed = ForgeweaveTraits.miningSpeed(pickaxe, true, 10.0F);
        helper.assertTrue(Math.abs(speed - 12.5F) < 0.001F, "expected 10.0 * 1.25 = 12.5 mining speed, got " + speed);

        float attackSpeedBonus = ForgeweaveTraits.attackSpeedBonus(pickaxe);
        helper.assertTrue(Math.abs(attackSpeedBonus - 0.2F) < 0.001F,
                "expected a 20% attack speed bonus, got " + attackSpeedBonus);

        helper.succeed();
    }

    /**
     * Netherite -&gt; {@code forgeweave:reinforced_core}: a netherite-headed tool starts with
     * {@code DEFAULT_SLOTS + 1 = 4} free modifier slots, not the usual three.
     */
    @GameTest(template = "empty")
    public static void netheriteToolStartsWithFourModifierSlots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "netherite", "netherite", "netherite");

        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == ForgeweaveModifiers.DEFAULT_SLOTS + 1,
                "expected " + (ForgeweaveModifiers.DEFAULT_SLOTS + 1) + " free slots on a netherite tool, got "
                        + ForgeweaveModifiers.freeSlots(pickaxe));

        helper.succeed();
    }

    /**
     * The maintainer-decided second application recipe for the existing {@code extra_slot} modifier
     * (issue #103): a netherite ingot in the Tool Station's reagent slot applies it exactly like the
     * shipped extra-modifier item does, proving {@code ModifierApplication#recipeFor} already supports
     * two recipes naming the same modifier id (NOTICE.md).
     */
    @GameTest(template = "empty")
    public static void netheriteIngotAppliesTheExtraSlotModifier(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        int baseline = ForgeweaveModifiers.freeSlots(pickaxe);

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.NETHERITE_INGOT, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack widened = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertFalse(widened.isEmpty(), "expected the station to accept a netherite ingot as a reagent");

        helper.assertTrue(ForgeweaveModifiers.freeSlots(widened) == baseline + 1,
                "expected the netherite-ingot recipe to net +1 free slot like the extra-modifier item does, got "
                        + ForgeweaveModifiers.freeSlots(widened) + " (baseline " + baseline + ")");

        helper.succeed();
    }
}
