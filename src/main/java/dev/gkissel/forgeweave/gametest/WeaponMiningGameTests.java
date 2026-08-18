package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Issue #437 (parity audit 2026-08-18, T5): the Tool Station's melee weapons used to carry
 * {@code minecraft:mineable/axe} at full mining speed, which made a broadsword a better logging tool
 * than the hatchet. Upstream 1.12 splits its melee weapons two ways and neither way is an axe:
 *
 * <ul>
 *   <li>{@code library/tools/SwordCore.java} -- broadsword, longsword, rapier and cleaver:
 *       {@code effective_materials} = WEB/VINE/CORAL/GOURD/LEAVES (1.21's
 *       {@code #minecraft:sword_efficient} plus cobweb), {@code miningSpeedModifier() = 0.5}
 *       ("slooow, because it's a swooooord"), {@code getStrVsBlock} x7.5 on cobweb, and
 *       {@code canDestroyBlockInCreative() = false}.</li>
 *   <li>{@code tools/melee/item/FryPan.java} / {@code BattleSign.java} -- plain
 *       {@code TinkerToolCore}s that never override {@code isEffective}, so
 *       {@code ToolCore#isEffective} returns false for every block: they mine nothing at tool speed.
 *       Both still refuse to destroy blocks in creative.</li>
 * </ul>
 *
 * <p>The warmace has no 1.12 counterpart and rides vanilla's mace (ADR-0005 d.4); vanilla's
 * {@code MaceItem#createToolProperties} is {@code new Tool(List.of(), ...)} and its
 * {@code canAttackBlock} is {@code !player.isCreative()}, i.e. the frying pan's shape exactly.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class WeaponMiningGameTests {

    private static final List<String> THREE = List.of("wood", "stone", "wood");
    private static final List<String> TWO = List.of("wood", "stone");

    /**
     * The four {@code SwordCore} weapons: the sword-efficient set at half the tool's mining speed,
     * nothing at all on an oak log, and cobweb at 7.5x on top of that half.
     */
    @GameTest(template = "empty")
    public static void swordsMineTheSwordSetAtHalfSpeed(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack broadsword = ToolAssembly.assemble(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_BROADSWORD.get()), THREE);

        Tool tool = broadsword.get(DataComponents.TOOL);
        helper.assertTrue(tool != null, "an assembled broadsword must carry a tool component");
        ToolStats.Stats stats = broadsword.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null, "an assembled broadsword must carry tool stats");

        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        BlockState cobweb = Blocks.COBWEB.defaultBlockState();

        helper.assertFalse(tool.isCorrectForDrops(log), "a broadsword must not be an axe (issue #437)");
        helper.assertTrue(tool.getMiningSpeed(log) == 1.0F,
                "an oak log must fall back to the default mining speed, got " + tool.getMiningSpeed(log));

        float half = stats.miningSpeed() * 0.5F;
        helper.assertTrue(tool.isCorrectForDrops(leaves), "a broadsword must be correct-for-drops on leaves");
        assertSpeed(helper, "leaves", tool.getMiningSpeed(leaves), half);
        helper.assertTrue(tool.isCorrectForDrops(cobweb), "a broadsword must be correct-for-drops on cobweb");
        assertSpeed(helper, "cobweb", tool.getMiningSpeed(cobweb), half * 7.5F);
        helper.succeed();
    }

    /**
     * The frying pan, the battlesign and the warmace mine nothing: no rule in the {@code tool}
     * component matches any block, so every block falls back to the default speed and drops nothing
     * extra.
     */
    @GameTest(template = "empty")
    public static void bludgeonsMineNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        assertMinesNothing(helper, ToolAssembly.assemble(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_FRYING_PAN.get()), TWO), "frying pan");
        assertMinesNothing(helper, ToolAssembly.assemble(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_BATTLESIGN.get()), TWO), "battlesign");
        assertMinesNothing(helper, ToolAssembly.assembleAtForge(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_WARMACE.get()), THREE), "warmace");
        helper.succeed();
    }

    /**
     * {@code SwordCore}/{@code FryPan}/{@code BattleSign}'s {@code canDestroyBlockInCreative() =
     * false}, which 1.21 spells {@code Item#canAttackBlock} (vanilla's own {@code SwordItem} and
     * {@code MaceItem} return {@code !player.isCreative()} from it). The battleaxe is a real axe
     * upstream ({@code BattleAxe#setHarvestLevel("axe", 0)}) and keeps instant creative breaking.
     */
    @GameTest(template = "empty")
    public static void weaponsRefuseToBreakBlocksInCreative(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState stone = Blocks.STONE.defaultBlockState();

        for (ToolItem weapon : List.of(ForgeweaveItems.TOOL_BROADSWORD.get(), ForgeweaveItems.TOOL_LONGSWORD.get(),
                ForgeweaveItems.TOOL_RAPIER.get(), ForgeweaveItems.TOOL_CLEAVER.get(),
                ForgeweaveItems.TOOL_FRYING_PAN.get(), ForgeweaveItems.TOOL_BATTLESIGN.get(),
                ForgeweaveItems.TOOL_DAGGER.get(), ForgeweaveItems.TOOL_KATANA.get(),
                ForgeweaveItems.TOOL_SCIMITAR.get(), ForgeweaveItems.TOOL_WARMACE.get())) {
            helper.assertFalse(weapon.canAttackBlock(stone, helper.getLevel(), helper.absolutePos(pos), creative),
                    weapon + " must refuse to break blocks in creative (issue #437)");
            helper.assertTrue(weapon.canAttackBlock(stone, helper.getLevel(), helper.absolutePos(pos), survival),
                    weapon + " must still break blocks in survival");
        }
        helper.assertTrue(ForgeweaveItems.TOOL_BATTLEAXE.get()
                        .canAttackBlock(stone, helper.getLevel(), helper.absolutePos(pos), creative),
                "the battleaxe is an axe upstream and keeps creative block breaking");
        helper.succeed();
    }

    private static void assertMinesNothing(GameTestHelper helper, ItemStack stack, String name) {
        Tool tool = stack.get(DataComponents.TOOL);
        helper.assertTrue(tool != null, "an assembled " + name + " must carry a tool component");
        for (BlockState state : List.of(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(),
                Blocks.COBWEB.defaultBlockState(), Blocks.STONE.defaultBlockState())) {
            helper.assertFalse(tool.isCorrectForDrops(state),
                    "the " + name + " must not be correct-for-drops on " + state);
            helper.assertTrue(tool.getMiningSpeed(state) == 1.0F,
                    "the " + name + " must mine " + state + " at the default speed, got " + tool.getMiningSpeed(state));
        }
    }

    private static void assertSpeed(GameTestHelper helper, String what, float actual, float expected) {
        helper.assertTrue(Math.abs(actual - expected) < 1.0e-4F,
                "expected " + what + " at " + expected + " mining speed, got " + actual);
    }
}
