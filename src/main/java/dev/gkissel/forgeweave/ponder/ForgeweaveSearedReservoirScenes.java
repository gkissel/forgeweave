package dev.gkissel.forgeweave.ponder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.block.CastingBlockEntity;
import dev.gkissel.forgeweave.block.FaucetBlockEntity;
import dev.gkissel.forgeweave.block.SearedReservoirControllerBlock;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The seared reservoir scene (issue #891), on the controller's item: the closed box
 * {@code SearedReservoirScan} accepts -- a floor, walls two courses tall around a 1x1 interior with
 * glass and a drain in them, a ceiling with a bottom-half slab over the interior, the controller, and
 * no tank at all -- revealed in build order, then its capacity, filling through the drain, and a
 * faucet on that drain pouring into a casting table. Positions mirror
 * {@code assets/forgeweave/ponder/seared_reservoir.nbt} ({@code scripts/generate_ponder_schematics.py});
 * {@code PonderSchematicGameTests} rebuilds the layout server-side and asserts the real scan forms it.
 * Every rule and number in the text is {@code SearedReservoirScan}'s, {@code SearedReservoirBlockEntity}'s
 * or {@code SearedDrainBlock}'s own, cited there.
 *
 * <p>The pour is staged through block-entity NBT the way {@link ForgeweaveCastingScenes} stages its
 * own, with that class's helpers.
 *
 * <p>The inline English strings are Ponder's localization idiom (see
 * {@link ForgeweaveSmelteryScenes}): each registers a {@code forgeweave.ponder.seared_reservoir.*}
 * lang key that {@code ForgeweaveLanguageProvider} extracts through {@code PonderIndex.getLangAccess()}.
 */
public final class ForgeweaveSearedReservoirScenes {

    /** Mid-north wall, bottom course, facing the default camera. */
    private static final BlockPos CONTROLLER = new BlockPos(2, 2, 1);
    /** Mid-west wall, bottom course. */
    private static final BlockPos DRAIN = new BlockPos(1, 2, 2);
    /** On the drain, outside the wall, its spout over the table. */
    private static final BlockPos FAUCET = new BlockPos(0, 2, 2);
    private static final BlockPos TABLE = new BlockPos(0, 1, 2);

    private static final String MOLTEN_IRON = "forgeweave:molten_iron";
    private static final int INGOT = FaucetBlockEntity.TRANSACTION_AMOUNT;

    public static void assembly(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("seared_reservoir", "Assembling a Seared Reservoir");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 1, 3), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("A Seared Reservoir is a closed box of seared blocks that only stores fluid. Its floor takes seared blocks, seared glass or drains, but never a tank")
                .pointAt(util.vector().topOf(2, 1, 2))
                .placeNearTarget();
        scene.idle(100);

        Selection walls = util.select().fromTo(1, 2, 1, 3, 3, 3)
                .substract(util.select().position(CONTROLLER))
                .substract(util.select().position(DRAIN));
        scene.world().showSection(walls, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("The walls take any smeltery wall block: seared blocks, glass, tanks and drains all work, corners included. No tank is required; the box itself is the tank")
                .pointAt(util.vector().blockSurface(new BlockPos(1, 3, 2), Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(util.select().position(DRAIN), Direction.EAST);
        scene.idle(10);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Put a Seared Drain in a wall: it is how fluid gets in and out")
                .pointAt(util.vector().blockSurface(DRAIN, Direction.WEST))
                .placeNearTarget();
        scene.idle(80);

        scene.world().showSection(util.select().layer(4), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Close the top with a ceiling: any wall block, or bottom-half seared slabs and stairs, over the interior")
                .pointAt(util.vector().topOf(2, 4, 2))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(util.select().position(CONTROLLER), Direction.SOUTH);
        scene.overlay().showControls(util.vector().blockSurface(CONTROLLER, Direction.NORTH), Pointing.LEFT, 40)
                .withItem(new ItemStack(ForgeweaveItems.SEARED_RESERVOIR_CONTROLLER.get()));
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Finish with a Seared Reservoir Controller in a wall, facing outward. It lights up once the box is sealed")
                .pointAt(util.vector().blockSurface(CONTROLLER, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);

        scene.world().modifyBlock(CONTROLLER, state -> state.setValue(SearedReservoirControllerBlock.ACTIVE, true), false);
        scene.effects().indicateSuccess(CONTROLLER);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("The whole shell counts towards capacity, four buckets a block: this one, a single block wide and two tall, holds 144 buckets")
                .pointAt(util.vector().blockSurface(CONTROLLER, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showControls(util.vector().blockSurface(DRAIN, Direction.WEST), Pointing.LEFT, 40)
                .withItem(new ItemStack(ForgeweaveFluids.IRON.bucket().get()))
                .rightClick();
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Fill it by right-clicking the drain with a filled bucket, or by piping fluid into the drain. Fluids layer inside, and the controller's screen picks which one comes out first")
                .pointAt(util.vector().blockSurface(DRAIN, Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(util.select().position(FAUCET).add(util.select().position(TABLE)), Direction.DOWN);
        scene.world().modifyBlockEntityNBT(util.select().position(TABLE), CastingBlockEntity.class,
                tag -> tag.put("input", ForgeweaveCastingScenes.item("forgeweave:cast_ingot")));
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(FAUCET, Direction.NORTH), Pointing.LEFT, 30).rightClick();
        scene.idle(20);
        ForgeweaveCastingScenes.faucet(scene, util, FAUCET, MOLTEN_IRON, INGOT / 4);
        scene.idle(20);
        ForgeweaveCastingScenes.castingTank(scene, util, TABLE, MOLTEN_IRON, INGOT);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("A Faucet on the drain pours it back out into a casting table or basin below, just as a smeltery's drain does. A reservoir is where spare melt waits between pours")
                .pointAt(util.vector().topOf(TABLE))
                .placeNearTarget();
        scene.idle(60);
        ForgeweaveCastingScenes.faucet(scene, util, FAUCET, MOLTEN_IRON, 0);
        scene.idle(40);

        scene.markAsFinished();
    }

    private ForgeweaveSearedReservoirScenes() {}
}
