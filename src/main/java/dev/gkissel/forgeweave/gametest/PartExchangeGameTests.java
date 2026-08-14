package dev.gkissel.forgeweave.gametest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.Embossing;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Issue #264's verification: part exchange driven through the real Tool Station menu, pinning the
 * semantics derived from upstream 1.12's {@code ToolBuilder#tryReplaceToolParts} +
 * {@code rebuildTool} (see {@link ToolAssemblyRecipes#resolveExchange}'s javadoc for the
 * derivation): stats and traits rebuild from the new material set, the damage <em>value</em> rides
 * along against the new maximum (and refuses the swap when it doesn't fit), modifiers and an
 * embossment survive as {@code id + level}, a part the tool has no slot for is refused, a
 * same-material part is refused (upstream's "must not be duplicates" rule), and large tools
 * exchange only at the Tool Forge (the assembly gate, issue #152).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class PartExchangeGameTests {

    private static final ResourceLocation IRON = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "iron");
    private static final ResourceLocation MAGNETIC2 =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "magnetic2");

    /**
     * The mechanic itself: swapping the stone head for an iron one rebuilds the stat block and trait
     * list to exactly what assembling with iron in that slot writes, while the damage value -- not
     * the damage percentage -- carries over to the new durability pool, upstream's
     * {@code toolStack.copy()} + {@code rebuildTool} (which never touches damage).
     */
    @GameTest(template = "empty")
    public static void swappingTheHeadRebuildsStatsAndKeepsTheDamageValue(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.set(DataComponents.DAMAGE, 10);

        ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(ForgeweaveItems.TOOL_PICKAXE.get());
        ItemStack expected = ToolAssemblyRecipes.assemble(registries, entry,
                List.of(IRON, material("wood"), material("wood"))).orElseThrow();

        ToolStationMenu menu = load(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), pickaxe,
                ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "iron"));
        ItemStack swapped = take(helper, player, menu);

        helper.assertTrue(swapped.is(ForgeweaveItems.TOOL_PICKAXE.get()), "the swap must keep the tool a pickaxe");
        helper.assertTrue(IRON.equals(materialsOf(swapped).get(0)),
                "the head slot's material must now be iron, got " + materialsOf(swapped));
        helper.assertTrue(expected.get(ForgeweaveDataComponents.TOOL_STATS.get())
                        .equals(swapped.get(ForgeweaveDataComponents.TOOL_STATS.get())),
                "the stat block must be exactly what assembling iron/wood/wood writes, got "
                        + swapped.get(ForgeweaveDataComponents.TOOL_STATS.get()));
        helper.assertTrue(traits(expected).equals(traits(swapped)),
                "the trait list must rebuild from the new material set: expected " + traits(expected)
                        + ", got " + traits(swapped));
        helper.assertTrue(traits(swapped).contains(MAGNETIC2),
                "iron's head-scoped trait must arrive with the new head");
        helper.assertTrue(swapped.getDamageValue() == 10,
                "the damage VALUE must carry over unchanged (upstream keeps it), got " + swapped.getDamageValue());
        helper.assertTrue(swapped.getMaxDamage() == expected.getMaxDamage(),
                "the durability pool must be the new material set's, got " + swapped.getMaxDamage());
        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).getItem().isEmpty()
                        && menu.getSlot(ToolStationMenu.BINDING_SLOT).getItem().isEmpty(),
                "taking the swapped tool must spend the old tool and the new part");
        helper.succeed();
    }

    /**
     * Upstream {@code rebuildTool} re-applies the saved modifier list and the embossment's traits
     * onto the rebuilt base; the display name and repair count live outside the rebuilt tag and ride
     * the copy. Forgeweave equivalents: the {@code id + level} entries are untouched, the
     * embossment's donor traits are re-appended, and the custom name survives.
     */
    @GameTest(template = "empty")
    public static void modifiersEmbossmentAndRenameSurviveAnExchange(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        // A modifier (haste from one redstone), then an embossment (iron head donor + reagents).
        ItemStack hasted = take(helper, player,
                load(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), pickaxe,
                        new ItemStack(Items.REDSTONE)));
        ItemStack embossed = take(helper, player, loadEmbossing(helper, player, pos, hasted));
        embossed.set(DataComponents.CUSTOM_NAME, Component.literal("Fred"));
        List<ModifierEntry> before = ForgeweaveModifiers.of(embossed);
        helper.assertTrue(traits(embossed).contains(MAGNETIC2), "the embossment must be on before the swap");

        // Swap the wooden handle for a stone one.
        ItemStack swapped = take(helper, player,
                load(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), embossed,
                        ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "stone")));

        helper.assertTrue(before.equals(ForgeweaveModifiers.of(swapped)),
                "the id+level modifier list must survive untouched: " + before + " -> "
                        + ForgeweaveModifiers.of(swapped));
        helper.assertTrue(ForgeweaveModifiers.entry(swapped, Embossing.idFor(IRON)) != null,
                "the embossment entry must survive the swap");
        helper.assertTrue(traits(swapped).contains(MAGNETIC2),
                "the embossment's donor traits must be re-appended after the rebuild, got " + traits(swapped));
        helper.assertTrue(material("stone").equals(materialsOf(swapped).get(2)),
                "the handle slot's material must now be stone, got " + materialsOf(swapped));
        helper.assertTrue(Component.literal("Fred").equals(swapped.get(DataComponents.CUSTOM_NAME)),
                "the rename must ride the copy, got " + swapped.get(DataComponents.CUSTOM_NAME));
        ToolStats.Stats effective = ForgeweaveModifiers.effectiveStats(swapped);
        ToolStats.Stats base = swapped.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(effective.miningSpeed() > base.miningSpeed(),
                "haste must still be baked on top of the rebuilt base: " + base + " -> " + effective);
        helper.succeed();
    }

    /** Upstream: a part the tool's slot roster has no place for refuses the whole exchange. */
    @GameTest(template = "empty")
    public static void aPartTheToolHasNoSlotForIsRejected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationMenu menu = load(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), pickaxe,
                ToolAssembly.part(ForgeweaveItems.PART_SHOVEL_HEAD.get(), "iron"));

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a shovel head must not swap into a pickaxe; got "
                        + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem());
        helper.assertTrue(menu.rejection() != null, "a refused exchange must tell the player why");
        helper.succeed();
    }

    /** Upstream's "must not be duplicates of currently used parts": same material, same slot, no swap. */
    @GameTest(template = "empty")
    public static void aSameMaterialPartIsRejected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationMenu menu = load(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), pickaxe,
                ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "stone"));

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a stone head on a stone-headed pickaxe must not produce a swap");
        helper.assertTrue(menu.rejection() != null, "a refused duplicate must tell the player why");
        helper.succeed();
    }

    /**
     * Upstream's durability gate ({@code gui.error.not_enough_durability}): the damage value
     * survives the swap, so a tool more worn than the new part set's pool can hold is refused.
     */
    @GameTest(template = "empty")
    public static void anExchangeTheDamageDoesNotFitIsRejected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "iron", "wood", "wood");

        ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(ForgeweaveItems.TOOL_PICKAXE.get());
        int stoneMax = ToolAssemblyRecipes.assemble(registries, entry,
                List.of(material("stone"), material("wood"), material("wood"))).orElseThrow().getMaxDamage();
        helper.assertTrue(stoneMax + 1 < pickaxe.getMaxDamage(),
                "precondition: the iron-headed pool must exceed the stone-headed one by 2+, got "
                        + pickaxe.getMaxDamage() + " vs " + stoneMax);
        pickaxe.set(DataComponents.DAMAGE, stoneMax + 1);

        ToolStationMenu menu = load(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), pickaxe,
                ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "stone"));

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "damage past the new maximum must refuse the swap");
        helper.assertTrue(menu.rejection() != null, "the durability refusal must tell the player why");
        helper.succeed();
    }

    /**
     * Issue #293, upstream {@code ToolBuilder#rebuildTool}'s own last gate (lines 547-561 of the
     * pinned commit): the rebuild re-derives the tool's free modifier count from the new material set
     * and throws {@code gui.error.not_enough_modifiers} ("Not enough Modifiers. (%d needed)") when the
     * modifiers already on the tool no longer fit. Paper's {@code writable} pair is the slot-granting
     * part material here, so swapping a paper handle off a tool whose modifiers exactly fill the
     * widened budget must be refused rather than handed back over budget.
     */
    @GameTest(template = "empty")
    public static void anExchangeThatShrinksTheModifierBudgetIsRejected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "paper");
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), modifiers("haste", "searing", "magnetic_pull",
                "soulbound"));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == 0,
                "precondition: paper's extra slot must be exactly filled, got "
                        + ForgeweaveModifiers.freeSlots(pickaxe) + " free");

        ToolStationMenu menu = load(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), pickaxe,
                ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "wood"));

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "swapping the slot-granting part out from under four modifiers must produce no tool, got "
                        + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem());
        helper.assertTrue(menu.rejection() != null, "the over-budget refusal must tell the player why");
        helper.succeed();
    }

    /** The boundary the gate sits on: a budget that shrinks to exactly the occupied count still swaps. */
    @GameTest(template = "empty")
    public static void anExchangeLeavingExactlyEnoughSlotsIsAllowed(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "paper");
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), modifiers("haste", "searing", "magnetic_pull"));

        ItemStack swapped = take(helper, player,
                load(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), pickaxe,
                        ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "wood")));

        helper.assertTrue(material("wood").equals(materialsOf(swapped).get(2)),
                "the handle slot's material must now be wood, got " + materialsOf(swapped));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(swapped) == 0,
                "the swapped tool must land at exactly zero free slots, got "
                        + ForgeweaveModifiers.freeSlots(swapped));
        helper.succeed();
    }

    /** And a swap that changes no slot totals is untouched by the gate, modifiers or not. */
    @GameTest(template = "empty")
    public static void aBudgetNeutralExchangeStillWorks(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), modifiers("haste", "searing", "magnetic_pull"));

        ItemStack swapped = take(helper, player,
                load(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), pickaxe,
                        ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "iron")));

        helper.assertTrue(IRON.equals(materialsOf(swapped).get(0)),
                "the head slot's material must now be iron, got " + materialsOf(swapped));
        helper.assertTrue(ForgeweaveModifiers.occupiedSlots(swapped) == 3,
                "the three modifiers must survive the swap, got " + ForgeweaveModifiers.of(swapped));
        helper.succeed();
    }

    /** Issue #152's gate, applied to exchanges: a large tool swaps parts at the Tool Forge only. */
    @GameTest(template = "empty")
    public static void aLargeToolExchangesAtTheForgeOnly(GameTestHelper helper) {
        BlockPos forgePos = new BlockPos(1, 1, 1);
        BlockPos stationPos = new BlockPos(3, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolAssemblyRecipes.Entry hammer = ToolAssembly.entryFor(ForgeweaveItems.TOOL_HAMMER.get());
        ItemStack tool = ToolAssembly.assembleAtForge(helper, player, forgePos, hammer,
                Collections.nCopies(hammer.slotCount(), "stone"));
        ItemStack ironHead = ToolAssembly.part(ForgeweaveItems.PART_HAMMER_HEAD.get(), "iron");

        ToolStationMenu station = load(helper, player, stationPos, ForgeweaveBlocks.TOOL_STATION.get(),
                tool.copy(), ironHead.copy());
        helper.assertTrue(station.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "the Tool Station must refuse a large-tool part exchange");
        helper.assertTrue(station.rejection() != null, "and say so");

        ToolStationMenu forge = load(helper, player, forgePos, ForgeweaveBlocks.TOOL_FORGE.get(),
                tool.copy(), ironHead.copy());
        ItemStack swapped = take(helper, player, forge);
        helper.assertTrue(IRON.equals(materialsOf(swapped).get(1)),
                "the Tool Forge must swap the hammer head to iron, got " + materialsOf(swapped));
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static ToolStationMenu load(GameTestHelper helper, Player player, BlockPos pos, Block station,
            ItemStack tool, ItemStack input) {
        helper.setBlock(pos, station);
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, tool);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT, input);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        return menu;
    }

    /** {@link EmbossingGameTests}'s loadout: iron head donor plus the #248 parity reagent set. */
    private static ToolStationMenu loadEmbossing(GameTestHelper helper, Player player, BlockPos pos, ItemStack tool) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, tool);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT,
                ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "iron"));
        blockEntity.container().setItem(ToolStationMenu.HANDLE_SLOT,
                new ItemStack(ForgeweaveItems.GREEN_SLIME_CRYSTAL.get()));
        blockEntity.container().setItem(ToolStationMenu.EXTRA_SLOT_1,
                new ItemStack(ForgeweaveItems.BLUE_SLIME_CRYSTAL.get()));
        blockEntity.container().setItem(ToolStationMenu.EXTRA_SLOT_2,
                new ItemStack(ForgeweaveItems.MAGMA_SLIME_CRYSTAL.get()));
        blockEntity.container().setItem(ToolStationMenu.EXTRA_SLOT_3, new ItemStack(Items.GOLD_BLOCK));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        return menu;
    }

    private static ItemStack take(GameTestHelper helper, Player player, ToolStationMenu menu) {
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "expected the station to produce an output"
                + (menu.rejection() == null ? "" : "; it says: " + menu.rejection().getString()));
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }

    /** One level-1 entry per named modifier -- the fixture shape {@code ToolStationGameTests} uses. */
    private static List<ModifierEntry> modifiers(String... names) {
        return Arrays.stream(names)
                .map(name -> new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name), 1))
                .toList();
    }

    private static ResourceLocation material(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private static List<ResourceLocation> materialsOf(ItemStack stack) {
        return stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get()).parts();
    }

    private static List<ResourceLocation> traits(ItemStack stack) {
        return stack.getOrDefault(ForgeweaveDataComponents.TRAITS.get(), List.of());
    }
}
