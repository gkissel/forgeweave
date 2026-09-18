package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.tool.MiningLevel;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * D-M8-9 (issue #968): the mining-level lookup behind the Jade and WTHIT overlay line, one case per
 * rung of the ladder. A GameTest rather than a unit test because both halves read block tags, which
 * only exist once a server has loaded its data.
 *
 * <p>Whether either overlay actually draws the line is not testable here -- Jade is deliberately
 * kept off {@code localRuntime} because its login payload crashes GameTests, and WTHIT has no
 * runtime pair at all (JC-B) -- so that is a release-checklist line on #975. What is testable is
 * every number the line is built from, which is where a wrong rung would come from.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MiningLevelGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    /**
     * The five vanilla rungs, off vanilla blocks. Stone needs a pickaxe but no particular tier, iron
     * ore a stone pickaxe, diamond ore an iron one, and ancient debris a diamond one -- vanilla has
     * no block that asks for netherite, which is why the rung above appears only in the Track B
     * cases below.
     */
    @GameTest(template = "empty")
    public static void vanillaBlocksReportTheirVanillaRung(GameTestHelper helper) {
        assertRequired(helper, Blocks.STONE.defaultBlockState(), 0);
        assertRequired(helper, Blocks.IRON_ORE.defaultBlockState(), 1);
        assertRequired(helper, Blocks.DIAMOND_ORE.defaultBlockState(), 2);
        assertRequired(helper, Blocks.ANCIENT_DEBRIS.defaultBlockState(), 3);
        assertRequired(helper, Blocks.OBSIDIAN.defaultBlockState(), 3);
        helper.succeed();
    }

    /**
     * A block that needs no tool at all reports the bottom rung and, more to the point, produces no
     * overlay line -- "why does this bounce off" has no answer to give for dirt, and the overlays
     * must not grow a line on every block in the game.
     */
    @GameTest(template = "empty")
    public static void aBlockWithNoRequirementReportsTheBottomRungAndNoLine(GameTestHelper helper) {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        assertRequired(helper, dirt, 0);
        helper.assertTrue(MiningLevel.line(dirt, new ItemStack(Items.IRON_PICKAXE)) == null,
                "a block that drops without a correct tool must carry no mining-level line");
        helper.succeed();
    }

    /**
     * Every Track B ore reports the rung its own {@code TrackBOre.Tier} names, including the three
     * #877 minted above netherite -- the rungs nothing else in the game explains, and the reason
     * D-M8-9 exists. Derived by walking {@link TrackBOre#ALL}, so a new ore is covered without a
     * second table to update here.
     */
    @GameTest(template = "empty")
    public static void everyTrackBOreReportsItsOwnRung(GameTestHelper helper) {
        for (TrackBOre ore : TrackBOre.ALL) {
            assertRequired(helper, ForgeweaveBlocks.trackBOre(ore.id()).get().defaultBlockState(),
                    expectedRung(ore.tier()));
        }
        helper.succeed();
    }

    /**
     * The tool half, for a vanilla tool and a Forgeweave one alike: the rung comes off the stack's
     * own deny-drops rule, so a vanilla pickaxe reads correctly with no Forgeweave involvement, and
     * anything that is not a tool with a rung on the ladder reports {@link MiningLevel#NO_TOOL}
     * rather than the bottom rung.
     */
    @GameTest(template = "empty")
    public static void heldToolsReportTheirOwnRung(GameTestHelper helper) {
        assertHeld(helper, new ItemStack(Items.WOODEN_PICKAXE), 0);
        assertHeld(helper, new ItemStack(Items.STONE_PICKAXE), 1);
        assertHeld(helper, new ItemStack(Items.IRON_PICKAXE), 2);
        assertHeld(helper, new ItemStack(Items.DIAMOND_PICKAXE), 3);
        assertHeld(helper, new ItemStack(Items.NETHERITE_PICKAXE), 4);
        assertHeld(helper, ItemStack.EMPTY, MiningLevel.NO_TOOL);
        assertHeld(helper, new ItemStack(Items.STICK), MiningLevel.NO_TOOL);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        assertHeld(helper, ToolAssembly.pickaxe(helper, player, POS, "iron", "wood", "wood"), 2);
        helper.succeed();
    }

    /**
     * The line itself carries both facts side by side when a tool is held, and only the block's when
     * one is not. Asserted on the argument lists rather than on rendered text, since no lang file is
     * loaded on a server.
     */
    @GameTest(template = "empty")
    public static void theLineCarriesBothFactsWhenAToolIsHeld(GameTestHelper helper) {
        BlockState ore = Blocks.DIAMOND_ORE.defaultBlockState();

        helper.assertTrue(argumentCount(helper, ore, new ItemStack(Items.IRON_PICKAXE)) == 2,
                "with a tool in hand the line must name the block's rung and the tool's");
        helper.assertTrue(argumentCount(helper, ore, ItemStack.EMPTY) == 1,
                "with no tool in hand the line must name the block's rung alone");
        helper.succeed();
    }

    /** The rung a {@link TrackBOre.Tier} requires, i.e. its position on the eight-rung ladder. */
    private static int expectedRung(TrackBOre.Tier tier) {
        return switch (tier) {
            case STONE -> 0;
            // Named for the material rung, not for the tag: TrackBOre.Tier.DIAMOND is needs_iron_tool.
            case DIAMOND -> 2;
            case NETHERITE -> 4;
            case HARDCINDER -> 5;
            case WARSPAR -> 6;
            case RESONITE -> 7;
        };
    }

    private static void assertRequired(GameTestHelper helper, BlockState state, int expected) {
        int actual = MiningLevel.required(state);
        helper.assertTrue(actual == expected,
                state.getBlock() + " must need rung " + expected + ", got " + actual);
    }

    private static void assertHeld(GameTestHelper helper, ItemStack stack, int expected) {
        int actual = MiningLevel.held(stack);
        helper.assertTrue(actual == expected,
                stack + " must stand on rung " + expected + ", got " + actual);
    }

    private static int argumentCount(GameTestHelper helper, BlockState state, ItemStack held) {
        net.minecraft.network.chat.Component line = MiningLevel.line(state, held);
        helper.assertTrue(line != null && line.getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents,
                "the line must be a translatable component, got " + line);
        return ((net.minecraft.network.chat.contents.TranslatableContents) line.getContents()).getArgs().length;
    }
}
