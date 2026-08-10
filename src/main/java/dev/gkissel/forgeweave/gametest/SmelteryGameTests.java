package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryCore;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryScan;
import dev.gkissel.forgeweave.block.SmelteryStructure;
import dev.gkissel.forgeweave.menu.SmelteryMenu;

/**
 * Covers docs/SCOPE.md M2 issue #95's verification on a headless dedicated server: the smeltery
 * multiblock forms at its minimum and maximum size, rejects an oversized interior and a hole in a
 * wall (with a reason a player can read), notices a wall broken far from the core without the block
 * entity ever ticking, and round-trips its formed state through NBT.
 *
 * <p>Structures are built with the core placed last, so what is exercised is the real trigger --
 * {@code SmelteryControllerBlock#onPlace} -- rather than a direct call into the scan.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SmelteryGameTests {
    /** Where the core always goes: the middle of the -X wall, on the lowest wall layer. */
    static final BlockPos CORE_POS = new BlockPos(0, 2, 1);

    /** The one seared tank {@link #buildWalls} puts in the walls; issue #96's tests fill it with lava. */
    static final BlockPos TANK_POS = new BlockPos(1, 2, 0);

    @GameTest(template = "smeltery")
    public static void minimumStructureForms(GameTestHelper helper) {
        // SCOPE.md's "minimum 3x3x3 smeltery": a 1x1 interior two blocks tall over a seared floor.
        buildWalls(helper, 1, 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SmelteryStructure structure = structureOf(helper, core);
        helper.assertTrue(structure != null, "expected the minimum smeltery to form: " + reason(helper, core));
        helper.assertValueEqual(structure.width(), 1, "interior width");
        helper.assertValueEqual(structure.depth(), 1, "interior depth");
        helper.assertValueEqual(structure.height(), 2, "interior height");
        helper.succeed();
    }

    @GameTest(template = "smeltery")
    public static void maximumInteriorForms(GameTestHelper helper) {
        buildWalls(helper, SmelteryScan.MAX_SIZE, SmelteryScan.MAX_SIZE, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SmelteryStructure structure = structureOf(helper, core);
        helper.assertTrue(structure != null, "expected a 9x9 interior to form: " + reason(helper, core));
        helper.assertValueEqual(structure.interiorVolume(), SmelteryScan.MAX_SIZE * SmelteryScan.MAX_SIZE * 2, "interior volume");
        helper.succeed();
    }

    @GameTest(template = "smeltery")
    public static void oversizedInteriorIsRejected(GameTestHelper helper) {
        buildWalls(helper, SmelteryScan.MAX_SIZE + 1, SmelteryScan.MAX_SIZE + 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        helper.assertTrue(structureOf(helper, core) == null, "expected a 10x10 interior to be rejected");
        assertReason(helper, core, SmelteryScan.KEY_TOO_LARGE);
        helper.succeed();
    }

    @GameTest(template = "smeltery")
    public static void wallHoleIsRejectedWithAReason(GameTestHelper helper) {
        buildWalls(helper, 3, 3, 2);
        // Punch a hole in the far wall on the core's own layer, so no wall layer can complete.
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.AIR);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        helper.assertTrue(structureOf(helper, core) == null, "expected a hole in a wall to be rejected");
        assertReason(helper, core, SmelteryScan.KEY_INVALID_WALL);
        helper.succeed();
    }

    @GameTest(template = "smeltery")
    public static void wallsWithoutATankAreRejected(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        helper.setBlock(new BlockPos(1, 2, 0), ForgeweaveBlocks.SEARED_BRICKS.get()); // replace the tank
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        helper.assertTrue(structureOf(helper, core) == null, "expected a tankless smeltery to be rejected");
        assertReason(helper, core, SmelteryScan.KEY_NO_TANK);
        helper.succeed();
    }

    /**
     * Breaking a wall four blocks from the core sends it no neighbour update, so this is what proves
     * the revalidation-on-read path in {@link SmelteryControllerBlockEntity#structure()} works --
     * the block entity has no ticker to notice it any other way. The delay is one tick past that
     * method's rescan interval.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void breakingADistantWallInvalidatesTheStructure(GameTestHelper helper) {
        buildWalls(helper, 3, 3, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        helper.assertTrue(structureOf(helper, core) != null, "expected the smeltery to form first: " + reason(helper, core));

        helper.setBlock(new BlockPos(4, 2, 2), Blocks.AIR);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(structureOf(helper, core) == null, "expected the broken wall to invalidate the structure");
            assertReason(helper, core, SmelteryScan.KEY_INVALID_WALL);
            helper.succeed();
        });
    }

    @GameTest(template = "smeltery")
    public static void netherCoreCarriesItsOwnTier(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.NETHER_CORE.get());

        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        helper.assertTrue(blockEntity.isFormed(), "expected a Nether Core to form a smeltery too: " + reason(helper, core));
        helper.assertTrue(blockEntity.core() == SmelteryCore.NETHER, "expected the Nether Core tier to be recorded");
        helper.assertTrue(blockEntity.core().yieldMultiplier() > SmelteryCore.STANDARD.yieldMultiplier(),
                "expected the Nether Core to out-yield the Standard Core");
        helper.succeed();
    }

    /** SCOPE.md M2's save-compat fixture list calls out "smeltery block-entity NBT (tank fluids, structure bounds)". */
    @GameTest(template = "smeltery")
    public static void formedStateRoundTripsThroughNbt(GameTestHelper helper) {
        buildWalls(helper, 3, 3, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        SmelteryStructure structure = blockEntity.structure();
        helper.assertTrue(structure != null, "expected the smeltery to form first: " + reason(helper, core));
        blockEntity.tank().fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag tag = blockEntity.saveWithFullMetadata(registries);
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(core));
        BlockEntity reloaded = BlockEntity.loadStatic(helper.absolutePos(core), state, tag, registries);

        helper.assertTrue(reloaded instanceof SmelteryControllerBlockEntity, "expected the saved tag to rebuild a smeltery core");
        SmelteryControllerBlockEntity loaded = (SmelteryControllerBlockEntity) reloaded;
        helper.assertTrue(structure.equals(loaded.structure()), "expected the structure bounds to survive an NBT round trip");
        helper.assertValueEqual(loaded.tank().getFluidAmount(), 500, "tank contents after an NBT round trip");
        helper.assertValueEqual(loaded.tank().getCapacity(),
                structure.interiorVolume() * SmelteryControllerBlockEntity.CAPACITY_PER_BLOCK, "tank capacity after an NBT round trip");
        helper.succeed();
    }

    // ---------------------------------------------------------------- #101: the smeltery GUI

    /**
     * Opening the GUI has to give the client the melt as the server sees it: every fluid, in order,
     * with its amount, plus the interior-scaled capacity the fluid column is drawn against.
     *
     * <p>Asserted through {@link SmelteryMenu}'s own accessors rather than the block entity's, since
     * those are what the screen reads.
     */
    @GameTest(template = "smeltery")
    public static void menuExposesTheTankContents(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        helper.assertTrue(blockEntity.isFormed(), "expected the smeltery to form first: " + reason(helper, core));

        blockEntity.tank().fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
        blockEntity.tank().fill(new FluidStack(Fluids.LAVA, 250), IFluidHandler.FluidAction.EXECUTE);

        SmelteryMenu menu = openMenu(helper, core);
        List<FluidStack> fluids = menu.fluids(helper.getLevel());
        helper.assertValueEqual(fluids.size(), 2, "fluids visible in the menu");
        helper.assertTrue(fluids.get(0).is(Fluids.WATER), "expected the first-filled fluid at the bottom of the melt");
        helper.assertValueEqual(fluids.get(0).getAmount(), 500, "bottom fluid amount");
        helper.assertValueEqual(fluids.get(1).getAmount(), 250, "second fluid amount");
        helper.assertValueEqual(menu.capacity(helper.getLevel()),
                2 * SmelteryControllerBlockEntity.CAPACITY_PER_BLOCK, "capacity the menu reports");
        helper.succeed();
    }

    /**
     * The one mutation this GUI has: clicking a fluid makes it the drain fluid. The click arrives as
     * a menu button id, so this drives {@link SmelteryMenu#clickMenuButton} the way the server's
     * button-click packet handler does -- proving the round trip lands on the tank and, since the
     * bottom fluid is what a drain pours, that the choice actually takes effect.
     */
    @GameTest(template = "smeltery")
    public static void clickingAFluidMakesItTheDrainFluid(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        blockEntity.tank().fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
        blockEntity.tank().fill(new FluidStack(Fluids.LAVA, 250), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(blockEntity.tank().getFluid().is(Fluids.WATER), "expected water at the bottom to begin with");

        SmelteryMenu menu = openMenu(helper, core);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(menu.clickMenuButton(player, 1), "expected the menu to accept a fluid click");

        helper.assertTrue(blockEntity.tank().getFluid().is(Fluids.LAVA), "expected the clicked fluid to move to the bottom");
        helper.assertValueEqual(blockEntity.tank().getFluid().getAmount(), 250, "the moved fluid keeps its amount");
        FluidStack drained = blockEntity.tank().drain(50, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(drained.is(Fluids.LAVA), "expected a drain to pour the selected fluid");

        // An index past the end of the melt is a forged click and must change nothing.
        menu.clickMenuButton(player, 99);
        helper.assertTrue(blockEntity.tank().getFluid().is(Fluids.LAVA), "expected an out-of-range click to be ignored");
        helper.succeed();
    }

    /** The GUI's numbers are all functions of the interior, so a broken structure has to close it. */
    @GameTest(template = "smeltery")
    public static void menuClosesWhenTheStructureBreaks(GameTestHelper helper) {
        buildWalls(helper, 3, 3, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryMenu menu = openMenu(helper, core);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new BlockPos(0, 2, 1).getCenter()));
        helper.assertTrue(menu.stillValid(player), "expected a formed smeltery's menu to stay open");

        helper.setBlock(new BlockPos(4, 2, 2), Blocks.AIR);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(!menu.stillValid(player), "expected a broken structure to close the menu");
            helper.succeed();
        });
    }

    /**
     * A world saved before #101 stored the tank as a single-fluid NeoForge {@code FluidTank}, i.e.
     * one {@code Fluid} compound instead of a {@code fluids} list. Loading one must not silently
     * empty the smeltery.
     */
    @GameTest(template = "smeltery")
    public static void aPreExistingSingleFluidTankStillLoads(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        // The exact shape FluidTank#writeToNBT produced on master before this issue.
        CompoundTag tag = blockEntity.saveWithFullMetadata(registries);
        CompoundTag legacyTank = new CompoundTag();
        legacyTank.put("Fluid", new FluidStack(Fluids.LAVA, 777).save(registries));
        tag.put("tank", legacyTank);

        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(core));
        BlockEntity reloaded = BlockEntity.loadStatic(helper.absolutePos(core), state, tag, registries);
        SmelteryControllerBlockEntity loaded = (SmelteryControllerBlockEntity) reloaded;

        helper.assertValueEqual(loaded.tank().getFluidAmount(), 777, "a pre-#101 tank's contents after loading");
        helper.assertValueEqual(loaded.tank().fluids().size(), 1, "fluids decoded from the legacy shape");
        helper.assertTrue(loaded.tank().getFluid().is(Fluids.LAVA), "the legacy fluid's identity");
        helper.succeed();
    }

    /**
     * The GUI insertion path the issue asks for: a player picks up a meltable item and clicks it into
     * a melt slot. Driven through {@link net.minecraft.world.inventory.AbstractContainerMenu#clicked},
     * which is the exact server-side entry point the click packet reaches, so this covers the
     * dedicated-server round trip rather than a direct call into the inventory.
     */
    @GameTest(template = "smeltery")
    public static void clickingAnItemIntoAMeltSlotReachesTheMeltingInventory(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        helper.assertTrue(blockEntity.isFormed(), "expected the smeltery to form first: " + reason(helper, core));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SmelteryMenu menu = openMenu(helper, core);
        helper.assertValueEqual(menu.meltSlotCount(), 2, "melt slots for a 1x1x2 interior");

        // Carry an iron ingot, then click it into melt slot 0 the way the click packet does.
        menu.setCarried(new ItemStack(Items.IRON_INGOT));
        menu.clicked(0, 0, ClickType.PICKUP, player);

        helper.assertValueEqual(blockEntity.meltingItems().get(0).getItem(), Items.IRON_INGOT,
                "the item the player clicked into melt slot 0");
        helper.assertTrue(menu.getCarried().isEmpty(), "the whole stack of one should have left the cursor");
        helper.succeed();
    }

    /** A slot that only takes meltables is what keeps the grid from becoming general storage. */
    @GameTest(template = "smeltery")
    public static void aMeltSlotRefusesSomethingWithNoMeltingRecipe(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SmelteryMenu menu = openMenu(helper, core);
        menu.setCarried(new ItemStack(Items.STICK));
        menu.clicked(0, 0, ClickType.PICKUP, player);

        helper.assertTrue(blockEntity.meltingItems().get(0).isEmpty(), "a stick is not meltable and must be refused");
        helper.assertTrue(!menu.getCarried().isEmpty(), "the refused item stays on the cursor");
        helper.succeed();
    }

    /**
     * End to end through the GUI: click an ingot in and watch #96's melt loop actually work on it.
     * This is what proves the grid is wired to the real melting inventory rather than to one nothing
     * consumes -- and that the heat bars the screen draws have a moving number behind them.
     *
     * <p>Progress rather than a finished melt on purpose: {@code heatRequired} for molten iron is
     * {@code (769 - 300) * 8 = 3752}, advancing 10 per melt tick, so a completed melt is ~1500 ticks
     * of test. Two samples prove the same wiring in a tenth of the time; #96 owns the
     * melt-to-completion coverage.
     */
    @GameTest(template = "smeltery", timeoutTicks = 300)
    public static void anItemClickedInThroughTheGuiIsPickedUpByTheMeltLoop(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        // Lava in the wall tank is what #96's heat model runs on.
        helper.<SearedTankBlockEntity>getBlockEntity(new BlockPos(1, 2, 0)).tank()
                .fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SmelteryMenu menu = openMenu(helper, core);
        menu.setCarried(new ItemStack(Items.IRON_INGOT));
        menu.clicked(0, 0, ClickType.PICKUP, player);

        helper.runAfterDelay(60, () -> {
            float early = blockEntity.meltProgress(0);
            helper.assertTrue(early > 0f,
                    "expected the melt loop to have started on the GUI-inserted ingot, but progress was " + early);
            helper.runAfterDelay(60, () -> {
                float later = blockEntity.meltProgress(0);
                helper.assertTrue(later > early,
                        "expected melt progress to keep climbing, but it went " + early + " -> " + later);
                helper.succeed();
            });
        });
    }

    /**
     * An unmeltable item in the grid must not burn fuel (defect found by #96 against merged master,
     * fixed in #101).
     *
     * <p>{@code hasMeltableItem()} used to mean "any slot is non-empty", and #97 gates
     * {@code consumeFuel()} on it. So a stick sitting in the melting inventory armed a melt tick,
     * drained a whole fuel charge from the wall tank, found no recipe to heat, and returned without
     * decrementing the burn -- draining the tank a charge at a time on every insert, right-click,
     * neighbour change or chunk load.
     *
     * <p>Placed through {@link SmelteryControllerBlockEntity#meltingContainer()} rather than
     * {@code insertForMelting}, because that path refuses unmeltables outright and so cannot reach
     * the bug; the container is the surface the GUI's slots sit on.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void anUnmeltableItemInTheGridBurnsNoFuel(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        SearedTankBlockEntity wallTank = helper.getBlockEntity(TANK_POS);
        wallTank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        helper.assertTrue(blockEntity.isFormed(), "expected the smeltery to form first: " + reason(helper, core));

        blockEntity.meltingContainer().setItem(0, new ItemStack(Items.STICK));
        int fuelBefore = wallTank.tank().getFluidAmount();

        // Every path that arms a melt tick: a direct rescan (right-click / neighbour change) and the
        // scheduled ticks that follow. If the smeltery is burning fuel for nothing, this is when.
        blockEntity.updateStructure();
        helper.runAfterDelay(60, () -> {
            blockEntity.updateStructure();
            helper.runAfterDelay(60, () -> {
                helper.assertValueEqual(wallTank.tank().getFluidAmount(), fuelBefore,
                        "fuel burned while the grid held only an unmeltable item");
                helper.assertTrue(blockEntity.tank().getFluidAmount() == 0,
                        "expected nothing to have been melted");
                helper.succeed();
            });
        });
    }

    /** The container must still report itself non-empty while it holds an unmeltable item. */
    @GameTest(template = "smeltery")
    public static void theMeltGridIsNotEmptyWhileItHoldsAnUnmeltableItem(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);

        helper.assertTrue(blockEntity.meltingContainer().isEmpty(), "a fresh melt grid is empty");
        blockEntity.meltingContainer().setItem(0, new ItemStack(Items.STICK));
        // Container#isEmpty means "holds nothing", not "holds nothing useful" -- hoppers and
        // comparators read it, so it must not follow the meltable check.
        helper.assertTrue(!blockEntity.meltingContainer().isEmpty(),
                "a grid holding a stick is not an empty container");
        helper.succeed();
    }

    /**
     * #97 follow-up (PR #126's flagged gap): the fuel gauge needs real fluid data, not just the
     * synced burn temperature. Lava poured before the core forms means the very first scan
     * ({@code onPlace}) already refreshes the display snapshot, so the menu sees it immediately with
     * no need to wait for a melt tick.
     */
    @GameTest(template = "smeltery")
    public static void menuExposesFuelGaugeData(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        helper.<SearedTankBlockEntity>getBlockEntity(TANK_POS).tank()
                .fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        helper.assertTrue(blockEntity.isFormed(), "expected the smeltery to form first: " + reason(helper, core));

        SmelteryMenu menu = openMenu(helper, core);
        FluidStack fuel = menu.fuel(helper.getLevel());
        helper.assertTrue(!fuel.isEmpty(), "expected the fuel gauge to see the wall tank's lava");
        helper.assertTrue(fuel.is(Fluids.LAVA), "expected lava as the displayed fuel fluid");
        helper.assertValueEqual(fuel.getAmount(), SearedTankBlockEntity.CAPACITY, "fuel gauge amount");
        helper.assertValueEqual(menu.fuelCapacity(helper.getLevel()), SearedTankBlockEntity.CAPACITY, "fuel gauge capacity");
        helper.succeed();
    }

    /** The menu the controller's own {@code createMenu} builds, i.e. what a right-click produces. */
    private static SmelteryMenu openMenu(GameTestHelper helper, BlockPos core) {
        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        return new SmelteryMenu(1, helper.makeMockPlayer(GameType.SURVIVAL).getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(core)), helper.absolutePos(core),
                blockEntity.meltingContainer());
    }

    /**
     * Floor, four walls and one tank around an interior of {@code width} x {@code depth} x
     * {@code height}, with the interior starting at relative (1, 2, 1). The core's slot in the -X
     * wall is left open for {@link #placeCore}.
     */
    static void buildWalls(GameTestHelper helper, int width, int depth, int height) {
        for (int x = 1; x <= width; x++) {
            for (int z = 1; z <= depth; z++) {
                helper.setBlock(new BlockPos(x, 1, z), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
        }
        for (int y = 2; y < 2 + height; y++) {
            for (int x = 1; x <= width; x++) {
                helper.setBlock(new BlockPos(x, y, 0), ForgeweaveBlocks.SEARED_BRICKS.get());
                helper.setBlock(new BlockPos(x, y, depth + 1), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
            for (int z = 1; z <= depth; z++) {
                helper.setBlock(new BlockPos(0, y, z), ForgeweaveBlocks.SEARED_BRICKS.get());
                helper.setBlock(new BlockPos(width + 1, y, z), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
        }
        // A smeltery needs at least one tank in its walls (upstream's hasTank check).
        helper.setBlock(TANK_POS, ForgeweaveBlocks.SEARED_TANK.get());
    }

    /** Placed last so the scan runs off the real placement event with the structure already complete. */
    static BlockPos placeCore(GameTestHelper helper, Block core) {
        helper.setBlock(CORE_POS, core.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        return CORE_POS;
    }

    private static SmelteryStructure structureOf(GameTestHelper helper, BlockPos core) {
        return helper.<SmelteryControllerBlockEntity>getBlockEntity(core).structure();
    }

    private static String reason(GameTestHelper helper, BlockPos core) {
        return helper.<SmelteryControllerBlockEntity>getBlockEntity(core).lastResult().getString();
    }

    private static void assertReason(GameTestHelper helper, BlockPos core, String expectedKey) {
        Component message = helper.<SmelteryControllerBlockEntity>getBlockEntity(core).lastResult();
        String key = message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "<literal>";
        helper.assertTrue(expectedKey.equals(key), "expected the core to report " + expectedKey + " but it reported " + key);
    }
}
