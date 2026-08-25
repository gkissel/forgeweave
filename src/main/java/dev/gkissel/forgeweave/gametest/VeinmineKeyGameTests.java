package dev.gkissel.forgeweave.gametest;

import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.VeinmineKey;

/**
 * Issue #719: vein mining only happens while the veinmine key is held, only on the blocks the tool
 * family's {@code forgeweave:veinmine/<family>} tag whitelists, and -- for anything but the vein
 * hammer -- only with the veinmine modifier, whose level caps the run. Each test breaks a real
 * block through {@code ServerPlayerGameMode#destroyBlock} with a real assembled tool, the same path
 * {@link LargeToolGameTests} takes; the key state is set directly, which is all the payload does.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class VeinmineKeyGameTests {

    private static final BlockPos STATION = new BlockPos(0, 1, 0);
    private static final BlockPos ORIGIN = new BlockPos(3, 2, 3);
    private static final ResourceLocation VEINMINE = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "veinmine");

    /** Key not held: the vein hammer, whose whole point is the vein, takes one block. */
    @GameTest(template = "empty")
    public static void keyNotHeldMinesOneBlock(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_VEIN_HAMMER.get(), true, 0);
        VeinmineKey.set(player, false);
        row(helper, Blocks.IRON_ORE);

        player.gameMode.destroyBlock(helper.absolutePos(ORIGIN));

        helper.assertBlockPresent(Blocks.AIR, ORIGIN);
        helper.assertBlockPresent(Blocks.IRON_ORE, ORIGIN.offset(1, 0, 0));
        helper.assertBlockPresent(Blocks.IRON_ORE, ORIGIN.offset(2, 0, 0));
        helper.succeed();
    }

    /** Key held, but a plain pickaxe with no veinmine modifier: one block, even on ore. */
    @GameTest(template = "empty")
    public static void keyHeldPlainPickaxeMinesOneBlock(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_PICKAXE.get(), false, 0);
        VeinmineKey.set(player, true);
        row(helper, Blocks.IRON_ORE);

        player.gameMode.destroyBlock(helper.absolutePos(ORIGIN));

        helper.assertBlockPresent(Blocks.AIR, ORIGIN);
        helper.assertBlockPresent(Blocks.IRON_ORE, ORIGIN.offset(1, 0, 0));
        helper.succeed();
    }

    /** Key held, hatchet with veinmine I on a log run: the whole connected run goes. */
    @GameTest(template = "empty")
    public static void keyHeldVeinmineAxeVeinsLogs(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_HATCHET.get(), false, 1);
        VeinmineKey.set(player, true);
        row(helper, Blocks.OAK_LOG);

        player.gameMode.destroyBlock(helper.absolutePos(ORIGIN));

        helper.assertBlockPresent(Blocks.AIR, ORIGIN);
        helper.assertBlockPresent(Blocks.AIR, ORIGIN.offset(1, 0, 0));
        helper.assertBlockPresent(Blocks.AIR, ORIGIN.offset(2, 0, 0));
        helper.succeed();
    }

    /** Key held, hatchet with veinmine on planks: not a log, so one block. */
    @GameTest(template = "empty")
    public static void keyHeldVeinmineAxeOnPlanksMinesOneBlock(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_HATCHET.get(), false, 1);
        VeinmineKey.set(player, true);
        row(helper, Blocks.OAK_PLANKS);

        player.gameMode.destroyBlock(helper.absolutePos(ORIGIN));

        helper.assertBlockPresent(Blocks.AIR, ORIGIN);
        helper.assertBlockPresent(Blocks.OAK_PLANKS, ORIGIN.offset(1, 0, 0));
        helper.succeed();
    }

    /** Key held, pickaxe with veinmine on plain stone: not an ore, so one block. */
    @GameTest(template = "empty")
    public static void keyHeldVeinminePickaxeOnStoneMinesOneBlock(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_PICKAXE.get(), false, 1);
        VeinmineKey.set(player, true);
        row(helper, Blocks.STONE);

        player.gameMode.destroyBlock(helper.absolutePos(ORIGIN));

        helper.assertBlockPresent(Blocks.AIR, ORIGIN);
        helper.assertBlockPresent(Blocks.STONE, ORIGIN.offset(1, 0, 0));
        helper.succeed();
    }

    /**
     * Key held, pickaxe with veinmine III in a 5x5x5 ore cube (125 connected, well past any cap):
     * the origin plus exactly {@code 3 * VEINMINE_BLOCKS_PER_LEVEL} = 12 extra blocks.
     */
    @GameTest(template = "empty")
    public static void keyHeldVeinmineThreeCapsAtTwelveBlocks(GameTestHelper helper) {
        ServerPlayer player = holding(helper, ForgeweaveItems.TOOL_PICKAXE.get(), false, 3);
        VeinmineKey.set(player, true);
        BlockPos center = new BlockPos(3, 3, 3);
        fill(helper, center, 2, Blocks.IRON_ORE.defaultBlockState());

        player.gameMode.destroyBlock(helper.absolutePos(center));

        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            if (helper.getBlockState(pos).isAir()) {
                broken++;
            }
        }
        int expected = 1 + 3 * ForgeweaveModifiers.VEINMINE_BLOCKS_PER_LEVEL;
        helper.assertTrue(broken == expected, "expected the origin plus 12, broke " + broken);
        helper.succeed();
    }

    private static void row(GameTestHelper helper, Block block) {
        helper.setBlock(ORIGIN, block);
        helper.setBlock(ORIGIN.offset(1, 0, 0), block);
        helper.setBlock(ORIGIN.offset(2, 0, 0), block);
    }

    private static void fill(GameTestHelper helper, BlockPos center, int radius, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            helper.setBlock(pos, state);
        }
    }

    /** A real assembled tool in a survival mock player's hand, with {@code veinmineLevel} of the modifier when above 0. */
    private static ServerPlayer holding(GameTestHelper helper, ToolItem tool, boolean forge, int veinmineLevel) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(tool);
        var materials = Collections.nCopies(entry.slotCount(), "stone");
        ItemStack assembled = forge
                ? ToolAssembly.assembleAtForge(helper, player, STATION, entry, materials)
                : ToolAssembly.assemble(helper, player, STATION, entry, materials);
        helper.assertTrue(assembled.is(tool), "expected " + tool + ", got " + assembled);
        if (veinmineLevel > 0) {
            assembled.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(VEINMINE, veinmineLevel)));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, assembled);
        return player;
    }
}
