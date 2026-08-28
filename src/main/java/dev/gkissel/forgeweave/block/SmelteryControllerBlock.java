package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import dev.gkissel.forgeweave.advancement.ForgeweaveCriteriaTriggers;
import dev.gkissel.forgeweave.ponder.ForgeweavePonderHint;

/**
 * The Standard Core and Nether Core (docs/SCOPE.md M2 issue #95), one class parameterized by {@link
 * SmelteryCore}. Ported from upstream 1.12's {@code BlockSmelteryController}/{@code
 * BlockMultiblockController} (NOTICE.md): a horizontally-facing block with an {@code active}
 * property driving the lit front texture, whose {@code FACING} points out of the structure so the
 * block behind it is the smeltery interior.
 *
 * <p><b>The core never ticks.</b> Upstream polls {@code checkMultiblockStructure} once a second from
 * {@code TileSmeltery.update} even when nothing is formed, which the SCOPE.md M2 performance budget
 * ("spark profile confirms idle smeltery ~= zero tick") rules out. Every scan here is instead driven
 * by an event: the core being placed ({@link #onPlace}), a block change next to it ({@link
 * #neighborChanged}), or a player using it ({@link #useWithoutItem}). Changes further away -- a wall
 * broken on the far side of a 9x9 -- are caught by {@link SmelteryControllerBlockEntity#structure()}
 * revalidating on read; see that method.
 */
public class SmelteryControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** Upstream's {@code BlockMultiblockController.ACTIVE}: whether the structure is currently formed. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final SmelteryCore core;
    private final MapCodec<SmelteryControllerBlock> codec;

    public SmelteryControllerBlock(Properties properties, SmelteryCore core) {
        super(properties);
        this.core = core;
        this.codec = simpleCodec(p -> new SmelteryControllerBlock(p, core));
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    public SmelteryCore core() {
        return core;
    }

    @Override
    protected MapCodec<? extends SmelteryControllerBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmelteryControllerBlockEntity(pos, state, core);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Skip our own ACTIVE flip, which re-enters here with the same block already in place.
        if (!oldState.is(this)) {
            updateStructure(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        updateStructure(level, pos);
    }

    /**
     * Rescans, then either opens the smeltery GUI or -- when nothing formed -- reports why in chat.
     *
     * <p>The chat report was the whole interaction until issue #101 added the screen, and it stays
     * as the unformed case's affordance: it is how SCOPE.md M2's "the controller reports why an
     * invalid structure fails to form" is met, and a GUI showing an empty tank would say nothing
     * about the hole in the wall.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SmelteryControllerBlockEntity core) {
            core.updateStructure();
            boolean formed = core.isFormed();
            // #110 -- first-interaction hook for the advancement chain and the Ponder soft dependency
            // (docs/SCOPE.md M2 issue #110): grants "build smeltery" the first time a player's own
            // interaction finds the structure formed, and shows the one-time Ponder chat hint on every
            // first interaction regardless of outcome (ForgeweavePonderHint is itself a no-op after
            // the first call, or whenever Ponder is installed).
            if (player instanceof ServerPlayer serverPlayer) {
                if (formed) {
                    ForgeweaveCriteriaTriggers.SMELTERY_FORMED.get().trigger(serverPlayer);
                }
                ForgeweavePonderHint.maybeShow(serverPlayer);
            }
            // #101: a formed smeltery opens its GUI; an unformed one keeps #95's chat report, which
            // is the only thing that can explain the hole in the wall. Both #110 hooks above run
            // either way, so the advancement and the hint are unaffected by which branch is taken.
            if (formed) {
                core.open(player);
            } else {
                player.displayClientMessage(core.lastResult(), false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * The whole "the smeltery ticks" story -- a scheduled block tick rather than a
     * {@code BlockEntityTicker}, so an unformed core with nothing in it is on no tick list at all
     * (see {@link SmelteryControllerBlockEntity}). One melt step and one item-pickup sweep attempt
     * (#290) run on every firing, whichever cadence woke it -- {@link
     * SmelteryControllerBlockEntity#sweepInterior()} throttles itself against real elapsed
     * time, so a firing that is too soon for a sweep is simply a no-op there. Reschedules at the
     * tighter melt cadence while there is melt work, or the item-pickup cadence while merely formed;
     * an unformed core with nothing melting reschedules nothing and falls silent.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof SmelteryControllerBlockEntity core)) {
            return;
        }
        if (!core.isFormed()) {
            // #757: this firing only exists to serve armSettleWindow's bounded recheck -- isFormed()
            // above already forced the rescan (it is always at least RESCAN_INTERVAL_TICKS stale by
            // the time this runs), so all that is left is deciding whether the window is still open.
            if (core.settling()) {
                level.scheduleTick(pos, this, SmelteryControllerBlockEntity.RESCAN_INTERVAL_TICKS);
            }
            return;
        }
        boolean melting = core.meltTick();
        core.sweepInterior();
        if (melting) {
            level.scheduleTick(pos, this, SmelteryControllerBlockEntity.MELT_INTERVAL_TICKS);
        } else {
            level.scheduleTick(pos, this, SmelteryControllerBlockEntity.ITEM_PICKUP_INTERVAL_TICKS);
        }
    }

    private static void updateStructure(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SmelteryControllerBlockEntity core) {
            core.updateStructure();
            core.armSettleWindow();
        }
    }

    /**
     * T73/issue #504: upstream {@code BlockSmelteryController#randomDisplayTick} -- a formed core
     * puffs flame and smoke out of its front face every client tick, same offsets as upstream's.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5 + random.nextFloat() * 6f / 16f;
        double z = pos.getZ() + 0.5;
        spawnFireParticles(level, state.getValue(FACING), x, y, z, 0.52, random.nextDouble() * 0.6 - 0.3);
    }

    /**
     * Upstream {@code BlockMultiblockController#spawnFireParticles}: a SMOKE_NORMAL/FLAME pair just
     * outside whichever face the controller's {@code facing} points to, {@code front} out from the
     * block centre and {@code side} across it. Shared by {@link SearedFurnaceControllerBlock}, the
     * other subclass of upstream's abstract controller.
     */
    static void spawnFireParticles(Level level, Direction facing, double x, double y, double z, double front, double side) {
        double px = x + offsetAlong(facing, front, side);
        double pz = z + offsetAcross(facing, front, side);
        level.addParticle(ParticleTypes.SMOKE, px, y, pz, 0.0, 0.0, 0.0);
        level.addParticle(ParticleTypes.FLAME, px, y, pz, 0.0, 0.0, 0.0);
    }

    /** The X offset of upstream's per-facing switch: {@code front} for WEST/EAST, {@code side} for NORTH/SOUTH. */
    static double offsetAlong(Direction facing, double front, double side) {
        return switch (facing) {
            case WEST -> -front;
            case EAST -> front;
            default -> side;
        };
    }

    /** The Z offset of upstream's per-facing switch: {@code side} for WEST/EAST, {@code front} for NORTH/SOUTH. */
    static double offsetAcross(Direction facing, double front, double side) {
        return switch (facing) {
            case NORTH -> -front;
            case SOUTH -> front;
            default -> side;
        };
    }
}
