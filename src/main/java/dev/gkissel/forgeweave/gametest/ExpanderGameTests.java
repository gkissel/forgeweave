package dev.gkissel.forgeweave.gametest;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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
 * Issue #438's behavioral half: what the Width++/Height++ expanders actually do to a tool's mined
 * area, exercised the way a player reaches it -- a tool assembled at a real station, an expander
 * applied to it through that same station's modifier flow, then a real break through
 * {@code ServerPlayerGameMode#destroyBlock}. The pure recipe/aspect half is
 * {@code modifier.HarvestExpanderTest}.
 *
 * <p>Every expected count is upstream {@code tools/ToolEvents#onExtraBlockBreak}'s own arithmetic
 * (pinned {@code c01173c}) fed through {@code library/utils/ToolHelper#calcAOEBlocks}:
 *
 * <ul>
 *   <li>pickaxe/shovel/hatchet/kama: {@code width += 1} / {@code height += 1} on a {@code 1x1x1} base;
 *   <li>mattock: both axes grow by the <em>number</em> of expanders on the tool;
 *   <li>hammer/excavator/lumber axe/scythe: {@code += 2} on a {@code 3x3} base, plus
 *       {@code event.distance = 3} -- the manhattan clip that takes the corners off a 5x5.
 * </ul>
 *
 * <p>The scenes are slabs in the X/Y plane at a fixed Z because that is the plane a mock server
 * player (yaw 0, so looking south) mines: {@code AoeHarvest} recovers the mined face from the
 * player's look direction, giving NORTH, and upstream's own {@code calcAOEBlocks} maps a north/south
 * face's width to X and height to Y.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ExpanderGameTests {

    /** The station every tool here is built and modified at; clear of the block scenes below. */
    private static final BlockPos STATION = new BlockPos(0, 1, 0);
    /**
     * The block broken in every scene. X is deliberately low: gametest structures are spaced
     * {@code size + 5} apart along X ({@code StructureGridSpawner}), so with a 1x1x1 template only
     * relative X 0..5 is this test's own ground, and a 5-wide sweep centred here spans 0..4 with
     * room for a witness block at 5.
     */
    private static final BlockPos ORIGIN = new BlockPos(2, 3, 3);
    /**
     * The material every hammer here is built from. Not stone: an expanded hammer takes 21 blocks in
     * one swing and a stone one runs out of durability partway through, which would read as a wrong
     * shape rather than as the worn-out tool it is.
     */
    private static final String HARD = "cobalt";

    // ------------------------------------------------------------------ large tools (+2 per axis)

    /**
     * Baseline, so the two expanded cases below are read against something: an unmodified hammer
     * still takes exactly its 3x3 out of the same slab.
     */
    @GameTest(template = "empty")
    public static void anUnmodifiedHammerStillBreaksNine(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_HAMMER.get(), HARD);
        fillSlab(helper, 5, 5, Blocks.STONE.defaultBlockState());

        int broken = breakAndCountSlab(helper, player, 5, 5);

        helper.assertTrue(broken == 9, "an unmodified hammer must break its 3x3, broke " + broken);
        helper.succeed();
    }

    /**
     * Width++ on a hammer: {@code 3 + 2 = 5} wide by 3 high, all 15 of which survive the manhattan
     * clip ({@code |dx| + |dy| <= 3} peaks at exactly 3 on this shape). The slab is 6 wide, so the
     * column three blocks out standing afterwards is what proves the sweep has an edge.
     */
    @GameTest(template = "empty")
    public static void widthOnAHammerSweepsFiveWide(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_HAMMER.get(), HARD);
        expand(helper, player, ForgeweaveItems.EXPANDER_W.get());
        fillSlab(helper, 5, 5, Blocks.STONE.defaultBlockState());

        int broken = breakAndCountSlab(helper, player, 5, 5);

        helper.assertTrue(broken == 15, "Width++ must make the hammer's sweep 5x3, broke " + broken);
        helper.assertBlockPresent(Blocks.STONE, ORIGIN.offset(3, 0, 0));
        helper.succeed();
    }

    /**
     * Both expanders on a hammer: a 5x5 face, minus the four corners upstream's {@code distance = 3}
     * manhattan clip removes ({@code 2 + 2 > 3}) -- 21 blocks, not 25. The clip is the whole reason
     * {@code ToolEvents} sets a distance at all, so a test that could not tell 21 from 25 would miss
     * it entirely.
     */
    @GameTest(template = "empty")
    public static void bothExpandersClipTheCornersOffAFiveByFive(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_HAMMER.get(), HARD);
        expand(helper, player, ForgeweaveItems.EXPANDER_W.get());
        expand(helper, player, ForgeweaveItems.EXPANDER_H.get());
        fillSlab(helper, 5, 5, Blocks.STONE.defaultBlockState());

        int broken = breakAndCountSlab(helper, player, 5, 5);

        helper.assertTrue(broken == 21,
                "an expanded hammer must take a 5x5 minus its four clipped corners, broke " + broken);
        helper.assertBlockPresent(Blocks.STONE, ORIGIN.offset(-2, 2, 0));
        helper.succeed();
    }

    // ------------------------------------------------------------------ small tools (+1 per axis)

    /**
     * Width++ on a pickaxe: upstream's small-tool branch is {@code += 1} on a {@code 1x1x1} base, so
     * the sweep becomes two blocks wide -- not the large tools' five, which is the distinction the
     * per-tool branch in {@code ToolEvents} exists to make.
     */
    @GameTest(template = "empty")
    public static void widthOnAPickaxeTakesExactlyOneExtraBlock(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_PICKAXE.get(), "stone");
        expand(helper, player, ForgeweaveItems.EXPANDER_W.get());
        fillSlab(helper, 5, 5, Blocks.STONE.defaultBlockState());

        int broken = breakAndCountSlab(helper, player, 5, 5);

        helper.assertTrue(broken == 2, "Width++ on a pickaxe must break 2 blocks, broke " + broken);
        helper.succeed();
    }

    /** An unmodified pickaxe is still a one-block pickaxe -- the shape's base is {@code 1x1x1}. */
    @GameTest(template = "empty")
    public static void anUnmodifiedPickaxeStillBreaksOne(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_PICKAXE.get(), "stone");
        fillSlab(helper, 5, 5, Blocks.STONE.defaultBlockState());

        int broken = breakAndCountSlab(helper, player, 5, 5);

        helper.assertTrue(broken == 1, "an unmodified pickaxe must break exactly one block, broke " + broken);
        helper.succeed();
    }

    /**
     * The mattock's own branch: upstream grows <em>both</em> axes by the number of expanders, so one
     * expander -- either one -- makes it 2x2 rather than 2x1. Dirt, because a mattock is an
     * axe+shovel and was never correct-tool-for-drops on stone.
     */
    @GameTest(template = "empty")
    public static void oneExpanderMakesTheMattockTwoByTwo(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_MATTOCK.get(), "stone");
        expand(helper, player, ForgeweaveItems.EXPANDER_H.get());
        fillSlab(helper, 5, 5, Blocks.DIRT.defaultBlockState());

        int broken = breakAndCountSlab(helper, player, 5, 5);

        helper.assertTrue(broken == 4, "one expander must make the mattock 2x2, broke " + broken);
        helper.succeed();
    }

    // ------------------------------------------------------------------ the aoeOnly gate

    /**
     * Upstream's {@code ModifierAspect.aoeOnly}: a tool with no mined area to widen refuses the
     * expander outright rather than accepting a modifier that would do nothing. A broadsword mines no
     * area at all, so the station produces nothing and says why.
     */
    @GameTest(template = "empty")
    public static void aSwordRefusesAnExpander(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack sword = assembled(helper, player, ForgeweaveItems.TOOL_BROADSWORD.get(), "stone");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            blockEntity.container().setItem(i, ItemStack.EMPTY);
        }
        blockEntity.container().setItem(0, sword);
        blockEntity.container().setItem(1, new ItemStack(ForgeweaveItems.EXPANDER_W.get()));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a broadsword has no area to widen, so the station must produce nothing");
        helper.assertTrue(menu.rejection() != null, "and must say why it refused");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** Assembles {@code tool} at the station, applies nothing yet, and puts it in the player's hand. */
    private static ServerPlayer holding(GameTestHelper helper, ToolItem tool, String material) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Survival, not the mock player's default: a creative break never reaches the AoE path.
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, assembled(helper, player, tool, material));
        return player;
    }

    /** One tool, every part of the same material -- no shape here depends on the parts differing. */
    private static ItemStack assembled(GameTestHelper helper, Player player, ToolItem tool, String material) {
        ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(tool);
        ItemStack built = ToolAssembly.assembleAtForge(helper, player, STATION, entry,
                Collections.nCopies(entry.slotCount(), material));
        helper.assertTrue(built.is(tool), "the station must build the tool under test, got " + built);
        return built;
    }

    /**
     * Runs one expander through the station's real modifier flow and puts the result back in the
     * player's hand -- the whole point being that the geometry tests above never hand-write a
     * modifier onto a stack.
     */
    private static void expand(GameTestHelper helper, ServerPlayer player, Item reagent) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            blockEntity.container().setItem(i, ItemStack.EMPTY);
        }
        blockEntity.container().setItem(0, player.getMainHandItem());
        blockEntity.container().setItem(1, new ItemStack(reagent));

        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "the station must accept " + reagent
                + (menu.rejection() != null ? " (" + menu.rejection().message().getString() + ")" : ""));
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        blockEntity.container().setItem(0, ItemStack.EMPTY);

        List<ModifierEntry> modifiers = ForgeweaveModifiers.of(output);
        helper.assertFalse(modifiers.isEmpty(), "the expander must land on the tool, got " + modifiers);
        player.setItemInHand(InteractionHand.MAIN_HAND, output);
    }

    /**
     * A flat slab of {@code state} in the X/Y plane at {@link #ORIGIN}'s Z, {@code width} blocks wide
     * from {@code ORIGIN.x - 2} and {@code height} tall from {@code ORIGIN.y - 2} -- the plane a
     * south-facing player mines into, sized so every shape under test fits with a block to spare.
     */
    private static void fillSlab(GameTestHelper helper, int width, int height, BlockState state) {
        forEachSlabPos(width, height, pos -> helper.setBlock(pos, state));
    }

    /** Breaks {@link #ORIGIN} as the player would, and counts how much of the slab went with it. */
    private static int breakAndCountSlab(GameTestHelper helper, ServerPlayer player, int width, int height) {
        player.gameMode.destroyBlock(helper.absolutePos(ORIGIN));
        int[] broken = {0};
        forEachSlabPos(width, height, pos -> {
            if (helper.getBlockState(pos).isAir()) {
                broken[0]++;
            }
        });
        return broken[0];
    }

    private static void forEachSlabPos(int width, int height, Consumer<BlockPos> action) {
        for (int dx = -2; dx < -2 + width + 1; dx++) {
            for (int dy = -2; dy < -2 + height + 1; dy++) {
                action.accept(ORIGIN.offset(dx, dy, 0));
            }
        }
    }
}
