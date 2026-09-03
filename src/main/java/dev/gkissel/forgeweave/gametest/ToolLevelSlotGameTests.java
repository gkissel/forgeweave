package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.tool.ToolLevel;
import dev.gkissel.forgeweave.tool.ToolLeveling;

/**
 * Issue #921 (M7-4, docs/SCOPE.md D-M7-1): {@link ForgeweaveModifiers#freeSlots} sums a tool's
 * {@code tool_level} component's {@link ToolLevel#bonusSlots} as a third additive term, beside
 * {@link ForgeweaveModifiers#DEFAULT_SLOTS} and the existing modifier/trait bonus. The level state
 * here is written straight onto the stack's component -- the same fixture shape {@code
 * ModifierGameTests#withModifier} uses for a tool's modifier list -- rather than earned through the
 * mining/melee/ranged/utility XP grants M7-2/M7-3 add; those are out of scope for this ticket.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ToolLevelSlotGameTests {

    private static final ResourceLocation SEARING = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "searing");

    /**
     * A tool at level 1 shows exactly one more free slot than the same tool at level 0, and the
     * earned slot is spendable: filling every default slot first, then leveling once, then applying
     * a fourth modifier into the slot leveling just granted.
     */
    @GameTest(template = "empty")
    public static void levelOneGrantsOneMoreSlotThanLevelZeroAndItIsSpendable(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == ForgeweaveModifiers.DEFAULT_SLOTS,
                "a freshly assembled tool starts with " + ForgeweaveModifiers.DEFAULT_SLOTS + " free slots, got "
                        + ForgeweaveModifiers.freeSlots(pickaxe));

        ItemStack filled = applyReagent(helper, player, pos, pickaxe, new ItemStack(Items.MAGMA_CREAM));
        filled = applyReagent(helper, player, pos, filled, new ItemStack(Items.ENDER_PEARL));
        filled = applyReagent(helper, player, pos, filled, new ItemStack(Items.ECHO_SHARD));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(filled) == 0,
                "expected the three default slots full, got " + ForgeweaveModifiers.freeSlots(filled) + " free");

        filled.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), new ToolLevel(1, 0, 1));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(filled) == 1,
                "level 1 must grant exactly one more free slot than level 0, got "
                        + ForgeweaveModifiers.freeSlots(filled));

        ItemStack spent = applyReagent(helper, player, pos, filled, new ItemStack(Items.TURTLE_SCUTE));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(spent) == 0,
                "the level-earned slot must accept a fourth modifier, leaving 0 free, got "
                        + ForgeweaveModifiers.freeSlots(spent));

        helper.succeed();
    }

    /**
     * Three level-ups give three slots on top of whatever the tool already had spoken for; spending
     * two of them leaves one free, the same {@code occupiedSlots} accounting every other bonus-slot
     * source already uses.
     */
    @GameTest(template = "empty")
    public static void threeLevelUpsGiveThreeSlotsAndSpendingTwoLeavesOneFree(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ItemStack filled = applyReagent(helper, player, pos, pickaxe, new ItemStack(Items.MAGMA_CREAM));
        filled = applyReagent(helper, player, pos, filled, new ItemStack(Items.ENDER_PEARL));
        filled = applyReagent(helper, player, pos, filled, new ItemStack(Items.ECHO_SHARD));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(filled) == 0,
                "expected the three default slots full before leveling, got "
                        + ForgeweaveModifiers.freeSlots(filled) + " free");

        filled.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), new ToolLevel(3, 0, 3));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(filled) == 3,
                "three level-ups must grant three free slots, got " + ForgeweaveModifiers.freeSlots(filled));

        ItemStack spentTwo = applyReagent(helper, player, pos, filled, new ItemStack(Items.TURTLE_SCUTE));
        spentTwo = applyReagent(helper, player, pos, spentTwo, new ItemStack(Items.AMETHYST_SHARD));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(spentTwo) == 1,
                "spending two of the three level-granted slots must leave exactly one free, got "
                        + ForgeweaveModifiers.freeSlots(spentTwo));

        helper.succeed();
    }

    /**
     * D-M7-3: turning {@code toolLeveling} off gates new XP grants, never the slots a tool already
     * earned -- revoking them would invalidate a modifier already applied into one. This also pins
     * that {@link ToolLeveling#addXp} itself goes inert with the flag off, in a real server (unlike
     * {@code ToolLevelingTest}'s unit tests, whose {@code SERVER} config spec never loads).
     */
    @GameTest(template = "empty")
    public static void toolLevelingOffKeepsEarnedSlotsAndTheModifierInThem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), new ToolLevel(1, 0, 1));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == ForgeweaveModifiers.DEFAULT_SLOTS + 1,
                "expected the earned slot counted before the flag is touched, got "
                        + ForgeweaveModifiers.freeSlots(pickaxe));

        ItemStack leveled = applyReagent(helper, player, pos, pickaxe, new ItemStack(Items.MAGMA_CREAM));
        helper.assertTrue(ForgeweaveModifiers.entry(leveled, SEARING) != null,
                "expected searing applied into the earned slot");
        int freeBeforeFlagFlip = ForgeweaveModifiers.freeSlots(leveled);

        ForgeweaveConfig.TOOL_LEVELING.set(false);
        try {
            helper.assertTrue(ForgeweaveModifiers.freeSlots(leveled) == freeBeforeFlagFlip,
                    "the flag must not change freeSlots' count of an already-earned slot");
            helper.assertTrue(ForgeweaveModifiers.entry(leveled, SEARING) != null,
                    "a modifier already applied into an earned slot must keep working with the flag off");

            ToolLevel before = ToolLevel.of(leveled);
            boolean leveledUp = ToolLeveling.addXp(leveled, 1_000_000, null);
            helper.assertFalse(leveledUp, "addXp must report no level crossed while the flag is off");
            helper.assertTrue(ToolLevel.of(leveled).equals(before),
                    "addXp must leave the component untouched while the flag is off");
        } finally {
            ForgeweaveConfig.TOOL_LEVELING.set(true);
        }

        helper.succeed();
    }

    /**
     * Trait-granted (netherite's {@code reinforced_core}), modifier-granted ({@code extra_slot}) and
     * level-granted bonus slots all sum on top of {@link ForgeweaveModifiers#DEFAULT_SLOTS} on one
     * tool: {@code 3 + 1 (trait) + 1 (extra_slot net) + 1 (level) = 6}.
     */
    @GameTest(template = "empty")
    public static void traitModifierAndLevelBonusSlotsSumTogether(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "netherite", "netherite", "netherite");
        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == ForgeweaveModifiers.DEFAULT_SLOTS + 1,
                "expected reinforced_core's +1 trait slot alone, got " + ForgeweaveModifiers.freeSlots(pickaxe));

        ItemStack widened = applyReagent(helper, player, pos, pickaxe, new ItemStack(ForgeweaveItems.EXTRA_MODIFIER.get()));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(widened) == ForgeweaveModifiers.DEFAULT_SLOTS + 2,
                "expected trait (+1) and extra_slot's net (+1) together, got " + ForgeweaveModifiers.freeSlots(widened));

        widened.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), new ToolLevel(1, 0, 1));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(widened) == ForgeweaveModifiers.DEFAULT_SLOTS + 3,
                "expected trait (+1), extra_slot's net (+1) and the level (+1) all summed, got "
                        + ForgeweaveModifiers.freeSlots(widened));

        helper.succeed();
    }

    /** As {@code ModifierGameTests#applyReagent}: a real Tool Station craft, tool in slot 0, reagent in slot 1. */
    private static ItemStack applyReagent(GameTestHelper helper, Player player, BlockPos pos, ItemStack tool,
            ItemStack reagent) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, tool);
        blockEntity.container().setItem(1, reagent);
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "expected the station to produce a modified tool");
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }
}
