package dev.gkissel.forgeweave.gametest;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.ponder.ForgeweavePonderHint;

/**
 * Issue #110's GameTest coverage: the two advancement grants with a real hook to exercise headlessly
 * (structure forming, a modifier application), and the Ponder chat hint's one-time-per-player
 * behavior. {@code first_melt} and {@code first_alloy} have no coverage here since nothing fires them
 * yet ({@link dev.gkissel.forgeweave.advancement.ForgeweaveCriteriaTriggers}'s javadoc); #96/#98 add
 * their own GameTests alongside the trigger call each adds.
 *
 * <p>A real {@link ServerPlayer} (not the plain mock {@code Player} most other GameTests use) is
 * required throughout: {@code PlayerAdvancements} only tracks progress for a player registered with
 * the server, which {@link GameTestHelper#makeMockServerPlayerInLevel()} provides.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class AdvancementGameTests {
    private static final BlockPos CORE_POS = new BlockPos(0, 2, 1);

    @GameTest(template = "smeltery")
    public static void formingTheStructureGrantsBuildSmelteryAdvancement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        buildMinimalSmeltery(helper);

        helper.useBlock(CORE_POS, player);

        helper.assertTrue(isGranted(helper, player, "smeltery/build_smeltery"),
                "expected forming the smeltery through a real player interaction to grant the advancement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void applyingAModifierGrantsFirstModifierAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(!output.isEmpty(), "expected one redstone to apply haste before checking the advancement");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);

        helper.assertTrue(isGranted(helper, player, "smeltery/first_modifier"),
                "expected taking a modifier application's output to grant the advancement");
        helper.succeed();
    }

    /** Server-side half of the one-time hint: the persisted flag ({@code ForgeweavePonderHint}) is what makes "once" durable. */
    @GameTest(template = "smeltery")
    public static void ponderHintIsShownOnlyOnceForANewPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        buildMinimalSmeltery(helper);
        helper.assertFalse(hintShown(player), "expected a fresh player to not have seen the hint yet");

        helper.useBlock(CORE_POS, player);
        helper.assertTrue(hintShown(player), "expected the first controller interaction to record the hint as shown");

        // A second interaction (or a direct call, same as a second controller click) must stay a no-op.
        ForgeweavePonderHint.maybeShow(player);
        helper.assertTrue(hintShown(player), "expected a second call to remain a no-op rather than un-set the flag");
        helper.succeed();
    }

    private static boolean isGranted(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static boolean hintShown(ServerPlayer player) {
        return player.getPersistentData().getCompound("PlayerPersisted").getBoolean("forgeweave_ponder_hint_shown");
    }

    /**
     * The minimum smeltery: a 1x1 interior two blocks tall over a seared floor, core placed last in
     * the open -X wall slot -- the same shape {@code SmelteryGameTests#minimumStructureForms} builds
     * (that method's own wall-building helper is private to that class, so this is a small, deliberate
     * duplicate rather than a cross-file API change to a file issue #95 still owns).
     */
    private static void buildMinimalSmeltery(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), ForgeweaveBlocks.SEARED_BRICKS.get());
        for (int y = 2; y <= 3; y++) {
            helper.setBlock(new BlockPos(1, y, 0), ForgeweaveBlocks.SEARED_BRICKS.get());
            helper.setBlock(new BlockPos(1, y, 2), ForgeweaveBlocks.SEARED_BRICKS.get());
            helper.setBlock(new BlockPos(0, y, 1), ForgeweaveBlocks.SEARED_BRICKS.get());
            helper.setBlock(new BlockPos(2, y, 1), ForgeweaveBlocks.SEARED_BRICKS.get());
        }
        // A smeltery needs at least one tank in its walls (upstream's hasTank check).
        helper.setBlock(new BlockPos(1, 2, 0), ForgeweaveBlocks.SEARED_TANK.get());
        // Placed last, overwriting the -X wall block just above, so the scan runs off the real
        // placement event with the structure already complete (mirrors SmelteryGameTests#placeCore).
        helper.setBlock(CORE_POS, ForgeweaveBlocks.STANDARD_CORE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
    }

    private AdvancementGameTests() {}
}
