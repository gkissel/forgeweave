package dev.gkissel.forgeweave.ponder;

import java.util.function.Consumer;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.block.CastingBlockEntity;
import dev.gkissel.forgeweave.block.FaucetBlockEntity;
import dev.gkissel.forgeweave.block.SearedChannelBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The channel-and-faucet scene (issue #700), on the faucet's and the seared channel's items: a
 * formed 1x1x3 smeltery with a Seared Drain in its north wall, a Faucet on the drain, and a channel
 * fork under the faucet running west to a Casting Table and north to a Casting Basin
 * ({@code assets/forgeweave/ponder/casting.nbt}, {@code scripts/generate_ponder_schematics.py}).
 *
 * <p>The pour is staged through block-entity NBT rather than the real fluid flow: the faucet, the
 * channels and the casting blocks tick their transfers server-side only, and a Ponder world is a
 * client one, so the scene writes the same tags the server would have synced -- the faucet's
 * {@code drained} buffer, each channel's {@code fluid} and {@code is_flowing}, the table's and
 * basin's {@code tank} -- and the ordinary renderers draw the stream, the runs and the pools. The
 * key shapes are the ones {@code SaveCompatCorpusTest} pins ({@code m2_faucet_mid_pour},
 * {@code m441_seared_channel_flowing}, {@code m2_casting_table_mid_pour}).
 *
 * <p>The inline English strings are Ponder's localization idiom (see
 * {@link ForgeweaveSmelteryScenes}): each registers a {@code forgeweave.ponder.casting.*} lang key
 * that {@code ForgeweaveLanguageProvider} extracts through {@code PonderIndex.getLangAccess()}.
 */
public final class ForgeweaveCastingScenes {

    private static final BlockPos CORE = new BlockPos(4, 2, 5);
    private static final BlockPos DRAIN = new BlockPos(5, 3, 4);
    private static final BlockPos FAUCET = new BlockPos(5, 3, 3);
    private static final BlockPos FORK = new BlockPos(5, 2, 3);
    private static final BlockPos WEST_RUN = new BlockPos(4, 2, 3);
    private static final BlockPos TABLE_SPOUT = new BlockPos(3, 2, 3);
    private static final BlockPos BASIN_SPOUT = new BlockPos(5, 2, 2);
    private static final BlockPos TABLE = new BlockPos(3, 1, 3);
    private static final BlockPos BASIN = new BlockPos(5, 1, 2);

    private static final String MOLTEN_IRON = "forgeweave:molten_iron";
    /** One ingot's worth ({@code FaucetBlockEntity.TRANSACTION_AMOUNT}); a block's worth is nine. */
    private static final int INGOT = FaucetBlockEntity.TRANSACTION_AMOUNT;
    /** Upstream's two-tick flow window ({@code SearedChannelBlockEntity.FLOW_TICKS}); nothing ages it in a Ponder world, so it stays lit. */
    private static final byte FLOW_TICKS = 2;

