package dev.gkissel.forgeweave.ponder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The smeltery multiblock assembly scene (issue #664): the minimum structure {@code SmelteryScan}
 * accepts (a seared floor, walls two blocks tall around a 1x1 interior, one seared tank, the
 * standard core), revealed in build order over the schematic
 * {@code assets/forgeweave/ponder/smeltery.nbt}. The block positions below mirror that schematic --
 * regenerate it with {@code scripts/generate_ponder_schematics.py} if the layout changes, and keep
 * {@code PonderSchematicGameTests} green (it rebuilds the same layout server-side and asserts the
 * real scan forms it).
 *
 * <p>The inline English strings are Ponder's own localization idiom, not stray literals: each
 * {@code text(...)}/{@code title(...)} call registers a {@code forgeweave.ponder.smeltery.*} lang
 * key whose en_US value is extracted by {@code ForgeweaveLanguageProvider} through
 * {@code PonderIndex.getLangAccess().provideLang(...)}, and playback looks the key up via I18n.
 */
public final class ForgeweaveSmelteryScenes {

    /** Mid-south wall, facing out of the structure toward the default camera. */
    private static final BlockPos CORE = new BlockPos(2, 2, 3);

    /** Mid-east wall. */
    private static final BlockPos TANK = new BlockPos(3, 2, 2);

    public static void assembly(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("smeltery", "Assembling a Smeltery");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        // The seared base: the scan needs a floor under the interior; players lay a full square.
        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("A smeltery is built from seared blocks. Start with a floor beneath the space that will hold the melt")
                .pointAt(util.vector().topOf(2, 1, 2))
                .placeNearTarget();
        scene.idle(80);

        // The walls, minus the two special blocks revealed on their own below.
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

        scene.world().showSection(util.select().position(TANK), Direction.WEST);
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("At least one Seared Tank must be part of the walls. It holds the fuel, such as lava, that heats the smeltery")
                .pointAt(util.vector().blockSurface(TANK, Direction.EAST))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(util.select().position(CORE), Direction.NORTH);
        scene.overlay().showControls(util.vector().blockSurface(CORE, Direction.SOUTH), Pointing.RIGHT, 40)
                .withItem(new ItemStack(ForgeweaveItems.STANDARD_CORE.get()));
        scene.idle(10);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Complete the structure by placing a Standard Core in one of the walls, facing outward")
                .pointAt(util.vector().blockSurface(CORE, Direction.SOUTH))
                .placeNearTarget();
        scene.idle(80);

        // The completion cue: the ACTIVE-driven lit front, as when the scan accepts a structure.
        scene.world().modifyBlock(CORE, state -> state.setValue(SmelteryControllerBlock.ACTIVE, true), false);
        scene.effects().indicateSuccess(CORE);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Once the structure is complete the core lights up: the smeltery is formed and ready to melt")
                .pointAt(util.vector().blockSurface(CORE, Direction.SOUTH))
                .placeNearTarget();
        scene.idle(100);

        scene.markAsFinished();
    }

    private ForgeweaveSmelteryScenes() {}
}
