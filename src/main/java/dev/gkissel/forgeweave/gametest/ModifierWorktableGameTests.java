package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ModifierWorktableBlockEntity;
import dev.gkissel.forgeweave.compat.apotheosis.ApotheosisSockets;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.menu.ModifierWorktableMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.modifier.Worktable;
import dev.gkissel.forgeweave.modifier.WorktableRecipe;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #1057's verification: the Modifier Worktable's two functions, end to end through the menu
 * where the reagent accounting matters and through {@link Worktable} where only the arithmetic does.
 *
 * <p>Clone pinned at {@code b98c7867}: {@code tools/recipe/ModifierRemovalRecipe.java},
 * {@code tools/recipe/ModifierSortingRecipe.java}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ModifierWorktableGameTests {

    private static final BlockPos WORKTABLE = new BlockPos(1, 1, 1);
    private static final BlockPos STATION = new BlockPos(2, 1, 1);

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private static HolderLookup.Provider registries(GameTestHelper helper) {
        return helper.getLevel().registryAccess();
    }

    /** The worktable, placed and loaded with a tool and up to two reagents. */
    private static ModifierWorktableMenu load(GameTestHelper helper, Player player, ItemStack tool,
            ItemStack first, ItemStack second) {
        helper.setBlock(WORKTABLE, ForgeweaveBlocks.MODIFIER_WORKTABLE.get());
        ModifierWorktableBlockEntity blockEntity = helper.getBlockEntity(WORKTABLE);
        blockEntity.container().setItem(Worktable.TOOL_SLOT, tool);
        blockEntity.container().setItem(Worktable.INPUT_START, first);
        blockEntity.container().setItem(Worktable.INPUT_START + 1, second);
        ModifierWorktableMenu menu = new ModifierWorktableMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(WORKTABLE)), null);
        menu.broadcastChanges();
        return menu;
    }

    /** A tool carrying exactly {@code entries}, baked the way an application leaves it. */
    private static ItemStack withModifiers(ItemStack tool, ModifierEntry... entries) {
        ItemStack copy = tool.copy();
        copy.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(entries));
        ModifierApplication.rebake(copy);
        return copy;
    }

    private static ModifierEntry entry(String path, int level) {
        return new ModifierEntry(id(path), level);
    }

    private static List<ResourceLocation> ids(ItemStack tool) {
        return ForgeweaveModifiers.of(tool).stream().map(ModifierEntry::id).toList();
    }

    /** Picks the button for {@code path}, takes the result, and hands back the tool that came out. */
    private static ItemStack craft(GameTestHelper helper, Player player, ModifierWorktableMenu menu, String path) {
        int index = menu.options().stream().map(ModifierEntry::id).toList().indexOf(id(path));
        helper.assertTrue(index >= 0, "the worktable must offer " + path + ", got " + menu.options());
        menu.clickMenuButton(player, index);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ModifierWorktableMenu.RESULT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "the worktable must produce a result for " + path);
        menu.getSlot(ModifierWorktableMenu.RESULT_SLOT).onTake(player, output);
        return output;
    }

    // ------------------------------------------------------------------ removal

    /**
     * The headline case: a plain one-slot modifier comes off whole, the slot it held comes back, and
     * the wet sponge comes back dry (upstream's {@code remove_modifier_sponge.json} leftovers).
     */
    @GameTest(template = "empty")
    public static void removingAPlainModifierGivesBackItsSlotAndADrySponge(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = withModifiers(
                ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood"), entry("silky", 1));
        int freeBefore = ForgeweaveModifiers.freeSlots(pickaxe);

        ModifierWorktableMenu menu = load(helper, player, pickaxe,
                new ItemStack(Items.WET_SPONGE), ItemStack.EMPTY);
        ItemStack stripped = craft(helper, player, menu, "silky");

        helper.assertTrue(ForgeweaveModifiers.of(stripped).isEmpty(),
                "removing the only modifier must empty the list, got " + ForgeweaveModifiers.of(stripped));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(stripped) == freeBefore + 1,
                "the slot silky occupied must come back: " + freeBefore + " -> "
                        + ForgeweaveModifiers.freeSlots(stripped));
        helper.assertTrue(menu.getSlot(ModifierWorktableMenu.TOOL_SLOT).getItem().isEmpty(),
                "taking the result must clear the tool slot");
        helper.assertTrue(menu.getSlot(ModifierWorktableMenu.INPUT_START).getItem().isEmpty(),
                "the wet sponge must be spent");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.SPONGE)),
                "the wet sponge must come back dry");
        helper.succeed();
    }

    /**
     * An incremental modifier loses a whole level's worth of units, not one unit -- haste's 50 --
     * and gives back the one slot that level occupied while keeping the rest.
     */
    @GameTest(template = "empty")
    public static void removingAnIncrementalModifierTakesAWholeLevelsUnits(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = withModifiers(
                ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood"), entry("haste", 60));
        int freeBefore = ForgeweaveModifiers.freeSlots(pickaxe);
        helper.assertTrue(ForgeweaveModifiers.displayLevel(id("haste"), 60) == 2,
                "60 redstone is haste II, which is the two-slot state this test is about");

        ModifierWorktableMenu menu = load(helper, player, pickaxe,
                new ItemStack(Items.WET_SPONGE), ItemStack.EMPTY);
        ItemStack stripped = craft(helper, player, menu, "haste");

        ModifierEntry left = ForgeweaveModifiers.entry(stripped, id("haste"));
        helper.assertTrue(left != null && left.level() == 10,
                "60 units minus haste's 50 per level must leave 10, got " + left);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(stripped) == freeBefore + 1,
                "exactly one slot comes back: " + freeBefore + " -> " + ForgeweaveModifiers.freeSlots(stripped));
        helper.succeed();
    }

    /**
     * Armor qualifies, and a {@link dev.gkissel.forgeweave.modifier.Modifier#utility} modifier that
     * cost no slot gives none back -- Create's goggles, the only slot-free utility shipped (#1007).
     */
    @GameTest(template = "empty")
    public static void removingASlotFreeUtilityFromArmorGivesBackNoSlot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack helmet = withModifiers(ToolAssembly.assembleAt(helper, player, STATION,
                        ForgeweaveBlocks.TOOL_STATION.get(), ToolAssembly.entryOf(ToolConstants.HELMET),
                        List.of("iron", "iron")),
                entry("goggles", 1), entry("thorns", 1));
        int freeBefore = ForgeweaveModifiers.freeSlots(helmet);

        ModifierWorktableMenu menu = load(helper, player, helmet,
                new ItemStack(Items.WET_SPONGE), ItemStack.EMPTY);
        ItemStack stripped = craft(helper, player, menu, "goggles");

        helper.assertTrue(ForgeweaveModifiers.entry(stripped, id("goggles")) == null,
                "the goggles must be gone, got " + ForgeweaveModifiers.of(stripped));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(stripped) == freeBefore,
                "a slot-free utility gives back no slot: " + freeBefore + " -> "
                        + ForgeweaveModifiers.freeSlots(stripped));
        helper.assertTrue(ForgeweaveModifiers.entry(stripped, id("thorns")) != null,
                "the armor's other modifier must be untouched, got " + ForgeweaveModifiers.of(stripped));
        helper.succeed();
    }

    /**
     * A tool with nothing removable produces nothing and says so. Material traits are the case that
     * matters: they are never {@link ModifierEntry}s, so they can never reach a button -- and neither
     * can an innate, which lives on the tool item rather than on the stack at all.
     */
    @GameTest(template = "empty")
    public static void traitsAreNeverOfferedAndAPlainToolProducesNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");
        helper.assertFalse(ForgeweaveTraits.of(pickaxe).isEmpty(),
                "this test needs a tool whose materials carry traits");

        ModifierWorktableMenu menu = load(helper, player, pickaxe,
                new ItemStack(Items.WET_SPONGE), ItemStack.EMPTY);

        helper.assertTrue(menu.options().isEmpty(),
                "a tool with only material traits must offer nothing, got " + menu.options());
        helper.assertTrue(menu.getSlot(ModifierWorktableMenu.RESULT_SLOT).getItem().isEmpty(),
                "nothing applicable must produce nothing");
        helper.assertTrue(menu.outcome().rejection() != null,
                "the screen must be told why, rather than left blank");
        helper.succeed();
    }

    /**
     * Issue #1057's socketed decision: a {@code socketed} modifier holding a gem is refused rather
     * than removed, because its level is the socket count and dropping one would strand the gem in
     * the last socket. An empty socket still comes off.
     */
    @GameTest(template = "empty")
    public static void aSocketedModifierHoldingAGemIsNotOffered(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack base = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");

        ItemStack empty = withModifiers(base, new ModifierEntry(ApotheosisSockets.SOCKETED_ID, 1));
        helper.assertTrue(Worktable.options(empty, WorktableRecipe.Kind.REMOVE).size() == 1,
                "an empty socket must still be removable, got "
                        + Worktable.options(empty, WorktableRecipe.Kind.REMOVE));

        ItemStack seated = empty.copy();
        seated.set(ForgeweaveDataComponents.SOCKETS.get(),
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
        helper.assertTrue(Worktable.options(seated, WorktableRecipe.Kind.REMOVE).isEmpty(),
                "a socket holding a gem must not be offered, got "
                        + Worktable.options(seated, WorktableRecipe.Kind.REMOVE));
        helper.assertFalse(ApotheosisSockets.gems(seated).get(0).isEmpty(),
                "the gem must still be where the player put it");
        helper.succeed();
    }

    // ------------------------------------------------------------------ sorting

    /**
     * The compass swaps a modifier with its neighbour, direction by slot, wrapping at both ends, and
     * is never spent -- upstream's {@code ModifierSortingRecipe} rules verbatim.
     */
    @GameTest(template = "empty")
    public static void sortingSwapsNeighboursBothWaysAndKeepsTheCompass(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = withModifiers(
                ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood"),
                entry("silky", 1), entry("haste", 1), entry("reinforced", 1));

        // Forward: the compass in the first slot moves silky one place down the list.
        ModifierWorktableMenu forward = load(helper, player, pickaxe,
                new ItemStack(Items.COMPASS), ItemStack.EMPTY);
        ItemStack moved = craft(helper, player, forward, "silky");
        helper.assertTrue(ids(moved).equals(List.of(id("haste"), id("silky"), id("reinforced"))),
                "forward sorting must swap silky with haste, got " + ids(moved));
        helper.assertTrue(forward.getSlot(ModifierWorktableMenu.INPUT_START).getItem().is(Items.COMPASS),
                "the compass is never consumed");

        // Reverse: the compass in the second slot, first empty, moves silky back.
        ModifierWorktableMenu reverse = load(helper, player, moved,
                ItemStack.EMPTY, new ItemStack(Items.COMPASS));
        ItemStack back = craft(helper, player, reverse, "silky");
        helper.assertTrue(ids(back).equals(List.of(id("silky"), id("haste"), id("reinforced"))),
                "reverse sorting must put silky back at the front, got " + ids(back));

        // Wrap: reverse on the first entry takes it to the end.
        ModifierWorktableMenu wrap = load(helper, player, back, ItemStack.EMPTY, new ItemStack(Items.COMPASS));
        ItemStack wrapped = craft(helper, player, wrap, "silky");
        helper.assertTrue(ids(wrapped).equals(List.of(id("reinforced"), id("haste"), id("silky"))),
                "reverse sorting the first entry must wrap it to the end, got " + ids(wrapped));

        // Wrap the other way: forward on the last entry takes it to the front.
        ModifierWorktableMenu wrapForward = load(helper, player, wrapped,
                new ItemStack(Items.COMPASS), ItemStack.EMPTY);
        ItemStack wrappedForward = craft(helper, player, wrapForward, "silky");
        helper.assertTrue(ids(wrappedForward).equals(List.of(id("silky"), id("haste"), id("reinforced"))),
                "forward sorting the last entry must wrap it to the front, got " + ids(wrappedForward));
        helper.succeed();
    }

    /** Sorting needs something to swap with, so one modifier offers no buttons and makes nothing. */
    @GameTest(template = "empty")
    public static void sortingRefusesAToolWithFewerThanTwoModifiers(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = withModifiers(
                ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood"), entry("silky", 1));

        ModifierWorktableMenu menu = load(helper, player, pickaxe,
                new ItemStack(Items.COMPASS), ItemStack.EMPTY);

        helper.assertTrue(menu.options().isEmpty(),
                "one modifier is nothing to sort, got " + menu.options());
        helper.assertTrue(menu.getSlot(ModifierWorktableMenu.RESULT_SLOT).getItem().isEmpty(),
                "one modifier must produce no result");
        helper.succeed();
    }

    /**
     * ADR-0004 item 2: what a removal and a sort leave on the stack is still a list of
     * {@code modifier id + level} and nothing else, so a save written after either still decodes.
     * The order is the new guarantee -- sorting exists to change it -- so the round trip checks it.
     */
    @GameTest(template = "empty")
    public static void removalAndSortingBothSerializeAsIdAndLevelInOrder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = withModifiers(
                ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood"),
                entry("silky", 1), entry("haste", 60));

        ModifierWorktableMenu sortMenu = load(helper, player, pickaxe,
                new ItemStack(Items.COMPASS), ItemStack.EMPTY);
        ItemStack sorted = craft(helper, player, sortMenu, "silky");
        assertRoundTrips(helper, sorted, List.of(entry("haste", 60), entry("silky", 1)));

        ModifierWorktableMenu removeMenu = load(helper, player, sorted,
                new ItemStack(Items.WET_SPONGE), ItemStack.EMPTY);
        ItemStack removed = craft(helper, player, removeMenu, "haste");
        assertRoundTrips(helper, removed, List.of(entry("haste", 10), entry("silky", 1)));
        helper.succeed();
    }

    private static void assertRoundTrips(GameTestHelper helper, ItemStack stack, List<ModifierEntry> expected) {
        helper.assertTrue(ForgeweaveModifiers.of(stack).equals(expected),
                "expected " + expected + " on the stack, got " + ForgeweaveModifiers.of(stack));
        ItemStack decoded = ItemStack.CODEC
                .encodeStart(registries(helper).createSerializationContext(
                        com.mojang.serialization.JsonOps.INSTANCE), stack)
                .flatMap(json -> ItemStack.CODEC.parse(registries(helper).createSerializationContext(
                        com.mojang.serialization.JsonOps.INSTANCE), json))
                .getOrThrow();
        helper.assertTrue(ForgeweaveModifiers.of(decoded).equals(expected),
                "the modifier list must survive a save round trip, got " + ForgeweaveModifiers.of(decoded));
    }
}