    public static void pouring(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("casting", "Faucets and Channels");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        Selection smeltery = util.select().fromTo(4, 1, 4, 6, 4, 6);
        scene.world().showSection(smeltery, Direction.DOWN);
        scene.world().modifyBlock(CORE, state -> state.setValue(SmelteryControllerBlock.ACTIVE, true), false);
        scene.idle(10);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("A Seared Drain in a smeltery's wall is where the melt comes out")
                .pointAt(util.vector().blockSurface(DRAIN, Direction.NORTH))
                .placeNearTarget();
        scene.idle(80);

        scene.world().showSection(util.select().position(FAUCET), Direction.SOUTH);
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Place a Faucet on the drain. Right-click it and it pours one ingot's worth of the selected melt out of its spout")
                .pointAt(util.vector().blockSurface(FAUCET, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);

        Selection channels = util.select().fromTo(3, 2, 3, 5, 2, 3).add(util.select().position(BASIN_SPOUT));
        scene.world().showSection(channels, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Seared Channels carry it sideways. Placing one against another chains them; right-click an arm to change its direction, or the top to open the bottom")
                .pointAt(util.vector().topOf(WEST_RUN))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(util.select().position(TABLE).add(util.select().position(BASIN)), Direction.DOWN);
        scene.world().modifyBlockEntityNBT(util.select().position(TABLE), CastingBlockEntity.class,
                tag -> tag.put("input", item("forgeweave:cast_ingot")));
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("An open bottom pours into whatever waits below: a Casting Table holding a cast makes ingots and parts, a Casting Basin makes blocks")
                .pointAt(util.vector().topOf(TABLE))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showControls(util.vector().blockSurface(FAUCET, Direction.NORTH), Pointing.LEFT, 30).rightClick();
        scene.idle(20);
        faucet(scene, util, INGOT / 4);
        scene.idle(10);
        channel(scene, util, FORK, Direction.WEST, Direction.NORTH);
        scene.idle(8);
        channel(scene, util, WEST_RUN, Direction.WEST);
        channel(scene, util, BASIN_SPOUT, Direction.DOWN);
        scene.idle(8);
        channel(scene, util, TABLE_SPOUT, Direction.DOWN);
        scene.idle(8);
        tank(scene, util, TABLE, INGOT);
        tank(scene, util, BASIN, INGOT * 9);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("The melt runs down the channels and fills the table and the basin. Keep pouring until they are full, then let them cool")
                .pointAt(util.vector().topOf(TABLE))
                .placeNearTarget();
        scene.idle(60);
        faucet(scene, util, 0);
        for (BlockPos pos : new BlockPos[] {FORK, WEST_RUN, BASIN_SPOUT, TABLE_SPOUT}) {
            channel(scene, util, pos);
        }
        scene.idle(40);

        tank(scene, util, TABLE, 0);
        tank(scene, util, BASIN, 0);
        scene.world().modifyBlockEntityNBT(util.select().position(TABLE), CastingBlockEntity.class,
                tag -> tag.put("output", item("minecraft:iron_ingot")));
        scene.world().modifyBlockEntityNBT(util.select().position(BASIN), CastingBlockEntity.class,
                tag -> tag.put("output", item("minecraft:iron_block")));
        scene.effects().indicateSuccess(TABLE);
        scene.effects().indicateSuccess(BASIN);
        scene.overlay().showControls(util.vector().topOf(TABLE), Pointing.DOWN, 40)
                .withItem(new ItemStack(Items.IRON_INGOT));
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Once cooled, right-click to take the result out. The cast stays in the table for the next pour")
                .pointAt(util.vector().topOf(BASIN))
                .placeNearTarget();
        scene.idle(100);

        scene.markAsFinished();
    }

    /** The faucet's stream: {@code drained} is what is mid-pour, which is all the renderer reads. */
    private static void faucet(SceneBuilder scene, SceneBuildingUtil util, int amount) {
        scene.world().modifyBlockEntityNBT(util.select().position(FAUCET), FaucetBlockEntity.class, tag -> {
            tag.put("drained", fluid(amount));
            tag.putBoolean("pouring", amount > 0);
        });
    }

    /**
     * A channel with its buffer full and the given sides marked flowing (none: empty and still).
     * {@code is_flowing} is upstream's five-byte array, down first then north, south, west, east.
     */
    private static void channel(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Direction... flowing) {
        byte[] flags = new byte[5];
        for (Direction side : flowing) {
            flags[switch (side) {
                case DOWN -> 0;
                case NORTH -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> 4;
            }] = FLOW_TICKS;
        }
        Consumer<CompoundTag> edit = tag -> {
            tag.put("fluid", fluid(flowing.length == 0 ? 0 : SearedChannelBlockEntity.CAPACITY));
            tag.putByteArray("is_flowing", flags);
        };
        scene.world().modifyBlockEntityNBT(util.select().position(pos), SearedChannelBlockEntity.class, edit);
    }

    private static void tank(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, int amount) {
        scene.world().modifyBlockEntityNBT(util.select().position(pos), CastingBlockEntity.class, tag -> {
            CompoundTag tank = new CompoundTag();
            if (amount > 0) {
                tank.put("Fluid", fluid(amount));
            }
            tag.put("tank", tank);
        });
    }

    /** A {@code FluidStack} in its own codec's shape ({@code FluidStack.parseOptional}); zero is the empty stack. */
    private static CompoundTag fluid(int amount) {
        CompoundTag tag = new CompoundTag();
        if (amount > 0) {
            tag.putString("id", MOLTEN_IRON);
            tag.putInt("amount", amount);
        }
        return tag;
    }

    private static CompoundTag item(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("count", 1);
        return tag;
    }

    private ForgeweaveCastingScenes() {}
}
