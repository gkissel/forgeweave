package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryScan;
import dev.gkissel.forgeweave.block.SmelteryStructure;

/**
 * Issue #289's verification on a headless dedicated server: plain seared glass is a valid smeltery
 * wall block but, matching upstream 1.12's {@code MultiblockSmeltery#isFloorBlock} override (only
 * plain seared blocks qualify as floor, NOTICE.md), it is rejected as a floor block.
 *
 * <p>Reuses {@link SmelteryGameTests}'s {@code buildWalls}/{@code placeCore} fixtures and swaps one
 * wall or floor block for seared glass, the same way {@link SmelteryGameTests#wallHoleIsRejectedWithAReason}
 * and {@link SmelteryGameTests#wallsWithoutATankAreRejected} substitute a single block.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SearedGlassGameTests {

    /** Seared glass in a wall, replacing one seared-brick wall block, still lets the smeltery form. */
    @GameTest(template = "smeltery")
    public static void searedGlassInAWallForms(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        // The east wall block at the core's own layer -- not the tank slot, not the core's own slot.
        helper.setBlock(new BlockPos(2, 2, 1), ForgeweaveBlocks.SEARED_GLASS.get());
        BlockPos core = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SmelteryStructure structure = helper.<SmelteryControllerBlockEntity>getBlockEntity(core).structure();
        helper.assertTrue(structure != null,
                "expected seared glass in a wall to still let the smeltery form: "
                        + helper.<SmelteryControllerBlockEntity>getBlockEntity(core).lastResult().getString());
        helper.succeed();
    }

    /** Seared glass in the floor, upstream's {@code isFloorBlock} exclusion, fails the scan. */
    @GameTest(template = "smeltery")
    public static void searedGlassInTheFloorFailsTheScan(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        helper.setBlock(new BlockPos(1, 1, 1), ForgeweaveBlocks.SEARED_GLASS.get());
        BlockPos core = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        helper.assertTrue(blockEntity.structure() == null, "expected seared glass in the floor to be rejected");

        Component message = blockEntity.lastResult();
        String key = message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "<literal>";
        helper.assertTrue(SmelteryScan.KEY_INVALID_FLOOR.equals(key),
                "expected the core to report " + SmelteryScan.KEY_INVALID_FLOOR + " but it reported " + key);
        helper.succeed();
    }
}
