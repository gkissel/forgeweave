package dev.gkissel.forgeweave.block;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * The seared channel (issue #441, parity audit T9), ported from upstream 1.12's {@code BlockChannel}
 * (NOTICE.md). A channel is the smeltery's gravity plumbing: a faucet pours into its top, and it
 * carries that fluid sideways to other channels or to any fluid handler, and downwards into whatever
 * is below it.
 *
 * <p>Each of the four horizontal sides carries its own {@link ChannelConnection} -- {@code none},
 * {@code in} (fluid enters here) or {@code out} (fluid leaves here) -- and the bottom carries a
 * plain {@link #DOWN} flag. Right-clicking a side cycles it; sneaking cycles the other way.
 *
 * <p><b>Where the connections live.</b> Upstream 1.12 keeps them in the block entity and rebuilds
 * the render state in {@code getActualState}, which modern Minecraft removed. They are real
 * blockstate properties here, which is how upstream itself carried the same mechanic forward (the
 * 1.20 clone's {@code ChannelBlock}, NOTICE.md); the flow semantics below stay 1.12's.
 *
 * <p><b>Redstone (upstream's own gate).</b> A powered channel opens its downward output and an
 * unpowered one closes it, so a lever under a channel run is the on/off switch for pouring into a
 * casting basin. {@link #POWERED} exists only to spot the edge -- upstream 1.12 remembers the same
 * bit in its block entity as {@code wasPowered}.
 */
public class SearedChannelBlock extends Block implements EntityBlock {
    public static final MapCodec<SearedChannelBlock> CODEC = simpleCodec(SearedChannelBlock::new);

    /** Upstream's {@code DOWN}: whether the channel pours out of its bottom. */
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    /** Upstream 1.12's {@code wasPowered}, as a state bit: what the redstone gate compares against. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final EnumProperty<ChannelConnection> NORTH = EnumProperty.create("north", ChannelConnection.class);
    public static final EnumProperty<ChannelConnection> SOUTH = EnumProperty.create("south", ChannelConnection.class);
    public static final EnumProperty<ChannelConnection> WEST = EnumProperty.create("west", ChannelConnection.class);
    public static final EnumProperty<ChannelConnection> EAST = EnumProperty.create("east", ChannelConnection.class);

    /** The side property for each horizontal direction. */
    public static final Map<Direction, EnumProperty<ChannelConnection>> SIDES = new EnumMap<>(Map.of(
            Direction.NORTH, NORTH,
            Direction.SOUTH, SOUTH,
            Direction.WEST, WEST,
            Direction.EAST, EAST));

    /**
     * Upstream's {@code getFlowDepth}: how far below its own origin a channel's fluid sits, which is
     * where a faucet pouring into it has to stop drawing its stream.
     */
    public static final float FLOW_DEPTH = 0.53125f;

    /** Upstream's hit-to-side split: the centre piece spans 5/16 to 11/16 on both horizontal axes. */
    private static final double ARM_MIN = 5 / 16d;
    private static final double ARM_MAX = 11 / 16d;

    /**
     * Upstream 1.12's collision boxes: a 6x4x6 centre (dropping to y=2 when the bottom is open) plus
     * one arm per flowing side. Indexed by {@link #shapeKey} so the lookup is O(1), the way upstream
     * indexes its own bounds table.
     */
    private static final VoxelShape[] SHAPES = new VoxelShape[32];

    static {
        VoxelShape centreClosed = box(5, 4, 5, 11, 8, 11);
        VoxelShape centreOpen = box(5, 2, 5, 11, 8, 11);
        VoxelShape north = box(5, 4, 0, 11, 8, 5);
        VoxelShape south = box(5, 4, 11, 11, 8, 16);
        VoxelShape west = box(0, 4, 5, 5, 8, 11);
        VoxelShape east = box(11, 4, 5, 16, 8, 11);
        for (int key = 0; key < SHAPES.length; key++) {
            VoxelShape shape = (key & 1) != 0 ? centreOpen : centreClosed;
            if ((key & 2) != 0) {
                shape = Shapes.or(shape, north);
            }
            if ((key & 4) != 0) {
                shape = Shapes.or(shape, south);
            }
            if ((key & 8) != 0) {
                shape = Shapes.or(shape, west);
            }
            if ((key & 16) != 0) {
                shape = Shapes.or(shape, east);
            }
            SHAPES[key] = shape;
        }
    }

    public SearedChannelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(DOWN, false)
                .setValue(POWERED, false)
                .setValue(NORTH, ChannelConnection.NONE)
                .setValue(SOUTH, ChannelConnection.NONE)
                .setValue(WEST, ChannelConnection.NONE)
                .setValue(EAST, ChannelConnection.NONE));
    }

    @Override
    protected MapCodec<? extends SearedChannelBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DOWN, POWERED, NORTH, SOUTH, WEST, EAST);
    }

    private static int shapeKey(BlockState state) {
        return (state.getValue(DOWN) ? 1 : 0)
                | (state.getValue(NORTH).canFlow() ? 2 : 0)
                | (state.getValue(SOUTH).canFlow() ? 4 : 0)
                | (state.getValue(WEST).canFlow() ? 8 : 0)
                | (state.getValue(EAST).canFlow() ? 16 : 0);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[shapeKey(state)];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SearedChannelBlockEntity(pos, state);
    }

    // ------------------------------------------------------------------ connecting

    /** Whether something on {@code side} of {@code pos} can take or give fluid. */
    static boolean canConnect(Level level, BlockPos pos, Direction side) {
        BlockPos facing = pos.relative(side);
        return level.getBlockState(facing).getBlock() instanceof SearedChannelBlock
                || level.getCapability(Capabilities.FluidHandler.BLOCK, facing, side.getOpposite()) != null;
    }

    /**
     * Upstream 1.12's {@code onPlaceBlock}: a channel placed against another channel <em>receives</em>
     * from it -- the new one's facing side is {@code in} and (through {@link #updateShape}) the old
     * one's becomes {@code out}, so a run built by walking away from the smeltery flows the way it
     * was built. Sneaking reverses that. Placed against anything else that holds fluid the new
     * channel outputs into it, and placed on top of something it connects downwards.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState().setValue(POWERED, level.hasNeighborSignal(pos));
        Direction clicked = context.getClickedFace();
        if (clicked == Direction.DOWN) {
            return state;
        }
        if (clicked == Direction.UP) {
            return state.setValue(DOWN, canConnect(level, pos, Direction.DOWN));
        }

        Direction toward = clicked.getOpposite();
        BlockPos placedOn = pos.relative(toward);
        ChannelConnection connection = ChannelConnection.NONE;
        if (level.getBlockState(placedOn).getBlock() instanceof SearedChannelBlock) {
            Player player = context.getPlayer();
            connection = player != null && player.isShiftKeyDown() ? ChannelConnection.OUT : ChannelConnection.IN;
        } else if (level.getCapability(Capabilities.FluidHandler.BLOCK, placedOn, clicked) != null) {
            connection = ChannelConnection.OUT;
        }
        return state.setValue(SIDES.get(toward), connection);
    }

    /**
     * Two channels always agree: whatever one side says, the other mirrors as its opposite. A
     * connection facing air is dropped, which is how breaking a casting table closes the channel
     * that fed it.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
            LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        if (facing == Direction.DOWN) {
            return state.getValue(DOWN) && facingState.isAir() ? state.setValue(DOWN, false) : state;
        }
        if (facing == Direction.UP) {
            return state;
        }
        EnumProperty<ChannelConnection> property = SIDES.get(facing);
        if (facingState.getBlock() instanceof SearedChannelBlock) {
            return state.setValue(property, facingState.getValue(SIDES.get(facing.getOpposite())).getOpposite());
        }
        if (state.getValue(property) != ChannelConnection.NONE && facingState.isAir()) {
            return state.setValue(property, ChannelConnection.NONE);
        }
        return state;
    }

    // ------------------------------------------------------------------ interaction

    /**
     * Holding a channel and clicking a channel places the new one rather than toggling the old one,
     * upstream's own carve-out -- otherwise a run could never be extended sideways.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() == asItem() && level.getBlockState(pos.relative(hit.getDirection())).canBeReplaced()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Upstream's {@code onBlockActivated}: cycle the connection on whichever arm was clicked. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        Direction side = sideClicked(pos, player, hit);
        BlockState updated = cycle(state, level, pos, player, side);
        if (updated == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, updated);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Upstream maps the hit position onto an arm, so clicking the north arm toggles north no matter
     * which face of it was hit; a click on the centre (or the top) means the bottom, and sneaking on
     * a face means the arm opposite it.
     */
    private static Direction sideClicked(BlockPos pos, Player player, BlockHitResult hit) {
        Direction face = hit.getDirection();
        Direction side = face == Direction.UP ? Direction.DOWN : face;
        if (player.isShiftKeyDown() && side != Direction.DOWN) {
            side = side.getOpposite();
        }
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        if (local.z() < ARM_MIN) {
            return Direction.NORTH;
        }
        if (local.z() > ARM_MAX) {
            return Direction.SOUTH;
        }
        if (local.x() < ARM_MIN) {
            return Direction.WEST;
        }
        if (local.x() > ARM_MAX) {
            return Direction.EAST;
        }
        return side;
    }

    /**
     * One step of upstream's connection cycle, plus its own guard that a side may only be set to
     * {@code out} when there is something on it to output into.
     *
     * @return the new state, or {@code null} when the click changes nothing
     */
    @Nullable
    private static BlockState cycle(BlockState state, Level level, BlockPos pos, Player player, Direction side) {
        if (side == Direction.DOWN) {
            if (!state.getValue(DOWN) && canConnect(level, pos, Direction.DOWN)) {
                player.displayClientMessage(Component.translatable("message.forgeweave.channel.down.out"), true);
                return state.setValue(DOWN, true);
            }
            if (state.getValue(DOWN)) {
                player.displayClientMessage(Component.translatable("message.forgeweave.channel.down.none"), true);
                return state.setValue(DOWN, false);
            }
            return null;
        }

        EnumProperty<ChannelConnection> property = SIDES.get(side);
        boolean reverse = player.isShiftKeyDown();
        ChannelConnection next = state.getValue(property).getNext(reverse);
        if (next == ChannelConnection.OUT && !canConnect(level, pos, side)) {
            next = next.getNext(reverse);
        }
        player.displayClientMessage(
                Component.translatable("message.forgeweave.channel.side." + next.getSerializedName()), true);
        return state.setValue(property, next);
    }

    // ------------------------------------------------------------------ redstone and ticking

    /**
     * Upstream's redstone gate: power opens the downward output, losing power closes it. Nothing
     * else about a channel is redstone-controlled.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered)
                    .setValue(DOWN, powered && canConnect(level, pos, Direction.DOWN)), Block.UPDATE_ALL);
        }
    }

    /** Dust routed at a channel connects to it, so the gate above can be wired from any side. */
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    /**
     * The channel's only tick source. Unlike upstream, which ticks every channel in the world
     * forever, an idle channel here costs nothing (docs/SCOPE.md M2 performance budget): a fill books
     * a scheduled block tick, and {@link SearedChannelBlockEntity#flowStep()} re-books it for as long
     * as there is fluid to move or a flow flag still counting down.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SearedChannelBlockEntity channel) {
            channel.flowStep();
        }
    }

    /** Two connected channels hide the faces they share, upstream's own culling rule. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return side.getAxis().isHorizontal()
                && adjacent.getBlock() instanceof SearedChannelBlock
                && state.getValue(SIDES.get(side)).canFlow()
                && adjacent.getValue(SIDES.get(side.getOpposite())).canFlow();
    }

    /** Upstream's {@code ChannelConnection}: what one horizontal side of a channel does. */
    public enum ChannelConnection implements StringRepresentable {
        NONE,
        IN,
        OUT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Whether fluid moves across this side at all -- {@code in} and {@code out} both do. */
        public boolean canFlow() {
            return this != NONE;
        }

        /** The matching connection on the channel across this side. */
        public ChannelConnection getOpposite() {
            return switch (this) {
                case IN -> OUT;
                case OUT -> IN;
                case NONE -> NONE;
            };
        }

        /** Upstream's click cycle: none -> out -> in -> none, or the reverse when sneaking. */
        public ChannelConnection getNext(boolean reverse) {
            if (reverse) {
                return switch (this) {
                    case NONE -> IN;
                    case IN -> OUT;
                    case OUT -> NONE;
                };
            }
            return switch (this) {
                case NONE -> OUT;
                case OUT -> IN;
                case IN -> NONE;
            };
        }
    }
}
