package dev.gkissel.forgeweave.gametest;

import java.util.List;

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
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.tool.ToolRepair;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Parity audit T31 (issue #462): upstream 1.12's {@code TinkersItem#getRepairParts()} /
 * {@code #getRepairModifierForPart(int)} pair, which Forgeweave had collapsed to "the first HEAD
 * slot, factor 1". Three things had to become true, and each has a test here:
 *
 * <ol>
 *   <li>a repair can be paid in <em>any</em> repair slot's material, not only the head's -- both of
 *       the mattock's heads, since upstream {@code Mattock#getRepairParts()} is {@code {1, 2}};
 *   <li>paying several distinct materials in one round adds upstream's {@code 1/9}-per-extra-material
 *       bonus, spends exactly one of each, and counts as one repair;
 *   <li>a slot's repair modifier reaches the durability math -- the rapier's {@code 0.8x} is the only
 *       upstream factor below {@code 1}, so it is the one that cannot be mistaken for the old
 *       behavior.
 * </ol>
 *
 * <p>The mattock's parts are (handle, axe head, shovel head), so a flint axe head and a bone shovel
 * head give two distinct repair materials on a Tool Station-tier tool. Neither flint, bone, iron nor
 * wood carries a repair-bonus trait (only stone's {@code cheap} does), so the damage arithmetic below
 * is the repair formula alone.
 *
 * <p>Every tool here starts fully damaged and with {@link #WORN} repairs already behind it. That is
 * not incidental: one repair round is worth roughly a whole durability pool on these small tools, so
 * at full value every repair would clamp to zero damage and no arithmetic would be observable.
 * Upstream's diminishing-returns term bottoms out at half value ({@code TinkersItem#calculateRepair}),
 * which is exactly the dial that makes a round land inside the pool.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MultiPartRepairGameTests {

    /** Head durability of {@code forgeweave:flint}, from its material JSON. */
    private static final int FLINT_HEAD_DURABILITY = 150;

    /** Head durability of {@code forgeweave:bone}. */
    private static final int BONE_HEAD_DURABILITY = 200;

    /** Head durability of {@code forgeweave:iron}, for the rapier below. */
    private static final int IRON_HEAD_DURABILITY = 204;

    /** Enough past repairs to pin the diminishing-returns term at its 0.5 floor -- see the class javadoc. */
    private static final int WORN = 100;

    /** A fully damaged, well-worn mattock with a flint axe head and a bone shovel head. */
    private static ItemStack damagedMattock(GameTestHelper helper, Player player, BlockPos pos) {
        return worn(ToolAssembly.assemble(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_MATTOCK.get()), List.of("wood", "flint", "bone")));
    }

    private static ItemStack worn(ItemStack tool) {
        tool.set(ForgeweaveDataComponents.REPAIR_COUNT.get(), WORN);
        tool.setDamageValue(tool.getMaxDamage());
        return tool;
    }

    /** What one round of repair worth {@code amount} restores on {@code tool} at a Tool Station. */
    private static int incrementFor(ItemStack tool, int amount) {
        ToolStats.Stats stats = tool.get(ForgeweaveDataComponents.TOOL_STATS.get());
        return ToolAssemblyRecipes.repairIncrement(amount, stats.durability(), tool.getMaxDamage(), WORN, 0, false);
    }

    /** Loads the station with a tool and up to five free-slot inputs and returns the menu it opens. */
    private static ToolStationMenu load(GameTestHelper helper, Player player, BlockPos pos, ItemStack tool,
            ItemStack... inputs) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, tool);
        for (int i = 1; i < ToolStationMenu.INPUT_SLOTS; i++) {
            blockEntity.container().setItem(i, i - 1 < inputs.length ? inputs[i - 1] : ItemStack.EMPTY);
        }
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        return menu;
    }

    private static void assertRepairedBy(GameTestHelper helper, ItemStack tool, ItemStack repaired, int expected) {
        helper.assertTrue(expected > 0 && expected < tool.getMaxDamage(),
                "the test needs a repair that neither vanishes nor clamps, got " + expected);
        helper.assertTrue(repaired.is(tool.getItem()), "expected the repaired tool, got " + repaired);
        helper.assertTrue(repaired.getDamageValue() == tool.getMaxDamage() - expected,
                "expected " + tool.getMaxDamage() + " - " + expected + " damage left, got " + repaired.getDamageValue());
    }

    /**
     * Upstream {@code Mattock#getRepairParts()} is {@code {1, 2}}: both heads. Before #462 a mattock
     * only accepted its <em>first</em> head's material, so the bone below was not a repair item at
     * all and the station produced nothing.
     */
    @GameTest(template = "empty")
    public static void aSecondHeadSlotsMaterialRepairsTheTool(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = damagedMattock(helper, player, pos);

        int expected = incrementFor(mattock, ToolRepair.repairAmount(BONE_HEAD_DURABILITY, 1));
        ToolStationMenu menu = load(helper, player, pos, mattock, new ItemStack(Items.BONE, 1));

        assertRepairedBy(helper, mattock, menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem(), expected);
        helper.succeed();
    }

    /** The first head slot still repairs, at its own material's durability and not the second's. */
    @GameTest(template = "empty")
    public static void theFirstHeadSlotsMaterialStillRepairsAlone(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = damagedMattock(helper, player, pos);

        int expected = incrementFor(mattock, ToolRepair.repairAmount(FLINT_HEAD_DURABILITY, 1));
        ToolStationMenu menu = load(helper, player, pos, mattock, new ItemStack(Items.FLINT, 1));

        assertRepairedBy(helper, mattock, menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem(), expected);
        helper.succeed();
    }

    /**
     * Upstream {@code TinkersItem#calculateRepairAmount} pays one item of every distinct repair
     * material in one round and multiplies the whole sum by {@code 1 + (matched - 1) / 9}. One flint
     * plus one bone is therefore worth strictly more than their plain sum, both slots give up exactly
     * one item, and the tool's repair count goes up by one -- a round is one repair, not one per item.
     */
    @GameTest(template = "empty")
    public static void payingTwoMaterialsAtOnceEarnsTheMultiMaterialBonus(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = damagedMattock(helper, player, pos);

        int both = ToolRepair.repairAmount(FLINT_HEAD_DURABILITY + BONE_HEAD_DURABILITY, 2);
        helper.assertTrue(both == 388, "expected floor(350 * 10/9) = 388, got " + both);
        int expected = incrementFor(mattock, both);
        helper.assertTrue(expected > incrementFor(mattock, ToolRepair.repairAmount(FLINT_HEAD_DURABILITY, 1))
                        + incrementFor(mattock, ToolRepair.repairAmount(BONE_HEAD_DURABILITY, 1)),
                "two materials in one round must beat the same two spent one at a time");

        ToolStationMenu menu = load(helper, player, pos, mattock,
                new ItemStack(Items.FLINT, 1), new ItemStack(Items.BONE, 1));
        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();

        assertRepairedBy(helper, mattock, repaired, expected);
        helper.assertTrue(repaired.getOrDefault(ForgeweaveDataComponents.REPAIR_COUNT.get(), 0) == WORN + 1,
                "one round of several materials is one repair, got "
                        + repaired.getOrDefault(ForgeweaveDataComponents.REPAIR_COUNT.get(), 0));

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, repaired);
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "the flint must be spent");
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "the bone must be spent");
        helper.succeed();
    }

    /**
     * Upstream {@code Rapier#getRepairModifierForPart} returns its {@code DURABILITY_MODIFIER} of
     * {@code 0.8}, the only factor below {@code 1} in the tree -- so an iron rapier repairs for
     * {@code floor(204 * 0.8) = 163}, not the 204 an iron head is worth on every other tool.
     */
    @GameTest(template = "empty")
    public static void theRapiersRepairModifierIsBelowOne(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack rapier = worn(ToolAssembly.assemble(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_RAPIER.get()), List.of("wood", "iron", "wood")));

        int amount = ToolRepair.repairAmount(IRON_HEAD_DURABILITY * 0.8f, 1);
        helper.assertTrue(amount == 163, "expected floor(204 * 0.8) = 163, got " + amount);
        int expected = incrementFor(rapier, amount);
        helper.assertTrue(expected < incrementFor(rapier, IRON_HEAD_DURABILITY),
                "the rapier's 0.8x must cost it durability against an unmodified repair");

        ToolStationMenu menu = load(helper, player, pos, rapier, new ItemStack(Items.IRON_INGOT, 1));

        assertRepairedBy(helper, rapier, menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem(), expected);
        helper.succeed();
    }
}
