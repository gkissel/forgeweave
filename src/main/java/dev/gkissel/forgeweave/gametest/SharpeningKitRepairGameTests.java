package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
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
 * Parity audit T32 (issue #463): upstream 1.12 spends a sharpening kit of a repair slot's own
 * material as a repair item worth two ingot-equivalents, both at the Tool Station
 * ({@code ToolCore#repairCustom}, reached from {@code TinkersItem#calculateRepairAmount}) and in the
 * crafting grid ({@code tools/common/RepairRecipe}). Forgeweave only ever used the kit as
 * Fortification's reagent, so both paths are new here.
 *
 * <p>The mattock is the same fixture {@link MultiPartRepairGameTests} uses and for the same reasons:
 * its parts are (wood handle, flint axe head, bone shovel head), so it has two distinct repair
 * materials at factor {@code 1}, and starting it fully damaged with {@link #WORN} repairs behind it
 * pins the diminishing-returns term at its {@code 0.5} floor so a round lands inside the pool.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SharpeningKitRepairGameTests {

    /** Head durability of {@code forgeweave:flint}, from its material JSON. */
    private static final int FLINT_HEAD_DURABILITY = 150;

    /** Enough past repairs to pin the diminishing-returns term at its 0.5 floor. */
    private static final int WORN = 100;

    /**
     * What one sharpening kit is worth, in repair items: upstream prices the part at
     * {@code Material.VALUE_Shard * 4 == 2 * VALUE_Ingot} and {@code ToolCore#repairCustom} divides
     * that cost by {@code VALUE_Ingot}.
     */
    private static final float KIT = 2f;

    private static ItemStack damagedMattock(GameTestHelper helper, Player player, BlockPos pos) {
        ItemStack tool = ToolAssembly.assemble(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_MATTOCK.get()), List.of("wood", "flint", "bone"));
        tool.set(ForgeweaveDataComponents.REPAIR_COUNT.get(), WORN);
        tool.setDamageValue(tool.getMaxDamage());
        return tool;
    }

    private static ItemStack kit(String material) {
        return ToolAssembly.part(ForgeweaveItems.PART_SHARPENING_KIT.get(), material);
    }

    /** What one round of repair worth {@code amount} restores on {@code tool} at a Tool Station. */
    private static int incrementFor(ItemStack tool, int amount) {
        ToolStats.Stats stats = tool.get(ForgeweaveDataComponents.TOOL_STATS.get());
        return ToolAssemblyRecipes.repairIncrement(amount, stats.durability(), tool.getMaxDamage(), WORN, 0, false);
    }

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

    /**
     * Upstream {@code ToolCore#repairCustom}: a kit of the repair slot's material restores that
     * slot's head durability times the kit's two-ingot cost. It is <em>not</em> counted in
     * {@code calculateRepairAmount}'s {@code materialsMatched} set, which is only fed by the ordinary
     * {@code Material#matches} branch -- so a kit-only repair goes through the multi-material term
     * with a count of zero and comes out at {@code 1 - 1/9} of the kit's face value. That is
     * upstream's arithmetic verbatim, quirk included.
     */
    @GameTest(template = "empty")
    public static void aSharpeningKitOfARepairSlotsMaterialRepairsTheTool(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = damagedMattock(helper, player, pos);

        int expected = incrementFor(mattock, ToolRepair.repairAmount(FLINT_HEAD_DURABILITY * KIT, 0));
        ToolStationMenu menu = load(helper, player, pos, mattock, kit("flint"));
        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();

        helper.assertTrue(expected > 0 && expected < mattock.getMaxDamage(),
                "the test needs a repair that neither vanishes nor clamps, got " + expected);
        helper.assertTrue(repaired.is(mattock.getItem()), "expected the repaired mattock, got " + repaired);
        helper.assertTrue(repaired.getDamageValue() == mattock.getMaxDamage() - expected,
                "expected " + (mattock.getMaxDamage() - expected) + " damage left, got " + repaired.getDamageValue());
        helper.succeed();
    }

    /**
     * Upstream {@code ToolCore#repairCustom} returns {@code 0} unless every matched kit carries the
     * repair slot's own material, and {@code TinkersItem#repair}'s "check if all items were used"
     * bail then throws the whole loadout out. An iron kit is no repair item for a flint-and-bone
     * mattock, so the station has nothing to offer.
     */
    @GameTest(template = "empty")
    public static void aSharpeningKitOfAnotherMaterialRepairsNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = damagedMattock(helper, player, pos);

        ToolStationMenu menu = load(helper, player, pos, mattock, kit("iron"));

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a kit of a material the mattock does not repair with should produce nothing, got "
                        + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem());
        helper.succeed();
    }

    /**
     * Upstream runs the kit branch and the ordinary repair-item branch of a repair slot in the same
     * pass of {@code calculateRepairAmount}, so a kit and a flint spend together in one round rather
     * than in two -- one bump of the repair count, not two.
     */
    @GameTest(template = "empty")
    public static void aKitAndTheMaterialsRepairItemSpendInOneRound(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = damagedMattock(helper, player, pos);

        int kitOnly = load(helper, player, pos, mattock, kit("flint"))
                .getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().getDamageValue();
        ItemStack both = load(helper, player, pos, mattock, kit("flint"), new ItemStack(Items.FLINT, 1))
                .getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();

        helper.assertTrue(both.getDamageValue() < kitOnly,
                "a kit plus a flint should repair more than the kit alone, got " + both.getDamageValue()
                        + " vs " + kitOnly);
        helper.assertTrue(both.getOrDefault(ForgeweaveDataComponents.REPAIR_COUNT.get(), 0) == WORN + 1,
                "the kit and the flint should be one repair round, got "
                        + both.getOrDefault(ForgeweaveDataComponents.REPAIR_COUNT.get(), 0) + " repairs");
        helper.succeed();
    }

    /**
     * Upstream {@code tools/common/RepairRecipe}: the crafting grid repairs a tool with sharpening
     * kits, and with nothing else -- its {@code repairItems} set holds the kit alone.
     */
    @GameTest(template = "empty")
    public static void theCraftingGridRepairsAToolWithASharpeningKit(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = damagedMattock(helper, player, pos);

        ItemStack crafted = craft(helper, mattock, kit("flint"));

        helper.assertTrue(crafted.is(mattock.getItem()), "expected the repaired mattock, got " + crafted);
        helper.assertTrue(crafted.getDamageValue() < mattock.getDamageValue(),
                "the grid should have repaired the mattock, got " + crafted.getDamageValue() + " damage");
        helper.succeed();
    }

    /** Upstream's {@code else return ItemStack.EMPTY}: anything that is not a kit is not this recipe. */
    @GameTest(template = "empty")
    public static void theCraftingGridRepairRefusesAnythingButKits(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = damagedMattock(helper, player, pos);

        helper.assertTrue(craft(helper, mattock, kit("flint"), new ItemStack(Items.FLINT, 1)).isEmpty(),
                "the grid recipe takes sharpening kits only");
        helper.assertTrue(craft(helper, mattock, kit("iron")).isEmpty(),
                "a kit of a material the mattock does not repair with is not a grid repair either");
        helper.succeed();
    }

    /** Runs {@code inputs} plus the tool through the real recipe manager as a 3x3 crafting grid. */
    private static ItemStack craft(GameTestHelper helper, ItemStack tool, ItemStack... inputs) {
        ServerLevel level = helper.getLevel();
        List<ItemStack> grid = new ArrayList<>(9);
        grid.add(tool.copy());
        for (ItemStack input : inputs) {
            grid.add(input);
        }
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }
}
