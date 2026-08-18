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
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.tool.ToolRepair;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Parity audit T30 (issue #461): upstream 1.12 repairs a tool with <em>any</em> item its head
 * material is registered with, each worth its own material value -- {@code TinkersItem#repair} calls
 * {@code Material#matches}, which is the very same {@code RecipeMatchRegistry} the Part Builder
 * matches against ({@code library/materials/Material.java}, {@code extends RecipeMatchRegistry}), and
 * {@code calculateRepairAmount} then scales the heal by {@code match.amount / VALUE_Ingot}. So a log
 * ({@code VALUE_Ingot * 4}) is worth four planks and a wood stick ({@code VALUE_Shard}) half a plank.
 * Forgeweave matched only the material's single {@code repair_item} {@code Ingredient} at a flat one
 * ingot-equivalent, so a log or a stick was not a repair item at all.
 *
 * <p>The fixture is an all-wood pickaxe, fully damaged with {@link #WORN} repairs already behind it
 * -- the same trick {@link MultiPartRepairGameTests} uses, and for the same reason: upstream's
 * diminishing-returns term bottoms out at half value, which is what makes a round land inside a
 * small tool's durability pool instead of clamping at zero damage. Wood's only trait is
 * {@code forgeweave:ecological}, which does not touch repair, so the arithmetic below is the repair
 * formula alone.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CraftingItemRepairGameTests {

    /** Head durability of {@code forgeweave:wood}, from its material JSON. */
    private static final int WOOD_HEAD_DURABILITY = 35;

    /** Enough past repairs to pin the diminishing-returns term at its 0.5 floor. */
    private static final int WORN = 100;

    private static ItemStack damagedWoodPickaxe(GameTestHelper helper, Player player, BlockPos pos) {
        ItemStack tool = ToolAssembly.pickaxe(helper, player, pos, "wood", "wood", "wood");
        tool.set(ForgeweaveDataComponents.REPAIR_COUNT.get(), WORN);
        tool.setDamageValue(tool.getMaxDamage());
        return tool;
    }

    /** Loads the station with a tool plus one free-slot input and returns the menu it opens. */
    private static ToolStationMenu load(GameTestHelper helper, Player player, BlockPos pos, ItemStack tool,
            ItemStack input) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, tool);
        blockEntity.container().setItem(1, input);
        for (int i = 2; i < ToolStationMenu.INPUT_SLOTS; i++) {
            blockEntity.container().setItem(i, ItemStack.EMPTY);
        }
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        return menu;
    }

    /**
     * The damage a single repair item worth {@code valueUnits} of wood leaves on {@code tool}:
     * upstream's {@code headDurability * match.amount / VALUE_Ingot}, in the same unit
     * ({@code PartBuilderRecipes.INGOT_VALUE = 144}, T58 / issue #489).
     */
    private static int damageAfterOne(ItemStack tool, int valueUnits) {
        ToolStats.Stats stats = tool.get(ForgeweaveDataComponents.TOOL_STATS.get());
        int amount = ToolRepair.repairAmount(
                WOOD_HEAD_DURABILITY * valueUnits / (float) PartBuilderRecipes.INGOT_VALUE, 1);
        int increment = ToolAssemblyRecipes.repairIncrement(
                amount, stats.durability(), tool.getMaxDamage(), WORN, 0, false);
        return tool.getMaxDamage() - increment;
    }

    private static void assertRepairedTo(GameTestHelper helper, ItemStack tool, ItemStack repaired, int expected) {
        helper.assertTrue(expected > 0 && expected < tool.getMaxDamage(),
                "the test needs a repair that neither vanishes nor clamps, got " + expected + " damage left");
        helper.assertTrue(repaired.is(tool.getItem()), "expected the repaired pickaxe, got " + repaired);
        helper.assertTrue(repaired.getDamageValue() == expected,
                "expected " + expected + " damage left, got " + repaired.getDamageValue());
    }

    /**
     * A plank is wood's {@code repair_item} <em>and</em> a {@code crafting_items} entry at
     * one ingot, so the pre-#461 heal is exactly what the value-scaled path still
     * gives. The regression guard for every other test here.
     */
    @GameTest(template = "empty")
    public static void aPlankStillRepairsOneIngotWorth(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = damagedWoodPickaxe(helper, player, pos);

        ToolStationMenu menu = load(helper, player, pos, pickaxe, new ItemStack(Items.OAK_PLANKS, 1));

        assertRepairedTo(helper, pickaxe, menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem(),
                damageAfterOne(pickaxe, PartBuilderRecipes.INGOT_VALUE));
        helper.succeed();
    }

    /**
     * Upstream {@code wood.addItem("logWood", 1, Material.VALUE_Ingot * 4)}: one log repairs as much
     * as four planks. Before #461 a log was not in {@code #minecraft:planks} and so was no repair
     * item at all -- the station produced nothing.
     */
    @GameTest(template = "empty")
    public static void aLogRepairsFourPlanksWorth(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = damagedWoodPickaxe(helper, player, pos);

        ToolStationMenu menu = load(helper, player, pos, pickaxe, new ItemStack(Items.OAK_LOG, 1));

        assertRepairedTo(helper, pickaxe, menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem(),
                damageAfterOne(pickaxe, 4 * PartBuilderRecipes.INGOT_VALUE));
        helper.succeed();
    }

    /**
     * Upstream {@code wood.addItem("stickWood", 1, Material.VALUE_Shard)}: a stick is worth half a
     * plank, the finest granularity upstream prices anything at. Also the proof that the scaling is a
     * real ratio and not "any crafting item pays one ingot".
     */
    @GameTest(template = "empty")
    public static void aStickRepairsHalfAPlanksWorth(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = damagedWoodPickaxe(helper, player, pos);

        ToolStationMenu menu = load(helper, player, pos, pickaxe, new ItemStack(Items.STICK, 1));

        assertRepairedTo(helper, pickaxe, menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem(),
                damageAfterOne(pickaxe, PartBuilderRecipes.SHARD_VALUE));
        helper.succeed();
    }

    /**
     * Upstream registers a shard of the material on every material it creates one for
     * ({@code TinkerMaterials#registerToolMaterialShards}'s {@code addRecipeMatch(new
     * RecipeMatch.ItemCombination(VALUE_Shard, shard))}), so a leftover Part Builder shard repairs
     * too -- at the same half-plank a stick is worth.
     */
    @GameTest(template = "empty")
    public static void aShardOfTheMaterialRepairs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = damagedWoodPickaxe(helper, player, pos);

        ItemStack shard = new ItemStack(ForgeweaveItems.SHARD.get(), 1);
        shard.set(ForgeweaveDataComponents.MATERIAL.get(),
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood"));
        ToolStationMenu menu = load(helper, player, pos, pickaxe, shard);

        assertRepairedTo(helper, pickaxe, menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem(),
                damageAfterOne(pickaxe, PartBuilderRecipes.SHARD_VALUE));
        helper.succeed();
    }

    /**
     * A shard of some <em>other</em> material is still not a repair item, so the loadout is no repair
     * at all and falls through to the modifier path's explained refusal -- the guard that the
     * crafting-item widening did not turn the repair tab into "anything goes".
     */
    @GameTest(template = "empty")
    public static void aShardOfAnotherMaterialDoesNotRepair(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = damagedWoodPickaxe(helper, player, pos);

        ItemStack shard = new ItemStack(ForgeweaveItems.SHARD.get(), 1);
        shard.set(ForgeweaveDataComponents.MATERIAL.get(),
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "flint"));
        ToolStationMenu menu = load(helper, player, pos, pickaxe, shard);

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a foreign material's shard must not repair, got "
                        + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem());
        helper.succeed();
    }

    /**
     * {@code cast_only} gates the Part Builder, not repair: upstream's {@code Material#isCraftable}
     * is only consulted by {@code ToolBuilder#tryBuildToolPart}, while {@code TinkersItem#repair}
     * goes straight to {@code Material#matches}. So an iron block -- iron is {@code cast_only} in
     * Forgeweave -- repairs an iron-headed tool at nine ingots, which on a pool this small is a full
     * heal. Before #461 only {@code #c:ingots/iron} was taken.
     */
    @GameTest(template = "empty")
    public static void aStorageBlockOfACastOnlyMetalRepairs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "iron", "wood", "wood");
        pickaxe.set(ForgeweaveDataComponents.REPAIR_COUNT.get(), WORN);
        pickaxe.setDamageValue(pickaxe.getMaxDamage());

        ToolStationMenu menu = load(helper, player, pos, pickaxe, new ItemStack(Items.IRON_BLOCK, 1));

        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(repaired.is(pickaxe.getItem()), "expected the repaired pickaxe, got " + repaired);
        helper.assertTrue(repaired.getDamageValue() == 0,
                "nine ingots' worth of iron should fully heal this pool, got " + repaired.getDamageValue()
                        + " damage left");
        helper.succeed();
    }
}
