package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Parity audit T24 (issue #455): what Blasting does once it is on a real tool -- the station flow
 * that puts it there, the drops it destroys, the blocks it makes minable, and the speed it mines
 * them at. The pure arithmetic is {@code modifier.BlastingTest}; everything here needs a level, a
 * station or an event the unit tests have no access to.
 *
 * <p>Upstream, pinned {@code c01173c}: {@code tools/modifiers/ModBlasting.java} and
 * {@code library/utils/ToolHelper.java:189-201}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class BlastingGameTests {

    private static final ResourceLocation BLASTING =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "blasting");

    private static final BlockPos STATION = new BlockPos(1, 1, 1);

    /**
     * {@code TinkerModifiers:148}'s {@code ItemCombination(1, tnt, tnt, tnt)}: three TNT buy exactly
     * one level, and the first level takes one of the tool's three modifier slots.
     */
    @GameTest(template = "empty")
    public static void threeTntBuyOneLevelOfBlasting(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");

        ItemStack blasted = apply(helper, player, pickaxe, new ItemStack(Items.TNT, 3));

        ModifierEntry entry = ForgeweaveModifiers.entry(blasted, BLASTING);
        helper.assertTrue(entry != null && entry.level() == 1,
                "three TNT must record blasting at level 1, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(blasted) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "blasting's first level must take one slot, got " + ForgeweaveModifiers.freeSlots(blasted));
        helper.succeed();
    }

    /**
     * Upstream's {@code FreeFirstModifierAspect(this, 1)}: nine TNT is all three levels, and they
     * still share the one slot the first level bought -- unlike haste, whose every level charges.
     */
    @GameTest(template = "empty")
    public static void allThreeLevelsShareOneModifierSlot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");

        ItemStack blasted = apply(helper, player, pickaxe, new ItemStack(Items.TNT, 9));

        ModifierEntry entry = ForgeweaveModifiers.entry(blasted, BLASTING);
        helper.assertTrue(entry != null && entry.level() == 3,
                "nine TNT must record blasting at level 3, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(blasted) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "levels two and three must ride inside the first level's slot, got "
                        + ForgeweaveModifiers.freeSlots(blasted));
        helper.succeed();
    }

    /**
     * {@code ModifierAspect.harvestOnly}: a broadsword is {@code Category.MELEE}, so the station
     * produces nothing and says why -- the same refusal shape the launcher and {@code aoeOnly} gates
     * already use.
     */
    @GameTest(template = "empty")
    public static void aSwordRefusesBlasting(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(ForgeweaveItems.TOOL_BROADSWORD.get());
        ItemStack sword = ToolAssembly.assemble(helper, player, STATION, entry,
                Collections.nCopies(entry.slotCount(), "stone"));

        ToolStationMenu menu = load(helper, player, sword, new ItemStack(Items.TNT, 3));

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "blasting is harvest-only, so a broadsword must produce nothing");
        helper.assertTrue(menu.rejection() != null, "and the station must say why");
        helper.succeed();
    }

    /**
     * {@code ModBlasting#canApplyTogether(IToolMod)} refuses silktouch -- and upstream checks both
     * directions, so the tool already carrying silky refuses the TNT.
     */
    @GameTest(template = "empty")
    public static void silkyRefusesBlasting(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");
        ItemStack silky = apply(helper, player, pickaxe, new ItemStack(ForgeweaveItems.SILKY_JEWEL.get(), 1));

        ToolStationMenu menu = load(helper, player, silky, new ItemStack(Items.TNT, 3));

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a silky tool must refuse blasting");
        helper.assertTrue(menu.rejection() != null, "and the station must say why");
        helper.succeed();
    }

    /**
     * {@code ToolHelper#isToolEffective2}'s blasting branch: a tool carrying blasting counts as
     * effective on any non-liquid block whose material needs no tool. A pickaxe is not normally
     * correct for dirt; with blasting it is, which is what puts dirt inside its AoE sweep, its
     * smelt path and its trait effectiveness checks.
     */
    @GameTest(template = "empty")
    public static void blastingMakesNonEffectiveBlocksCountAsHarvested(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");
        helper.assertFalse(pickaxe.isCorrectToolForDrops(Blocks.DIRT.defaultBlockState()),
                "a plain pickaxe must not be the correct tool for dirt");

        ItemStack blasted = apply(helper, player, pickaxe, new ItemStack(Items.TNT, 3));

        helper.assertTrue(blasted.isCorrectToolForDrops(Blocks.DIRT.defaultBlockState()),
                "blasting must make a tool-free block count as an effective break");
        helper.assertTrue(blasted.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()),
                "and must not cost the tool anything it already mined");
        helper.assertFalse(blasted.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()),
                "obsidian requires the correct tool, so blasting must not hand it over");
        helper.succeed();
    }

    /**
     * {@code ModBlasting#blockHarvestDrops}: at level 3 the drop chance is 0, so nothing survives the
     * break. The roll is per dropped stack, exactly as 1.12's {@code dropChance} is.
     */
    @GameTest(template = "empty")
    public static void fullBlastingDestroysEveryDrop(GameTestHelper helper) {
        ItemStack pickaxe = blastedPickaxe(3);
        ItemEntity first = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.COBBLESTONE, 4));
        ItemEntity second = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.DIRT, 1));
        BlockDropsEvent event = dropsEvent(helper, pickaxe, first, second);

        ForgeweaveModifiers.onBlockDrops(event);

        helper.assertTrue(event.getDrops().isEmpty(),
                "blasting III must blow up every drop, got " + event.getDrops().size());
        helper.succeed();
    }

    /** The same event with no blasting on the tool leaves the drops exactly as they were. */
    @GameTest(template = "empty")
    public static void anUnblastedToolKeepsItsDrops(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        ItemEntity drop = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.COBBLESTONE, 4));
        BlockDropsEvent event = dropsEvent(helper, pickaxe, drop);

        ForgeweaveModifiers.onBlockDrops(event);

        helper.assertTrue(event.getDrops().size() == 1, "an unmodified tool must destroy nothing");
        helper.succeed();
    }

    /**
     * {@code ModBlasting#miningSpeed} through the real {@link PlayerEvent.BreakSpeed}: obsidian is
     * hardness 50, so a blasting III tool mines it at {@code toolSpeed * 50 / 1.1} regardless of what
     * vanilla's own speed was -- the whole point of the modifier.
     */
    @GameTest(template = "empty")
    public static void blastingRewritesTheBreakSpeedFromTheBlocksHardness(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");
        ItemStack blasted = apply(helper, player, pickaxe, new ItemStack(Items.TNT, 9));

        ServerPlayer breaker = helper.makeMockServerPlayerInLevel();
        breaker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, blasted);
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());

        PlayerEvent.BreakSpeed event = new PlayerEvent.BreakSpeed(breaker,
                Blocks.OBSIDIAN.defaultBlockState(), 2.0F, pos);
        ForgeweaveModifiers.onBreakSpeed(event);

        float toolSpeed = ((ToolItem) blasted.getItem()).actualMiningSpeed(blasted);
        float expected = ForgeweaveModifiers.blastingBreakSpeed(3, toolSpeed, 50.0F, 2.0F);
        helper.assertTrue(Math.abs(event.getNewSpeed() - expected) < 1.0e-3F,
                "expected blasting to set " + expected + ", got " + event.getNewSpeed());
        helper.assertTrue(event.getNewSpeed() > 2.0F, "and it must be a real speedup on obsidian");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** A pickaxe with blasting written straight on, for the paths that never touch the station. */
    private static ItemStack blastedPickaxe(int level) {
        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        pickaxe.set(dev.gkissel.forgeweave.item.ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(BLASTING, level)));
        return pickaxe;
    }

    private static BlockDropsEvent dropsEvent(GameTestHelper helper, ItemStack tool, ItemEntity... drops) {
        return new BlockDropsEvent(helper.getLevel(), BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null,
                new ArrayList<>(List.of(drops)), null, tool);
    }

    /** The station loaded with {@code tool} and one reagent stack, output untaken. */
    private static ToolStationMenu load(GameTestHelper helper, Player player, ItemStack tool, ItemStack reagent) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            blockEntity.container().setItem(i, ItemStack.EMPTY);
        }
        blockEntity.container().setItem(0, tool);
        blockEntity.container().setItem(1, reagent);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        menu.broadcastChanges();
        return menu;
    }

    /** {@link #load}, plus taking the output -- the modified tool the caller asserts against. */
    private static ItemStack apply(GameTestHelper helper, Player player, ItemStack tool, ItemStack reagent) {
        ToolStationMenu menu = load(helper, player, tool, reagent);
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "the station must accept " + reagent
                + (menu.rejection() != null ? " (" + menu.rejection().message().getString() + ")" : ""));
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }
}
