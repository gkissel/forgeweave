package dev.gkissel.forgeweave.block;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Clear glass and the 16 clear stained glass colors (issue #951), ported from Mantle 1.12's {@code
 * BlockConnectedTexture} and upstream's {@code BlockClearStainedGlass} (NOTICE.md). Six boolean
 * properties say which sides touch another block of the same kind, and the block state picks a model
 * from those the way upstream's {@code blockstates/clear_stained_glass.json} does: eleven sprites,
 * nine models, one variant per combination.
 *
 * <p>Upstream answers the same question in {@code getActualState}, a render-time hook 1.21 no longer
 * has. Here the flags live in the block state itself and are set by {@link #getStateForPlacement}
 * and {@link #updateShape}, the route vanilla's own fences, panes and walls take. No block entity
 * either way.
 *
 * <p>One consequence of storing rather than computing: glass placed before this change loads with
 * every flag false and keeps the isolated frame until one of its neighbours changes. Breaking and
 * replacing a single pane re-forms the whole run around it.
 */
public class ConnectedGlassBlock extends Block {

    public static final MapCodec<ConnectedGlassBlock> CODEC = simpleCodec(ConnectedGlassBlock::new);

    public static final BooleanProperty CONNECTED_DOWN = BooleanProperty.create("connected_down");
    public static final BooleanProperty CONNECTED_UP = BooleanProperty.create("connected_up");
    public static final BooleanProperty CONNECTED_NORTH = BooleanProperty.create("connected_north");
    public static final BooleanProperty CONNECTED_SOUTH = BooleanProperty.create("connected_south");
    public static final BooleanProperty CONNECTED_WEST = BooleanProperty.create("connected_west");
    public static final BooleanProperty CONNECTED_EAST = BooleanProperty.create("connected_east");

    /** The property each side answers with, so placement, neighbour updates and datagen share one map. */
    public static final Map<Direction, BooleanProperty> SIDES = new EnumMap<>(Map.of(
            Direction.DOWN, CONNECTED_DOWN,
            Direction.UP, CONNECTED_UP,
            Direction.NORTH, CONNECTED_NORTH,
            Direction.SOUTH, CONNECTED_SOUTH,
            Direction.WEST, CONNECTED_WEST,
            Direction.EAST, CONNECTED_EAST));

    public ConnectedGlassBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty side : SIDES.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends ConnectedGlassBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTED_DOWN, CONNECTED_UP, CONNECTED_NORTH, CONNECTED_SOUTH, CONNECTED_WEST, CONNECTED_EAST);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            state = state.setValue(side.getValue(), canConnect(level.getBlockState(pos.relative(side.getKey()))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
            LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        return state.setValue(SIDES.get(facing), canConnect(facingState));
    }

    /** Upstream's {@code shouldSideBeRendered}: a shared face between two connected panes is not drawn. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return canConnect(adjacent) || super.skipRendering(state, adjacent, side);
    }

    /** Upstream's {@code canConnect}: same block, nothing else. Clear glass never joins a stained pane. */
    private boolean canConnect(BlockState other) {
        return other.is(this);
    }
}
