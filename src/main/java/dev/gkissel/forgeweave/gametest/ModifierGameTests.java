package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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
 * <p>The slot model these pin down (issue #344, ADR-0004): three free slots, and one slot charged
 * per modifier <em>level</em> exactly as upstream's {@code MultiAspect} does -- max Haste is five
 * slots, and the level that would exceed the budget is refused.
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
    private static final ResourceLocation LUCK = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "luck");
    // #107 batch: parity modifiers (issue #107).
    private static final ResourceLocation EXTRA_SLOT = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "extra_slot");

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
     * Issue #344's 1.12 parity: upstream charges a fresh modifier slot per haste level
     * ({@code ModifierAspect.MultiAspect#canApply} spends its {@code freeModifierAspect} every time
     * a new level starts), so 60 redstone -- past the 50-per-level threshold -- is one entry
     * occupying two slots.
     */
    @GameTest(template = "empty")
    public static void aSecondHasteLevelChargesASecondSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ItemStack hasted = applyReagent(helper, player, pos, pickaxe, new ItemStack(Items.REDSTONE, 60));

        List<ModifierEntry> entries = ForgeweaveModifiers.of(hasted);
        helper.assertTrue(entries.size() == 1, "levelling must not add a second entry, got " + entries);
        helper.assertTrue(entries.get(0).level() == 60, "60 redstone must record 60 units, got " + entries.get(0));
        helper.assertTrue(ForgeweaveModifiers.displayLevel(HASTE, 60) == 2,
                "past 50 redstone haste displays as level 2");
        helper.assertTrue(ForgeweaveModifiers.freeSlots(hasted) == ForgeweaveModifiers.DEFAULT_SLOTS - 2,
                "the second haste level must charge a second slot (upstream MultiAspect), got "
                        + ForgeweaveModifiers.freeSlots(hasted) + " free");
        helper.succeed();
    }

    /**
     * Issue #344's headline number: Haste III is three slots, filling a plain tool exactly. 150
     * units arrive as 16 nine-unit blocks plus 6 dust, since a single dust stack caps at 64.
     */
    @GameTest(template = "empty")
    public static void hasteThreeOccupiesThreeSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE_BLOCK, 16));
        blockEntity.container().setItem(2, new ItemStack(Items.REDSTONE, 6));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack hasted = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(hasted.isEmpty(), "16 blocks + 6 dust must produce a modified tool");

        ModifierEntry entry = ForgeweaveModifiers.entry(hasted, HASTE);
        helper.assertTrue(entry != null && entry.level() == 150,
                "16 blocks + 6 dust must record 150 units, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.displayLevel(HASTE, 150) == 3, "150 units is Haste III");
        helper.assertTrue(ForgeweaveModifiers.occupiedSlots(hasted) == 3,
                "Haste III must occupy three slots (one per level), got " + ForgeweaveModifiers.occupiedSlots(hasted));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(hasted) == 0,
                "three levels must fill a plain tool's three slots, got "
                        + ForgeweaveModifiers.freeSlots(hasted) + " free");
        helper.succeed();
    }

    /**
     * Issue #344's budget cap on levelling: upstream's {@code FreeModifierAspect#canApply} throws
     * {@code gui.error.not_enough_modifiers} the moment a unit would start a level with no free
     * modifier left, so the level that would exceed the budget is refused -- here a tool already at
     * Haste III on a plain three-slot budget takes no 151st redstone.
     */
    @GameTest(template = "empty")
    public static void aLevelPastTheSlotBudgetIsRefused(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(HASTE, 150)));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == 0,
                "the fixture needs zero free slots, got " + ForgeweaveModifiers.freeSlots(pickaxe));

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a fourth haste level must not start without a free slot, got "
                        + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem());
        helper.assertTrue(menu.rejection() != null, "and the station must say why");
        helper.succeed();
    }

    /**
     * Issue #344's apply-what-fits boundary, upstream's per-match rollback
     * ({@code ToolBuilder#tryModifyTool}): once an application has landed units this craft, the unit
     * that would start an unaffordable level rolls back and its reagents stay unconsumed -- 20
     * blocks and 10 dust against three free slots land exactly Haste III's 150 units (16 blocks + 6
     * dust) and leave the rest in the slots.
     */
    @GameTest(template = "empty")
    public static void unitsPastTheAffordableLevelAreLeftUnconsumed(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE_BLOCK, 20));
        blockEntity.container().setItem(2, new ItemStack(Items.REDSTONE, 10));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "the affordable three levels must still land");
        ModifierEntry entry = ForgeweaveModifiers.entry(output, HASTE);
        helper.assertTrue(entry != null && entry.level() == 150,
                "three free slots afford exactly 150 units, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(output) == 0,
                "and they land exactly full, got " + ForgeweaveModifiers.freeSlots(output) + " free");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(ToolStationMenu.BINDING_SLOT).getItem().getCount() == 4,
                "4 of the 20 blocks must stay unconsumed, got "
                        + menu.getSlot(ToolStationMenu.BINDING_SLOT).getItem());
        helper.assertTrue(menu.getSlot(ToolStationMenu.HANDLE_SLOT).getItem().getCount() == 4,
                "4 of the 10 dust must stay unconsumed, got "
                        + menu.getSlot(ToolStationMenu.HANDLE_SLOT).getItem());
        helper.succeed();
    }

    /**
     * The cap. Three distinct modifiers fill the tool, so a fourth is refused -- and refused with a
     * message rather than silently, which is what the station's info panel shows
     * ({@code ToolStationMenu#rejection}).
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
        helper.assertTrue(menu.rejection() != null,
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
        helper.assertTrue(menu.rejection() != null, "a capped modifier must say so");
        helper.succeed();
    }

    /**
     * Issue #259: a redstone block is worth 9 dust, upstream {@code TinkerModifiers}'
     * {@code modHaste.addItem("blockRedstone", 1, 9)} -- through the real station menu, against the
     * shipped haste JSON's {@code reagents} list. Dust-by-1 is {@link #oneRedstoneFillsOneModifierSlot}.
     */
    @GameTest(template = "empty")
    public static void oneRedstoneBlockAdvancesHasteByNine(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ItemStack hasted = applyReagent(helper, player, pos, pickaxe, new ItemStack(Items.REDSTONE_BLOCK, 1));

        ModifierEntry entry = ForgeweaveModifiers.entry(hasted, HASTE);
        helper.assertTrue(entry != null && entry.level() == 9,
                "one redstone block must record haste at 9 units, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(hasted) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "a block application still occupies exactly one slot, got "
                        + ForgeweaveModifiers.freeSlots(hasted) + " free");
        helper.succeed();
    }

    /**
     * Issue #259's cap behavior: at 245/250 a whole 9-unit block no longer fits and is refused
     * unconsumed (upstream's all-or-nothing {@code RecipeMatch} rollback), with a message -- while
     * dust keeps partial-filling the same 5-unit gap.
     */
    @GameTest(template = "empty")
    public static void aBlockThatOvershootsTheHasteCapIsRefused(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(HASTE, 245)));

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE_BLOCK, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a 9-unit block must not squeeze into 5 units of room");
        helper.assertTrue(menu.rejection() != null, "and the station must say why");

        // Dust still partial-fills the same gap to exactly 250.
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE, 8));
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertFalse(output.isEmpty(), "dust must still apply where a block cannot");
        ModifierEntry entry = ForgeweaveModifiers.entry(output, HASTE);
        helper.assertTrue(entry != null && entry.level() == 250,
                "dust must fill to the 250 cap exactly, got " + entry);
        helper.succeed();
    }

    /**
     * Issue #340: upstream 1.12's {@code ToolBuilder#tryModifyTool} iterates every registered
     * modifier against the whole input set (lines 176-223 at the pinned commit), so redstone in one
     * free slot and lapis in the other apply Haste and Luck together in one craft, each consuming its
     * own reagents.
     */
    @GameTest(template = "empty")
    public static void twoDifferentModifiersLandInOneCraft(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE, 2));
        blockEntity.container().setItem(2, new ItemStack(Items.LAPIS_LAZULI, 3));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "redstone + lapis must produce one modified tool, not nothing");
        ModifierEntry haste = ForgeweaveModifiers.entry(output, HASTE);
        ModifierEntry luck = ForgeweaveModifiers.entry(output, LUCK);
        helper.assertTrue(haste != null && haste.level() == 2,
                "2 redstone must record haste at 2 units in the same craft, got " + haste);
        helper.assertTrue(luck != null && luck.level() == 3,
                "3 lapis must record luck at 3 units in the same craft, got " + luck);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(output) == ForgeweaveModifiers.DEFAULT_SLOTS - 2,
                "two distinct modifiers must occupy two slots, got "
                        + ForgeweaveModifiers.freeSlots(output) + " free");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(ToolStationMenu.BINDING_SLOT).getItem().isEmpty(),
                "taking the output must spend the redstone");
        helper.assertTrue(menu.getSlot(ToolStationMenu.HANDLE_SLOT).getItem().isEmpty(),
                "taking the output must spend the lapis");
        helper.succeed();
    }

    /**
     * Issue #340's budget-exhaustion behavior, exactly upstream's: a <em>new</em> modifier whose
     * reagents are present but which no longer fits the slot budget rejects the whole craft --
     * {@code ModifierAspect.FreeModifierAspect#canApply} throws (ModifierAspect.java lines 61-67 at
     * the pinned commit) and {@code ToolBuilder#tryModifyTool} rethrows for a modifier not yet
     * applied this craft (lines 207-208), discarding the partial result. Not "apply what fits":
     * that path (lines 211-213) exists only for extra matches of an already-applied modifier.
     */
    @GameTest(template = "empty")
    public static void aSecondModifierPastTheSlotBudgetRejectsTheWholeCraft(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "test_one"), 1),
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "test_two"), 1)));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == 1,
                "the fixture needs exactly one free slot, got " + ForgeweaveModifiers.freeSlots(pickaxe));

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE, 1));
        blockEntity.container().setItem(2, new ItemStack(Items.LAPIS_LAZULI, 1));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "haste fits the last slot but luck does not, so the whole craft must be refused"
                        + " (upstream's either-all-or-none), got "
                        + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem());
        helper.assertTrue(menu.rejection() != null, "and the station must say why");
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
        // Dirt, not diamond (issue #106): diamond became a valid modifier reagent (forgeweave:diamond).
        helper.assertFalse(menu.getSlot(ToolStationMenu.BINDING_SLOT).mayPlace(new ItemStack(Items.DIRT)),
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

    /**
     * Issue #296: luck's growth-on-use, through the real {@code BlockDropsEvent} pipeline
     * ({@code ForgeweaveModifiers#onBlockDrops}) rather than calling the injectable roll directly --
     * the progress math and cap arithmetic themselves are unit-tested off a seeded
     * {@code RandomSource} in {@code ModifierBatch1Test}; this is the seam wiring.
     *
     * <p>Two halves, both exact rather than statistical:
     *
     * <ul>
     *   <li>Already at the shipped recipe's 360 cap: however many blocks break, the level must never
     *       move -- true on every roll outcome, so it needs no real randomness to be deterministic.
     *   <li>Below the cap: real per-break rolls are 3%, so this drives a bounded number of breaks
     *       (generous enough that missing every single one is astronomically unlikely, {@code
     *       0.97^2000 < 1e-25}) and asserts the level actually advanced by exactly one unit and no
     *       more -- if the block-break seam were never wired to the growth roll at all, this would fail
     *       on every run rather than flake, which is what makes it a real regression test.
     * </ul>
     */
    @GameTest(template = "empty")
    public static void luckGrowsFromBlockBreaksUpToTheRecipesCap(GameTestHelper helper) {
        ItemStack cappedPickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        cappedPickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(LUCK, 360)));
        for (int i = 0; i < 50; i++) {
            ForgeweaveModifiers.onBlockDrops(dropsEvent(helper, cappedPickaxe, null, drop(helper, new ItemStack(Items.COBBLESTONE))));
        }
        ModifierEntry cappedEntry = ForgeweaveModifiers.entry(cappedPickaxe, LUCK);
        helper.assertTrue(cappedEntry != null && cappedEntry.level() == 360,
                "luck already at the 360 cap must never grow further, got " + cappedEntry);

        ItemStack pickaxe = withModifier(LUCK);
        int breaks = 0;
        while (breaks < 2000 && ForgeweaveModifiers.entry(pickaxe, LUCK).level() == 1) {
            ForgeweaveModifiers.onBlockDrops(dropsEvent(helper, pickaxe, null, drop(helper, new ItemStack(Items.COBBLESTONE))));
            breaks++;
        }
        ModifierEntry entry = ForgeweaveModifiers.entry(pickaxe, LUCK);
        helper.assertTrue(entry != null && entry.level() == 2,
                "luck must grow by exactly one raw unit from block breaks, got " + entry + " after " + breaks + " breaks");
        helper.succeed();
    }

    /**
     * Issue #341: luck's Fortune is a real vanilla enchantment on the stack, so vanilla would render
     * the enchantment glint on any lapis-modified tool. Upstream 1.12 doesn't -- {@code
     * ToolCore#hasEffect} reports only the explicit enchant-effect flag (shocking's full charge), so a
     * modifier-granted enchantment never shimmers and the modifier's own texture overlay (luck's blue
     * dot, {@code ModifierOverlayModels}/{@code ModifierArt}) is the whole visual.
     */
    @GameTest(template = "empty")
    public static void aLapisModifiedToolCarriesFortuneWithoutGlinting(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        // 60 lapis is exactly the shipped luck.json's first level, so Fortune I actually lands.
        ItemStack lucky = applyReagent(helper, player, pos, pickaxe, new ItemStack(Items.LAPIS_LAZULI, 60));

        ItemEnchantments enchantments = lucky.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertTrue(enchantments.keySet().stream().anyMatch(holder -> holder.is(Enchantments.FORTUNE)),
                "60 lapis must grant real Fortune, got " + enchantments);
        helper.assertFalse(lucky.hasFoil(), "a modifier-granted enchantment must not make the tool glint");
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

    // #107 batch: parity modifiers (issue #107).

    /**
     * Issue #107's extra-slot modifier: docs/SCOPE.md's M2 gate names this scenario directly ("slot
     * cap + extra-slot"). One extra modifier item nets +1 free slot ({@code ForgeweaveModifiers
     * #EXTRA_SLOT}'s {@code bonusSlots} trap: it returns {@code level + 1} because its own entry
     * spends one of the three slots every tool starts with), so a tool that could take at most 3
     * distinct modifiers can take 4 after this application (its own entry plus the +1 it nets).
     */
    @GameTest(template = "empty")
    public static void anExtraSlotItemRaisesTheCap(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ItemStack widened = applyReagent(helper, player, pos, pickaxe, new ItemStack(ForgeweaveItems.EXTRA_MODIFIER.get(), 1));

        ModifierEntry entry = ForgeweaveModifiers.entry(widened, EXTRA_SLOT);
        helper.assertTrue(entry != null && entry.level() == 1,
                "one extra modifier item must record extra_slot at level 1, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(widened) == ForgeweaveModifiers.DEFAULT_SLOTS + 1,
                "one application must net exactly +1 free slot (its own entry spends one of its +2), got "
                        + ForgeweaveModifiers.freeSlots(widened) + " free");

        // Without extra_slot a tool fits exactly 3 distinct modifiers; with it applied (occupying one
        // of the widened cap's 4 slots itself) it fits 4 more on top -- a 4th distinct modifier that
        // would have been rejected before this application now fits.
        List<ModifierEntry> filled = new ArrayList<>(ForgeweaveModifiers.of(widened));
        filled.add(new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "test_one"), 1));
        filled.add(new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "test_two"), 1));
        filled.add(new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "test_three"), 1));
        filled.add(new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "test_four"), 1));
        widened.set(ForgeweaveDataComponents.MODIFIERS.get(), List.copyOf(filled));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(widened) == 0,
                "extra_slot plus four other distinct modifiers must exactly fill the widened cap, got "
                        + ForgeweaveModifiers.freeSlots(widened) + " free");
        helper.succeed();
    }

    /**
     * Issue #338 (maintainer playtest of 0.3.2-alpha, decision 2026-08-14): the extra modifier's
     * survival recipe was repriced from gold block + diamond to shapeless nether star + gold block --
     * too cheap for what it buys. Pins the new pairing through the real {@code RecipeManager} and
     * confirms the old pairing no longer resolves.
     */
    @GameTest(template = "empty")
    public static void theExtraModifierCraftsFromANetherStarAndGoldBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput repriced =
                CraftingInput.of(2, 1, List.of(new ItemStack(Items.NETHER_STAR), new ItemStack(Items.GOLD_BLOCK)));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, repriced, level)
                .map(match -> match.value().assemble(repriced, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveItems.EXTRA_MODIFIER.get()),
                "expected nether star + gold block to craft an extra modifier, got " + crafted);

        CraftingInput retired =
                CraftingInput.of(2, 1, List.of(new ItemStack(Items.GOLD_BLOCK), new ItemStack(Items.DIAMOND)));

        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, retired, level).isEmpty(),
                "gold block + diamond must no longer craft an extra modifier");

        helper.succeed();
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

    /**
     * Parity audit 2026-08-18 T2 (issue #434): upstream {@code ContainerToolStation#getInputs}
     * hands {@code ToolBuilder#tryModifyTool} every input slot but the tool's, so a reagent in any
     * of the five free slots applies. Redstone in slot 3 and lapis in slot 5 -- neither of the two
     * slots the pre-#434 resolver read -- must both land, and taking the output must spend both.
     */
    @GameTest(template = "empty")
    public static void reagentsInTheOuterFreeSlotsApply(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(3, new ItemStack(Items.REDSTONE, 2));
        blockEntity.container().setItem(5, new ItemStack(Items.LAPIS_LAZULI, 3));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "reagents in free slots 3 and 5 must produce a modified tool, got nothing"
                + (menu.rejection() != null ? " (" + menu.rejection().message().getString() + ")" : ""));
        ModifierEntry haste = ForgeweaveModifiers.entry(output, HASTE);
        ModifierEntry luck = ForgeweaveModifiers.entry(output, LUCK);
        helper.assertTrue(haste != null && haste.level() == 2, "slot 3's 2 redstone must land haste 2, got " + haste);
        helper.assertTrue(luck != null && luck.level() == 3, "slot 5's 3 lapis must land luck 3, got " + luck);

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(3).getItem().isEmpty(), "taking the output must spend slot 3's redstone");
        helper.assertTrue(menu.getSlot(5).getItem().isEmpty(), "taking the output must spend slot 5's lapis");
        helper.succeed();
    }

    /**
     * T2's pooling half for modifiers: one recipe's reagent spread over three of the five free slots
     * pools into a single application (upstream {@code RecipeMatch.Item#matches} sums the item's
     * count across every input stack), spending them slot-first.
     */
    @GameTest(template = "empty")
    public static void oneReagentSpreadOverThreeSlotsPools(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE, 1));
        blockEntity.container().setItem(3, new ItemStack(Items.REDSTONE, 1));
        blockEntity.container().setItem(4, new ItemStack(Items.REDSTONE, 1));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        ModifierEntry haste = ForgeweaveModifiers.entry(output, HASTE);
        helper.assertTrue(haste != null && haste.level() == 3,
                "1 + 1 + 1 redstone across slots 1, 3 and 4 must pool into haste 3, got " + haste);
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        for (int slot : new int[] {1, 3, 4}) {
            helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "slot " + slot + "'s redstone must be spent");
        }
        helper.succeed();
    }
}
