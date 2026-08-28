package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedFurnaceBlockEntity;
import dev.gkissel.forgeweave.block.SearedFurnaceBlockEntity.Progress;
import dev.gkissel.forgeweave.block.SearedFurnaceControllerBlock;
import dev.gkissel.forgeweave.block.SearedFurnaceScan;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryStructure;

/**
 * The seared furnace multiblock (issue #442): its framed floor/wall/ceiling rules from upstream
 * 1.12's {@code MultiblockSearedFurnace} (NOTICE.md), the {@code 9 + 3 * volume} inventory, a
 * lava-fuelled smelt of a vanilla furnace recipe, the hostile-mob sweep, and the NBT round trip.
 *
 * <p>Layout: floor at y=1 over the interior, walls from y=2 up, ceiling on top; the controller in the
 * middle of the -X wall on the lowest wall layer, facing out, placed last so the real
 * {@code onPlace} trigger is what scans. The one tank sits at the (0, 2, 0) wall corner.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SearedFurnaceGameTests {
    static final BlockPos CORE_POS = new BlockPos(0, 2, 1);
    static final BlockPos TANK_POS = new BlockPos(0, 2, 0);

    @GameTest(template = "smeltery")
    public static void minimumStructureFormsWithTwelveSlots(GameTestHelper helper) {
        buildFurnace(helper, 1, 1, 1);
        SearedFurnaceBlockEntity furnace = placeController(helper);

        SmelteryStructure structure = furnace.structure();
        helper.assertTrue(structure != null, "expected the 1x1x1 furnace to form: " + furnace.lastResult().getString());
        helper.assertValueEqual(structure.interiorVolume(), 1, "interior volume");
        helper.assertValueEqual(furnace.container().getContainerSize(), 9 + 3, "slots for a one-block interior");
        helper.succeed();
    }

    @GameTest(template = "smeltery")
    public static void largerInteriorGrowsTheInventory(GameTestHelper helper) {
        buildFurnace(helper, 3, 3, 2);
        SearedFurnaceBlockEntity furnace = placeController(helper);

        helper.assertTrue(furnace.isFormed(), "expected the 3x3x2 furnace to form: " + furnace.lastResult().getString());
        helper.assertValueEqual(furnace.container().getContainerSize(), 9 + 3 * 18, "slots for an 18-block interior");
        helper.succeed();
    }

    /** Issue #369's rule: bottom-half seared slabs and stairs roof a furnace; top-half ones do not. */
    @GameTest(template = "smeltery")
    public static void ceilingTakesBottomHalfSlabsAndStairs(GameTestHelper helper) {
        buildFurnace(helper, 3, 3, 1);
        BlockState bottomSlab = ForgeweaveBlocks.SEARED_SLAB_BRICKS.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState bottomStairs = ForgeweaveBlocks.SEARED_STAIRS_BRICKS.get().defaultBlockState().setValue(StairBlock.HALF, Half.BOTTOM);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 3, z), (x + z) % 2 == 0 ? bottomSlab : bottomStairs);
            }
        }
        SearedFurnaceBlockEntity furnace = placeController(helper);
        helper.assertTrue(furnace.isFormed(), "expected a slab-and-stairs ceiling to form: " + furnace.lastResult().getString());

        helper.setBlock(new BlockPos(2, 3, 2), bottomSlab.setValue(SlabBlock.TYPE, SlabType.TOP));
        furnace.updateStructure();
        helper.assertTrue(!furnace.isFormed(), "expected a top-half slab in the ceiling to be refused");
        assertReason(helper, furnace, SearedFurnaceScan.KEY_INVALID_CEILING);

        helper.setBlock(new BlockPos(2, 3, 2), bottomStairs.setValue(StairBlock.HALF, Half.TOP));
        furnace.updateStructure();
        helper.assertTrue(!furnace.isFormed(), "expected top-half stairs in the ceiling to be refused");
        assertReason(helper, furnace, SearedFurnaceScan.KEY_INVALID_CEILING);
        helper.succeed();
    }

    /**
     * Issue #369's rule is ceiling-only: upstream's {@code searedStairsSlabs} roster appears in
     * {@code MultiblockSearedFurnace#isCeilingBlock} alone -- {@code isValidBlock} (walls) and the
     * floor's interior footprint stay {@code searedBlock} only, so even a bottom-half seared slab
     * or stairs is refused there.
     */
    @GameTest(template = "smeltery")
    public static void wallsAndFloorRefuseStairsAndSlabs(GameTestHelper helper) {
        buildFurnace(helper, 3, 3, 2);
        BlockState bottomStairs = ForgeweaveBlocks.SEARED_STAIRS_BRICKS.get().defaultBlockState().setValue(StairBlock.HALF, Half.BOTTOM);
        helper.setBlock(new BlockPos(2, 3, 0), bottomStairs);
        SearedFurnaceBlockEntity furnace = placeController(helper);
        helper.assertTrue(!furnace.isFormed(), "expected bottom-half seared stairs in a wall to be refused");
        assertReason(helper, furnace, SearedFurnaceScan.KEY_INVALID_WALL);

        helper.setBlock(new BlockPos(2, 3, 0), ForgeweaveBlocks.SEARED_BRICKS.get());
        helper.setBlock(new BlockPos(2, 1, 2), ForgeweaveBlocks.SEARED_SLAB_BRICKS.get().defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        furnace.updateStructure();
        helper.assertTrue(!furnace.isFormed(), "expected a bottom-half seared slab in the floor to be refused");
        assertReason(helper, furnace, SearedFurnaceScan.KEY_INVALID_FLOOR);

        helper.setBlock(new BlockPos(2, 1, 2), ForgeweaveBlocks.SEARED_BRICKS.get());
        furnace.updateStructure();
        helper.assertTrue(furnace.isFormed(), "expected the repaired furnace to form: " + furnace.lastResult().getString());
        helper.succeed();
    }

    /** Upstream's furnace walls are seared blocks only: no glass, and tanks only at the corners. */
    @GameTest(template = "smeltery")
    public static void wallsRefuseGlassAndOffCornerTanks(GameTestHelper helper) {
        buildFurnace(helper, 3, 3, 2);
        helper.setBlock(new BlockPos(2, 3, 0), ForgeweaveBlocks.SEARED_GLASS.get());
        SearedFurnaceBlockEntity furnace = placeController(helper);
        helper.assertTrue(!furnace.isFormed(), "expected seared glass in a wall to be refused");
        assertReason(helper, furnace, SearedFurnaceScan.KEY_INVALID_WALL);

        helper.setBlock(new BlockPos(2, 3, 0), ForgeweaveBlocks.SEARED_TANK.get());
        furnace.updateStructure();
        helper.assertTrue(!furnace.isFormed(), "expected a tank away from a corner to be refused");
        assertReason(helper, furnace, SearedFurnaceScan.KEY_INVALID_WALL);

        helper.setBlock(new BlockPos(2, 3, 0), ForgeweaveBlocks.SEARED_BRICKS.get());
        furnace.updateStructure();
        helper.assertTrue(furnace.isFormed(), "expected the repaired wall to form: " + furnace.lastResult().getString());
        helper.succeed();
    }

    /** {@code hasTank}: at least one tank in the frame; the floor's outer ring counts as frame too. */
    @GameTest(template = "smeltery")
    public static void aTankIsRequiredAndTheFloorRingCounts(GameTestHelper helper) {
        buildFurnace(helper, 1, 1, 1);
        helper.setBlock(TANK_POS, ForgeweaveBlocks.SEARED_BRICKS.get());
        SearedFurnaceBlockEntity furnace = placeController(helper);
        helper.assertTrue(!furnace.isFormed(), "expected a tankless furnace to be refused");
        assertReason(helper, furnace, SearedFurnaceScan.KEY_NO_TANK);

        // A tank under the wall, in the floor plane's outer ring, is a frame block upstream too.
        helper.setBlock(new BlockPos(2, 1, 1), ForgeweaveBlocks.SEARED_TANK.get());
        furnace.updateStructure();
        helper.assertTrue(furnace.isFormed(), "expected a floor-ring tank to satisfy the tank rule: " + furnace.lastResult().getString());
        helper.succeed();
    }

    /**
     * One cobblestone at lava heat: 200 * 1 / 4 = 50 heat, times 8, at 10 per four ticks -- 160
     * ticks -- then the finishing step swaps in the stone and marks the slot complete.
     */
    @GameTest(template = "smeltery", timeoutTicks = 400)
    public static void lavaSmeltsAVanillaFurnaceRecipe(GameTestHelper helper) {
        SearedFurnaceBlockEntity furnace = lavaFuelledFurnace(helper);
        furnace.container().setItem(0, new ItemStack(Items.COBBLESTONE));

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(furnace.progressState(0) == Progress.HEATING,
                    "expected the cobblestone to be heating, but it was " + furnace.progressState(0));
            helper.assertTrue(furnace.fuel() > 0, "expected a burn to be under way");
        });
        helper.runAfterDelay(260, () -> {
            helper.assertTrue(furnace.container().getItem(0).is(Items.STONE),
                    "expected the cobblestone to have become stone, but the slot holds " + furnace.container().getItem(0));
            helper.assertTrue(furnace.progressState(0) == Progress.COMPLETE,
                    "expected the slot to read complete, but it read " + furnace.progressState(0));
            helper.succeed();
        });
    }

    /** Upstream's no-recipe and no-space markers: sticks cannot smelt; two iron shovels would make two nuggets in a max-1 slot. */
    @GameTest(template = "smeltery")
    public static void unsmeltableAndOversizedStacksAreFlagged(GameTestHelper helper) {
        SearedFurnaceBlockEntity furnace = lavaFuelledFurnace(helper);
        furnace.container().setItem(0, new ItemStack(Items.STICK, 4));
        // updateHeatRequired compares the result count against the *input's* max stack size, so a
        // pair of unstackable shovels (-> two nuggets) is refused even though nuggets stack fine.
        furnace.container().setItem(1, new ItemStack(Items.IRON_SHOVEL, 2));
        furnace.container().setItem(2, new ItemStack(Items.IRON_SHOVEL, 1));
        helper.assertTrue(furnace.progressState(0) == Progress.NO_RECIPE,
                "expected sticks to read no_recipe, got " + furnace.progressState(0));
        helper.assertTrue(furnace.progressState(1) == Progress.NO_SPACE,
                "expected two shovels to read no_space, got " + furnace.progressState(1));
        helper.assertTrue(furnace.progressState(2) == Progress.HEATING,
                "expected one shovel to heat, got " + furnace.progressState(2));
        helper.succeed();
    }

    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void hostileMobsInsideAreKilled(GameTestHelper helper) {
        buildFurnace(helper, 3, 3, 2);
        SearedFurnaceBlockEntity furnace = placeController(helper);
        helper.assertTrue(furnace.isFormed(), "expected the furnace to form: " + furnace.lastResult().getString());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(!zombie.isAlive(), "expected the zombie inside a formed furnace to be gone");
            helper.succeed();
        });
    }

    /**
     * #772: the same #757 visual-gap defect as {@code SmelteryControllerBlock} -- closing the last
     * gap in a wall three blocks from the controller sends it no neighbour update either, so nothing
     * would notice the structure forming until a player interacts. Deliberately never reads {@link
     * SearedFurnaceBlockEntity#structure()} (which would trigger its own revalidation-on-read and
     * mask the bug being fixed) between closing the gap and asserting -- only the controller's own
     * bounded settle-window recheck tick may notice it.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void closingADistantWallFormsTheFurnaceWithoutInteraction(GameTestHelper helper) {
        buildFurnace(helper, 3, 3, 2);
        BlockPos hole = new BlockPos(4, 3, 2);
        helper.setBlock(hole, Blocks.AIR);
        SearedFurnaceBlockEntity furnace = placeController(helper);
        helper.assertTrue(!helper.getBlockState(CORE_POS).getValue(SearedFurnaceControllerBlock.ACTIVE),
                "expected the hole to leave the controller reading unformed: " + furnace.lastResult().getString());

        helper.setBlock(hole, ForgeweaveBlocks.SEARED_BRICKS.get());
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(helper.getBlockState(CORE_POS).getValue(SearedFurnaceControllerBlock.ACTIVE),
                    "expected the controller's front to relight once the distant wall closed the structure, without any interaction");
            helper.succeed();
        });
    }

    @GameTest(template = "smeltery")
    public static void stateRoundTripsThroughNbt(GameTestHelper helper) {
        SearedFurnaceBlockEntity furnace = lavaFuelledFurnace(helper);
        furnace.container().setItem(2, new ItemStack(Items.COBBLESTONE, 5));
        SmelteryStructure structure = furnace.structure();

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag tag = furnace.saveWithFullMetadata(registries);
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(CORE_POS));
        BlockEntity reloaded = BlockEntity.loadStatic(helper.absolutePos(CORE_POS), state, tag, registries);

        helper.assertTrue(reloaded instanceof SearedFurnaceBlockEntity, "expected the saved tag to rebuild a seared furnace");
        SearedFurnaceBlockEntity loaded = (SearedFurnaceBlockEntity) reloaded;
        helper.assertTrue(structure.equals(loaded.structure()), "expected the structure bounds to survive an NBT round trip");
        helper.assertValueEqual(loaded.container().getContainerSize(), 12, "slot count after an NBT round trip");
        helper.assertValueEqual(loaded.container().getItem(2).getCount(), 5, "stack in slot 2 after an NBT round trip");
        helper.assertTrue(loaded.container().getItem(2).is(Items.COBBLESTONE), "expected cobblestone back in slot 2");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Floor at y=1 over the interior, walls y=2.., ceiling of seared bricks over the interior, and
     * one tank at the (0, 2, 0) wall corner. Interior x/z from 1, so the walls run at 0 and
     * {@code size + 1}.
     */
    static void buildFurnace(GameTestHelper helper, int width, int depth, int height) {
        for (int x = 1; x <= width; x++) {
            for (int z = 1; z <= depth; z++) {
                helper.setBlock(new BlockPos(x, 1, z), ForgeweaveBlocks.SEARED_BRICKS.get());
                helper.setBlock(new BlockPos(x, 2 + height, z), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
        }
        for (int y = 2; y < 2 + height; y++) {
            for (int x = 0; x <= width + 1; x++) {
                helper.setBlock(new BlockPos(x, y, 0), ForgeweaveBlocks.SEARED_BRICKS.get());
                helper.setBlock(new BlockPos(x, y, depth + 1), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
            for (int z = 1; z <= depth; z++) {
                helper.setBlock(new BlockPos(0, y, z), ForgeweaveBlocks.SEARED_BRICKS.get());
                helper.setBlock(new BlockPos(width + 1, y, z), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
        }
        helper.setBlock(TANK_POS, ForgeweaveBlocks.SEARED_TANK.get());
    }

    static SearedFurnaceBlockEntity placeController(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ForgeweaveBlocks.SEARED_FURNACE_CONTROLLER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        return helper.getBlockEntity(CORE_POS);
    }

    private static SearedFurnaceBlockEntity lavaFuelledFurnace(GameTestHelper helper) {
        buildFurnace(helper, 1, 1, 1);
        SearedFurnaceBlockEntity furnace = placeController(helper);
        SearedTankBlockEntity tank = helper.getBlockEntity(TANK_POS);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(furnace.isFormed(), "expected the test furnace to form: " + furnace.lastResult().getString());
        return furnace;
    }

    private static void assertReason(GameTestHelper helper, SearedFurnaceBlockEntity furnace, String expectedKey) {
        Component message = furnace.lastResult();
        String key = message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "<literal>";
        helper.assertTrue(expectedKey.equals(key), "expected the controller to report " + expectedKey + " but it reported " + key);
    }
}
