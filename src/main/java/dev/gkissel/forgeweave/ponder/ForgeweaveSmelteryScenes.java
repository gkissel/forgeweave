package dev.gkissel.forgeweave.ponder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.block.FaucetBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The smeltery scenes on the cores' items: the multiblock assembly (issue #664) -- the
 * minimum structure {@code SmelteryScan} accepts (a seared floor, walls two blocks tall around a
 * 1x1 interior, one seared tank, the standard core), revealed in build order -- the size
 * variants (issue #700), and the four core tiers with #845's pour-to-transform ladder (issue #891).
 * The block positions below mirror the schematics
 * {@code assets/forgeweave/ponder/smeltery.nbt}, {@code smeltery_sizes.nbt} and {@code core_tiers.nbt} -- regenerate them
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

    // -- the core tiers (#891, #845's ladder), on core_tiers.nbt: the assembly scene's 1x1x2 smeltery
    // with the core in the *top* wall course so the faucet that pours onto it can stand above it, fed
    // by a seared tank beside it on the wall ring (SmelteryCoreTransformGameTests' rig, turned to
    // face the camera). Every number below is pinned elsewhere: the multipliers by SmelteryCore, the
    // 1000 mB / 2000 mB costs by data/forgeweave/forgeweave/core_transform_recipe/*.json, and the
    // "one and a half ingots' worth from one iron ore" by SmelteryCoreTransformGameTests.

    private static final BlockPos TIERS_CORE = new BlockPos(2, 3, 1);
    private static final BlockPos TIERS_TANK = new BlockPos(1, 2, 2);
    private static final BlockPos TIERS_FAUCET = new BlockPos(2, 4, 1);
    private static final BlockPos TIERS_SOURCE = new BlockPos(3, 4, 1);

    private static final String LAVA = "minecraft:lava";
    private static final String DRAGON_BREATH = "forgeweave:molten_dragon_breath";
    private static final String DEEP_BLOOD = "forgeweave:deep_blood";
    /** The stream's mid-pour buffer, the same fraction of a faucet transaction the casting scene draws. */
    private static final int STREAM = FaucetBlockEntity.TRANSACTION_AMOUNT / 4;

    public static void cores(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("core_tiers", "Smeltery Cores");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 3, 3), Direction.DOWN);
        scene.world().modifyBlock(TIERS_CORE, state -> state.setValue(SmelteryControllerBlock.ACTIVE, true), false);
        ForgeweaveCastingScenes.searedTank(scene, util, TIERS_TANK, LAVA, SearedTankBlockEntity.CAPACITY);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(TIERS_CORE, Direction.NORTH), Pointing.LEFT, 40)
                .withItem(new ItemStack(Items.IRON_ORE));
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("The core in a smeltery's wall sets how much melt each ore gives. Under a Standard Core one iron ore melts to one and a half ingots' worth")
                .pointAt(util.vector().blockSurface(TIERS_CORE, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        swapCore(scene, ForgeweaveBlocks.NETHER_CORE.get());
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("A Nether Core, seared bricks around a netherite ingot, raises that to two ingots' worth. It is the last core that can be crafted")
                .pointAt(util.vector().blockSurface(TIERS_CORE, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(util.select().position(TIERS_FAUCET).add(util.select().position(TIERS_SOURCE)), Direction.DOWN);
        ForgeweaveCastingScenes.searedTank(scene, util, TIERS_SOURCE, DRAGON_BREATH, 1000);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("An End Core is made by pouring instead. Set a Faucet over a Nether Core, fed from a tank of molten dragon breath, and pour a full bucket onto it")
                .pointAt(util.vector().blockSurface(TIERS_FAUCET, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        pourOntoCore(scene, util, DRAGON_BREATH, ForgeweaveBlocks.END_CORE.get());
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Once 1,000 mB has gone in the core becomes an End Core: two and a half ingots' worth per ore. The smeltery stays formed and keeps its melt and fuel")
                .pointAt(util.vector().blockSurface(TIERS_CORE, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        ForgeweaveCastingScenes.searedTank(scene, util, TIERS_SOURCE, DEEP_BLOOD, 2000);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Two buckets of deep blood poured the same way over an End Core make a Deep Core, the top tier: three ingots' worth from every ore")
                .pointAt(util.vector().blockSurface(TIERS_FAUCET, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        pourOntoCore(scene, util, DEEP_BLOOD, ForgeweaveBlocks.DEEP_CORE.get());
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Each step is one way. The wrong fluid, or the right one over any other core, is simply refused: the faucet does not pour and nothing is used up")
                .pointAt(util.vector().blockSurface(TIERS_CORE, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        scene.markAsFinished();
    }

    /** Right-click the faucet, run the stream for a moment, empty the source, then the core is the next tier. */
    private static void pourOntoCore(SceneBuilder scene, SceneBuildingUtil util, String fluidId, Block toCore) {
        scene.overlay().showControls(util.vector().blockSurface(TIERS_FAUCET, Direction.NORTH), Pointing.LEFT, 30).rightClick();
        scene.idle(20);
        ForgeweaveCastingScenes.faucet(scene, util, TIERS_FAUCET, fluidId, STREAM);
        scene.idle(60);
        ForgeweaveCastingScenes.faucet(scene, util, TIERS_FAUCET, fluidId, 0);
        ForgeweaveCastingScenes.searedTank(scene, util, TIERS_SOURCE, fluidId, 0);
        swapCore(scene, toCore);
    }

    /** The same wall cell, the next tier's block, facing and lit as before ({@code Block.withPropertiesOf}). */
    private static void swapCore(SceneBuilder scene, Block toCore) {
        scene.world().modifyBlock(TIERS_CORE, state -> toCore.withPropertiesOf(state), true);
        scene.effects().indicateSuccess(TIERS_CORE);
        scene.idle(10);
    }

    private ForgeweaveSmelteryScenes() {}
}
