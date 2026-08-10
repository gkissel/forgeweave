package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Issue #105's verification: modifiers applied through the real Tool Station menu, against the
 * shipped {@code forgeweave:haste} recipe JSON (1 redstone per unit, cap 250 = upstream's 5 levels
 * of 50).
 *
 * <p>The slot model these pin down (docs/SCOPE.md M2 acceptance test 5, ADR-0004): three free slots,
 * one distinct modifier per slot, and levelling stays inside the slot the modifier already holds.
 * The codec round-trip and the datapack-retune coverage are unit tests
 * ({@code modifier.ModifierEntryTest}, {@code modifier.ModifierRecipeTest}) -- neither needs a world.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ModifierGameTests {

    private static final ResourceLocation HASTE = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "haste");
    // #108 batch: modern-vanilla modifiers (issue #108).
    private static final ResourceLocation SEARING = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "searing");
    private static final ResourceLocation MAGNETIC_PULL =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "magnetic_pull");
    private static final ResourceLocation RESONANT = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "resonant");

    @GameTest(template = "empty")
    public static void oneRedstoneFillsOneModifierSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == ForgeweaveModifiers.DEFAULT_SLOTS,
                "a freshly assembled tool starts with " + ForgeweaveModifiers.DEFAULT_SLOTS + " modifier slots, got "
                        + ForgeweaveModifiers.freeSlots(pickaxe));
        float baseSpeed = miningSpeed(pickaxe);

        ItemStack hasted = applyReagent(helper, player, pos, pickaxe, new ItemStack(Items.REDSTONE, 1));

        ModifierEntry entry = ForgeweaveModifiers.entry(hasted, HASTE);
        helper.assertTrue(entry != null && entry.level() == 1,
                "one redstone must record haste at level 1, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(hasted) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "applying a modifier must occupy exactly one slot, got "
                        + ForgeweaveModifiers.freeSlots(hasted) + " free");
        helper.assertTrue(miningSpeed(hasted) > baseSpeed,
                "haste must raise the mining speed the vanilla tool component carries: " + baseSpeed
                        + " -> " + miningSpeed(hasted));
        helper.succeed();
    }

    /**
     * Upstream 1.12 charges a fresh modifier slot per haste level; Forgeweave charges one for the
     * modifier's lifetime (the deliberate deviation recorded in issue #105's PR), so 60 redstone --
     * past the 50-per-level threshold -- must still leave the tool with two slots free and one entry.
     */
    @GameTest(template = "empty")
    public static void levellingUpStaysInsideTheSameSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ItemStack hasted = applyReagent(helper, player, pos, pickaxe, new ItemStack(Items.REDSTONE, 60));

        List<ModifierEntry> entries = ForgeweaveModifiers.of(hasted);
        helper.assertTrue(entries.size() == 1, "levelling must not add a second entry, got " + entries);
        helper.assertTrue(entries.get(0).level() == 60, "60 redstone must record 60 units, got " + entries.get(0));
        helper.assertTrue(ForgeweaveModifiers.displayLevel(HASTE, 60) == 2,
                "past 50 redstone haste displays as level 2");
        helper.assertTrue(ForgeweaveModifiers.freeSlots(hasted) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "levelling up must stay inside the slot the modifier already holds, got "
                        + ForgeweaveModifiers.freeSlots(hasted) + " free");
        helper.succeed();
    }

    /**
     * The cap. Three distinct modifiers fill the tool, so a fourth is refused -- and refused with a
     * message rather than silently, which is what the station's info panel shows
     * ({@code ToolStationMenu#modifierRejection}).
     *
     * <p>The three occupying ids are arbitrary: unimplemented ids are kept as inert data by design
     * ({@code ForgeweaveModifiers#get}), and they still occupy their slot, which is exactly the
     * situation this asserts. That keeps the test independent of which modifiers issues #106-#108
     * eventually ship.
     */
    @GameTest(template = "empty")
    public static void aFourthDistinctModifierIsRejected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "test_one"), 1),
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "test_two"), 1),
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "test_three"), 1)));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == 0,
                "three distinct modifiers must fill the tool, got "
                        + ForgeweaveModifiers.freeSlots(pickaxe) + " free");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a fourth distinct modifier must produce no output");
        helper.assertTrue(menu.modifierRejection() != null,
                "a refused application must tell the player why");
        helper.succeed();
    }

    /** Applying a levelled modifier past its cap is refused too, rather than silently wasting reagents. */
    @GameTest(template = "empty")
    public static void applyingPastTheLevelCapIsRejected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(HASTE, 250)));

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE, 8));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a capped modifier must produce no output");
        helper.assertTrue(menu.modifierRejection() != null, "a capped modifier must say so");
        helper.succeed();
    }

    /**
     * The station's two free slots take reagents as well as repair items, and taking the output
     * spends exactly the reagents the application used.
     */
    @GameTest(template = "empty")
    public static void theRepairSlotsAcceptReagentsAndSpendThem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);

        helper.assertTrue(menu.getSlot(ToolStationMenu.BINDING_SLOT).mayPlace(new ItemStack(Items.REDSTONE)),
                "the repair tab's free slots must accept a modifier reagent");
        helper.assertFalse(menu.getSlot(ToolStationMenu.BINDING_SLOT).mayPlace(new ItemStack(Items.DIAMOND)),
                "they must still reject an item that is neither a repair item nor a reagent");

        menu.getSlot(ToolStationMenu.BINDING_SLOT).set(new ItemStack(Items.REDSTONE, 3));
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected the modified pickaxe, got " + output);

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).getItem().isEmpty(),
                "taking the modified tool must clear the tool slot");
        helper.assertTrue(menu.getSlot(ToolStationMenu.BINDING_SLOT).getItem().isEmpty(),
                "all three redstone go into the tool, so the reagent slot must empty");
        helper.assertTrue(output.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()),
                "a modified tool must still harvest what it did before");
        helper.succeed();
    }

    // #108 batch: modern-vanilla modifiers (issue #108). Searing and Magnetic Pull key off NeoForge's
    // block-drops event ({@code ForgeweaveModifiers#onBlockDrops}), which needs a real
    // {@code ServerLevel} (Searing's furnace-recipe lookup) and a real {@code ServerPlayer} (Magnetic
    // Pull's inventory insert) -- both of which only a GameTest, not a plain unit test, provides.

    /** Searing (issue #108): a mined block's drop becomes its furnace-smelted result, count preserved. */
    @GameTest(template = "empty")
    public static void searingSmeltsWhatItMines(GameTestHelper helper) {
        ItemStack pickaxe = withModifier(SEARING);
        ItemEntity drop = drop(helper, new ItemStack(Items.IRON_ORE, 2));

        ForgeweaveModifiers.onBlockDrops(dropsEvent(helper, pickaxe, null, drop));

        helper.assertTrue(drop.getItem().is(Items.IRON_INGOT) && drop.getItem().getCount() == 2,
                "expected 2 iron ingot (the furnace result of iron ore), got " + drop.getItem());
        helper.succeed();
    }

    /** Magnetic Pull (issue #108): drops the player's inventory can hold go straight into it. */
    @GameTest(template = "empty")
    public static void magneticPullSendsDropsToTheBreakersInventory(GameTestHelper helper) {
        ItemStack pickaxe = withModifier(MAGNETIC_PULL);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemEntity drop = drop(helper, new ItemStack(Items.COBBLESTONE, 4));

        BlockDropsEvent event = dropsEvent(helper, pickaxe, player, drop);
        ForgeweaveModifiers.onBlockDrops(event);

        helper.assertTrue(event.getDrops().isEmpty(),
                "a drop the inventory can fully absorb must not spawn in the world");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.COBBLESTONE, 4)),
                "expected the cobblestone in the breaker's inventory instead of the ground");
        helper.succeed();
    }

    /** Resonant (issue #108): +50% dropped experience at level 1. */
    @GameTest(template = "empty")
    public static void resonantAddsBonusExperience(GameTestHelper helper) {
        ItemStack pickaxe = withModifier(RESONANT);
        BlockDropsEvent event = dropsEvent(helper, pickaxe, null, drop(helper, new ItemStack(Items.COAL)));
        event.setDroppedExperience(10);

        ForgeweaveModifiers.onBlockDrops(event);

        helper.assertTrue(event.getDroppedExperience() == 15,
                "expected 10 XP plus level 1's +50%, got " + event.getDroppedExperience());
        helper.succeed();
    }

    /** A bare pickaxe carrying one level of {@code id}; these three modifiers don't need an assembled tool. */
    private static ItemStack withModifier(ResourceLocation id) {
        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(id, 1)));
        return pickaxe;
    }

    private static ItemEntity drop(GameTestHelper helper, ItemStack stack) {
        return new ItemEntity(helper.getLevel(), 0, 0, 0, stack);
    }

    private static BlockDropsEvent dropsEvent(GameTestHelper helper, ItemStack tool, Entity breaker, ItemEntity... drops) {
        return new BlockDropsEvent(helper.getLevel(), BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null,
                new ArrayList<>(List.of(drops)), breaker, tool);
    }

    /** Runs one application through the station and returns the modified tool. */
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

    /** The mining speed the vanilla {@code tool} component actually carries, which is what mines blocks. */
    private static float miningSpeed(ItemStack stack) {
        return stack.get(DataComponents.TOOL).rules().stream()
                .flatMap(rule -> rule.speed().stream())
                .findFirst()
                .orElse(0.0F);
    }
}
