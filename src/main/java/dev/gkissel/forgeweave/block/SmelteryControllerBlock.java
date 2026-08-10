package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
     * Rescans and reports the outcome in chat. The smeltery GUI is issue #101; until then this is
     * how SCOPE.md M2's "the controller reports why an invalid structure fails to form" is met, and
     * it stays useful afterwards as the "why won't it form" affordance.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SmelteryControllerBlockEntity core) {
            core.updateStructure();
            player.displayClientMessage(core.lastResult(), false);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * #96: one melt step, then re-arm only if there is still something to melt. This is the whole
     * "the smeltery ticks" story -- a scheduled block tick rather than a {@code BlockEntityTicker},
     * so a core with nothing in it is on no tick list at all (see {@link SmelteryControllerBlockEntity}).
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SmelteryControllerBlockEntity core && core.meltTick()) {
            level.scheduleTick(pos, this, SmelteryControllerBlockEntity.MELT_INTERVAL_TICKS);
        }
    }

    private static void updateStructure(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SmelteryControllerBlockEntity core) {
            core.updateStructure();
        }
    }
}
