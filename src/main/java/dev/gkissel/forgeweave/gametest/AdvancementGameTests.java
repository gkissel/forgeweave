package dev.gkissel.forgeweave.gametest;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.ponder.ForgeweavePonderHint;

/**
 * Issue #110's GameTest coverage: the advancement grants that have a real hook to exercise
 * headlessly (structure forming, a melted item, a modifier application), and the Ponder chat hint's
 * one-time-per-player behavior. {@code first_alloy} has no coverage here since nothing fires it yet
 * ({@link dev.gkissel.forgeweave.advancement.ForgeweaveCriteriaTriggers}'s javadoc); #98 adds its own
 * GameTest alongside the trigger call it adds.
 *
 * <p>A real {@link ServerPlayer} (not the plain mock {@code Player} most other GameTests use) is
 * required throughout: {@code PlayerAdvancements} only tracks progress for a player registered with
 * the server, which {@link GameTestHelper#makeMockServerPlayerInLevel()} provides.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class AdvancementGameTests {

    @GameTest(template = "smeltery")
    public static void formingTheStructureGrantsBuildSmelteryAdvancement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        helper.useBlock(SmelteryGameTests.CORE_POS, player);

        helper.assertTrue(isGranted(helper, player, "smeltery/build_smeltery"),
                "expected forming the smeltery through a real player interaction to grant the advancement");
        helper.succeed();
    }

    /** {@link SmelteryControllerBlockEntity#insertForMelting(ItemStack, ServerPlayer)}'s own chosen "first melt" moment: a player-attributed insert into a formed, hot smeltery. */
    @GameTest(template = "smeltery")
    public static void insertingAMeltableItemIntoAHotSmelteryGrantsFirstMeltAdvancement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());

        ItemStack remaining = core.insertForMelting(new ItemStack(Items.IRON_ORE), player);

        helper.assertTrue(remaining.isEmpty(), "expected the iron ore to go into the hot smeltery");
        helper.assertTrue(isGranted(helper, player, "smeltery/first_melt"),
                "expected a player-attributed insert into a hot smeltery to grant the advancement");
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
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        helper.assertFalse(hintShown(player), "expected a fresh player to not have seen the hint yet");

        helper.useBlock(SmelteryGameTests.CORE_POS, player);
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

    private AdvancementGameTests() {}
}
