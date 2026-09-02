package dev.gkissel.forgeweave.ponder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.block.SearedFurnaceControllerBlock;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The seared furnace scene (issue #891), on the controller's item: the closed box
 * {@code SearedFurnaceScan} accepts -- a floor, plain seared walls two courses tall around a 1x1
 * interior, one tank in a corner column, a ceiling with a bottom-half slab over the interior, the
 * controller -- revealed in build order, then what it does and how it differs from a vanilla furnace
 * and from the smeltery. Positions mirror {@code assets/forgeweave/ponder/seared_furnace.nbt}
 * ({@code scripts/generate_ponder_schematics.py}); {@code PonderSchematicGameTests} rebuilds the
 * layout server-side and asserts the real scan forms it. Every rule in the text is
 * {@code SearedFurnaceScan}'s or {@code SearedFurnaceBlockEntity}'s own, cited there.
 *
 * <p>The inline English strings are Ponder's localization idiom (see
 * {@link ForgeweaveSmelteryScenes}): each registers a {@code forgeweave.ponder.seared_furnace.*}
 * lang key that {@code ForgeweaveLanguageProvider} extracts through {@code PonderIndex.getLangAccess()}.
 */
public final class ForgeweaveSearedFurnaceScenes {

    /** Mid-north wall, bottom course, facing the default camera. */
    private static final BlockPos CONTROLLER = new BlockPos(2, 2, 1);

    /** The north-west corner column: the only wall cell the furnace lets a tank take. */
    private static final BlockPos TANK = new BlockPos(1, 2, 1);

    private static final String LAVA = "minecraft:lava";

    public static void assembly(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("seared_furnace", "Assembling a Seared Furnace");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("A Seared Furnace is a closed box of seared blocks. Start with a floor: seared blocks under the interior, and anything at all around its edge")
                .pointAt(util.vector().topOf(2, 1, 2))
                .placeNearTarget();
        scene.idle(90);

        Selection walls = util.select().fromTo(1, 2, 1, 3, 3, 3)
                .substract(util.select().position(CONTROLLER))
                .substract(util.select().position(TANK));
        scene.world().showSection(walls, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Raise walls around the interior. Unlike a smeltery's they take only plain seared blocks: no glass, drains or ducts anywhere")
                .pointAt(util.vector().blockSurface(new BlockPos(1, 3, 2), Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(util.select().position(TANK), Direction.UP);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("At least one Seared Tank holds the fuel. A wall only takes one in a corner column; the edge of the floor or ceiling takes one anywhere")
                .pointAt(util.vector().blockSurface(TANK, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(util.select().layer(4), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Close the top with a ceiling. Over the interior it is seared blocks, or bottom-half seared slabs and stairs; the edge again takes anything")
                .pointAt(util.vector().topOf(2, 4, 2))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(util.select().position(CONTROLLER), Direction.SOUTH);
        scene.overlay().showControls(util.vector().blockSurface(CONTROLLER, Direction.NORTH), Pointing.LEFT, 40)
                .withItem(new ItemStack(ForgeweaveItems.SEARED_FURNACE_CONTROLLER.get()));
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Finish with a Seared Furnace Controller in a wall, facing outward. It lights up once the box is sealed and a tank is in place")
                .pointAt(util.vector().blockSurface(CONTROLLER, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);

        scene.world().modifyBlock(CONTROLLER, state -> state.setValue(SearedFurnaceControllerBlock.ACTIVE, true), false);
        scene.effects().indicateSuccess(CONTROLLER);
        ForgeweaveCastingScenes.searedTank(scene, util, TANK, LAVA, SearedTankBlockEntity.CAPACITY);
        scene.idle(10);
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("It cooks anything a vanilla furnace does, burning smeltery fuel such as lava from its tanks. Every slot holds sixteen and they all cook at once: fifteen slots at this size, three more for every block of interior")
                .pointAt(util.vector().blockSurface(CONTROLLER, Direction.NORTH))
                .placeNearTarget();
        scene.idle(110);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Where a smeltery turns ore into molten metal for casting, the furnace returns the ordinary furnace results as items, faster the hotter its fuel")
                .pointAt(util.vector().blockSurface(TANK, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        scene.markAsFinished();
    }

    private ForgeweaveSearedFurnaceScenes() {}
}
