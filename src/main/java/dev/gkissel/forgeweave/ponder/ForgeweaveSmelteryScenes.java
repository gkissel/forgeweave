package dev.gkissel.forgeweave.ponder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The smeltery scenes on the standard core's item: the multiblock assembly (issue #664) -- the
 * minimum structure {@code SmelteryScan} accepts (a seared floor, walls two blocks tall around a
 * 1x1 interior, one seared tank, the standard core), revealed in build order -- and the size
 * variants (issue #700). The block positions below mirror the schematics
 * {@code assets/forgeweave/ponder/smeltery.nbt} and {@code smeltery_sizes.nbt} -- regenerate them
 * with {@code scripts/generate_ponder_schematics.py} if a layout changes, and keep
 * {@code PonderSchematicGameTests} green (it rebuilds the same layouts server-side and asserts the
 * real scan forms them).
 *
 * <p><b>Orientation (#700).</b> Ponder's default camera looks from the north-west, so the core and
 * the tank sit in the north and west walls, facing out of them, where the camera sees their fronts.
 *
 * <p>The inline English strings are Ponder's own localization idiom, not stray literals: each
 * {@code text(...)}/{@code title(...)} call registers a {@code forgeweave.ponder.<scene>.*} lang
 * key whose en_US value is extracted by {@code ForgeweaveLanguageProvider} through
 * {@code PonderIndex.getLangAccess().provideLang(...)}, and playback looks the key up via I18n.
 */
public final class ForgeweaveSmelteryScenes {

    /** Mid-north wall, facing out of the structure toward the default camera. */
    private static final BlockPos CORE = new BlockPos(2, 2, 1);

    /** Mid-west wall. */
    private static final BlockPos TANK = new BlockPos(1, 2, 2);

    public static void assembly(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("smeltery", "Assembling a Smeltery");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("A smeltery is built from seared blocks. Start with a floor beneath the space that will hold the melt")
                .pointAt(util.vector().topOf(2, 1, 2))
                .placeNearTarget();
        scene.idle(80);

        Selection plainWalls = util.select().layersFrom(2)
                .substract(util.select().position(CORE))
                .substract(util.select().position(TANK));
        scene.world().showSection(plainWalls, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Raise walls at least two blocks tall around the interior. The corner columns are optional")
                .pointAt(util.vector().topOf(1, 3, 2))
                .placeNearTarget();
        scene.idle(80);

        scene.world().showSection(util.select().position(TANK), Direction.EAST);
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("At least one Seared Tank must be part of the walls. It holds the fuel, such as lava, that heats the smeltery")
                .pointAt(util.vector().blockSurface(TANK, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(util.select().position(CORE), Direction.SOUTH);
        scene.overlay().showControls(util.vector().blockSurface(CORE, Direction.NORTH), Pointing.LEFT, 40)
                .withItem(new ItemStack(ForgeweaveItems.STANDARD_CORE.get()));
        scene.idle(10);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Complete the structure by placing a Standard Core in one of the walls, facing outward")
                .pointAt(util.vector().blockSurface(CORE, Direction.NORTH))
                .placeNearTarget();
        scene.idle(80);

        scene.world().modifyBlock(CORE, state -> state.setValue(SmelteryControllerBlock.ACTIVE, true), false);
        scene.effects().indicateSuccess(CORE);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Once the structure is complete the core lights up: the smeltery is formed and ready to melt")
                .pointAt(util.vector().blockSurface(CORE, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        scene.markAsFinished();
    }

    // -- the size variants (#700), on smeltery_sizes.nbt: a 9x9 plate with the smallest smeltery
    // south-west (interior (2, 2..3, 6)) and a 3x3x3 one north-east (interior (5..7, 2..4, 1..3)).

    private static final BlockPos SMALL_CORE = new BlockPos(2, 2, 5);
    private static final BlockPos LARGE_CORE = new BlockPos(6, 2, 0);

    /** A plain wall block of the large smeltery's west wall, top layer: where the stairs go for the #369 beat. */
    private static final BlockPos STAIRS_DEMO = new BlockPos(4, 4, 3);

    public static void sizes(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("smeltery_sizes", "Smeltery Sizes");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        Selection small = util.select().fromTo(1, 1, 5, 3, 3, 7);
        Selection large = util.select().fromTo(4, 1, 0, 8, 4, 4);

        scene.world().showSection(small, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("The smallest smeltery has a 1x1 interior: a 3x3 floor and walls two blocks tall, one of them a tank, one the core")
                .pointAt(util.vector().blockSurface(SMALL_CORE, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(large, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("The interior can be any rectangle up to 9x9, and the walls as tall as you like. A bigger interior holds more melt")
                .pointAt(util.vector().topOf(6, 4, 2))
                .placeNearTarget();
        scene.idle(100);

        BlockState stairs = ForgeweaveBlocks.SEARED_STAIRS_BRICKS.get().defaultBlockState();
        scene.world().setBlock(STAIRS_DEMO, stairs, true);
        scene.effects().indicateRedstone(STAIRS_DEMO);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Seared stairs and slabs are not smeltery walls or floor. The walls end below the first layer that holds one, and the interior shrinks with them")
                .pointAt(util.vector().blockSurface(STAIRS_DEMO, Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        scene.world().setBlock(STAIRS_DEMO, ForgeweaveBlocks.SEARED_BRICKS.get().defaultBlockState(), true);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Only the Seared Furnace's ceiling accepts them, laid bottom-half over the interior. Use full seared blocks here")
                .pointAt(util.vector().blockSurface(STAIRS_DEMO, Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        scene.world().modifyBlock(SMALL_CORE, state -> state.setValue(SmelteryControllerBlock.ACTIVE, true), false);
        scene.world().modifyBlock(LARGE_CORE, state -> state.setValue(SmelteryControllerBlock.ACTIVE, true), false);
        scene.effects().indicateSuccess(SMALL_CORE);
        scene.effects().indicateSuccess(LARGE_CORE);
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Either size forms the same way: the core lights up once the floor, walls and tank are all in place")
                .pointAt(util.vector().blockSurface(LARGE_CORE, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }

    private ForgeweaveSmelteryScenes() {}
}
