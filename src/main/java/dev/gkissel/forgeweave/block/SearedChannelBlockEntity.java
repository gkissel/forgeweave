package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import dev.gkissel.forgeweave.block.SearedChannelBlock.ChannelConnection;

/**
 * A channel's buffer and the flow through it (issue #441, parity audit T9), ported from upstream
 * 1.12's {@code TileChannel}, {@code ChannelTank} and {@code ChannelSideTank} (NOTICE.md).
 *
 * <p>Upstream's flow, kept exactly:
 *
 * <ul>
 *   <li>The buffer holds {@value #CAPACITY} mB. Anything filled into it this tick is
 *       <em>locked</em> until the next one, so a channel that is being filled and drained in the
 *       same tick never reads as momentarily empty -- which is what would make the fluid in it
 *       flicker.
 *   <li>A downward output is tried first and takes the whole {@value FaucetBlockEntity#LIQUID_TRANSFER}
 *       mB per tick; only when nothing went down do the sides get a turn, splitting the same budget
 *       between however many of them are outputs.
 *   <li>A side that moved fluid is marked flowing for two ticks, which is what the renderer draws
 *       and what makes a stream look continuous rather than strobing.
 * </ul>
 *
 * <p><b>No block-entity ticker.</b> Like the faucet and the casting blocks, an idle channel must
 * cost nothing (docs/SCOPE.md M2 performance budget), so the flow runs on a vanilla scheduled block
 * tick that re-books itself while there is fluid to move or a flow flag still counting down;
 * upstream instead ticks every channel in the world forever.
 */
public class SearedChannelBlockEntity extends BlockEntity {
    /** Upstream 1.12's {@code new ChannelTank(36, this)}: six ticks of transfer buffered. */
    public static final int CAPACITY = 36;

    private static final String TAG_FLUID = "fluid";
    private static final String TAG_IS_FLOWING = "is_flowing";

    /** Upstream's own two-tick flow window: index 0 is down, 1-4 are the horizontals. */
    private static final int FLOW_TICKS = 2;

    private final byte[] isFlowing = new byte[5];
    private final ChannelTank tank = new ChannelTank();

    public SearedChannelBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.SEARED_CHANNEL.get(), pos, state);
    }

    /** What the channel is carrying, for the renderer and for tests. */
    public FluidStack fluid() {
        return tank.fluid;
    }

    /** Whether the renderer should draw a moving stream on this side. */
    public boolean isFlowing(Direction side) {
        return side != Direction.UP && isFlowing[flowIndex(side)] > 0;
    }

    private static int flowIndex(Direction side) {
        return side.getAxis().isVertical() ? 0 : side.get3DDataValue() - 1;
    }

    // ------------------------------------------------------------------ flow

    /**
     * One tick of upstream's {@code update}: move what can be moved, age the flow flags, and unlock
     * whatever was filled in last tick. Public, like {@link SmelteryControllerBlockEntity#meltTick()},
     * so a GameTest can drive the flow on its own clock instead of the scheduler's (#715).
     */
    public void flowStep() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!tank.fluid.isEmpty()) {
            boolean flowed = state.getValue(SearedChannelBlock.DOWN)
                    && trySide(Direction.DOWN, FaucetBlockEntity.LIQUID_TRANSFER);
            int outputs = countOutputs(state);
            if (!flowed && outputs > 0) {
                int rate = Mth.clamp(tank.usable() / outputs, 1, FaucetBlockEntity.LIQUID_TRANSFER);
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    trySide(side, rate);
                }
            }
        }

        for (int i = 0; i < isFlowing.length; i++) {
            if (isFlowing[i] > 0 && --isFlowing[i] == 0) {
                sync();
            }
        }
        tank.locked = 0;
        scheduleIfBusy();
    }

    private static int countOutputs(BlockState state) {
        int outputs = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (state.getValue(SearedChannelBlock.SIDES.get(side)) == ChannelConnection.OUT) {
                outputs++;
            }
        }
        return outputs;
    }

    private boolean isOutput(Direction side) {
        BlockState state = getBlockState();
        return switch (side) {
            case UP -> false;
            case DOWN -> state.getValue(SearedChannelBlock.DOWN);
            default -> state.getValue(SearedChannelBlock.SIDES.get(side)) == ChannelConnection.OUT;
        };
    }

    /** Upstream's {@code trySide}: push up to {@code rate} mB into whatever is on that side. */
    private boolean trySide(Direction side, int rate) {
        if (tank.fluid.isEmpty() || !isOutput(side) || level == null) {
            return false;
        }
        IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK,
                worldPosition.relative(side), side.getOpposite());
        if (target == null) {
            setFlow(side, false);
            return false;
        }

        int usable = Math.min(tank.usable(), rate);
        if (usable > 0) {
            int filled = target.fill(tank.fluid.copyWithAmount(usable), IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0) {
                tank.drain(filled);
                setFlow(side, true);
                return true;
            }
        }
        setFlow(side, false);
        return false;
    }

    /** Marks a side as flowing for {@value #FLOW_TICKS} ticks, syncing only when the answer changes. */
    private void setFlow(Direction side, boolean flowing) {
        if (side == Direction.UP) {
            return;
        }
        int index = flowIndex(side);
        boolean was = isFlowing[index] > 0;
        isFlowing[index] = (byte) (flowing ? FLOW_TICKS : 0);
        if (was != flowing) {
            sync();
        }
    }

    /** Books the next flow tick while there is anything left to do. */
    private void scheduleIfBusy() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!tank.fluid.isEmpty()) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
            return;
        }
        for (byte flag : isFlowing) {
            if (flag > 0) {
                level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
                return;
            }
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ capabilities

    /**
     * Upstream's capability split: the top (and a sideless query) always accepts fluid, an {@code in}
     * side accepts fluid and lights its own stream up, an {@code out} side hands back a handler that
     * accepts nothing -- so the neighbour on that side still sees a fluid handler and stays
     * connected -- and everything else exposes nothing at all.
     */
    @Nullable
    private IFluidHandler handlerFor(@Nullable Direction side) {
        if (side == null || side == Direction.UP) {
            return tank.fillOnly(null);
        }
        if (side == Direction.DOWN) {
            return null;
        }
        return switch (getBlockState().getValue(SearedChannelBlock.SIDES.get(side))) {
            case IN -> tank.fillOnly(side);
            case OUT -> ClosedHandler.INSTANCE;
            case NONE -> null;
        };
    }

    /** Wires the fluid-handler capability; called from {@code Forgeweave}'s constructor. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ForgeweaveBlockEntities.SEARED_CHANNEL.get(),
                (blockEntity, side) -> blockEntity.handlerFor(side));
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_FLUID, tank.fluid.saveOptional(registries));
        tag.putByteArray(TAG_IS_FLOWING, isFlowing.clone());
    }

    /**
     * ponytail: upstream also saves its {@code locked} counter. It is cleared at the end of every
     * flow tick, so a reload that drops it costs at most one tick of extra drainability -- not worth
     * a field in the save format.
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.fluid = FluidStack.parseOptional(registries, tag.getCompound(TAG_FLUID));
        byte[] saved = tag.getByteArray(TAG_IS_FLOWING);
        for (int i = 0; i < isFlowing.length; i++) {
            isFlowing[i] = i < saved.length ? (byte) Mth.clamp(saved[i], 0, FLOW_TICKS) : 0;
        }
    }

    /** A channel reloaded with fluid in it has lost its scheduled tick, so it books a fresh one. */
    @Override
    public void onLoad() {
        super.onLoad();
        scheduleIfBusy();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------ the tank

    /**
     * Upstream's {@code ChannelTank}: a one-fluid buffer that only ever accepts fluid from outside,
     * with this tick's intake locked against being drained straight back out.
     */
    private final class ChannelTank {
        private FluidStack fluid = FluidStack.EMPTY;
        private int locked;

        /** How much of the buffer may leave this tick. */
        int usable() {
            return Math.max(fluid.getAmount() - locked, 0);
        }

        void drain(int amount) {
            fluid.shrink(amount);
            if (fluid.isEmpty()) {
                fluid = FluidStack.EMPTY;
                sync();
            }
            setChanged();
        }

        int fill(FluidStack resource, IFluidHandler.FluidAction action, @Nullable Direction side) {
            if (resource.isEmpty() || (!fluid.isEmpty() && !FluidStack.isSameFluidSameComponents(fluid, resource))) {
                return 0;
            }
            int accepted = Math.min(resource.getAmount(), CAPACITY - fluid.getAmount());
            if (accepted <= 0 || action.simulate()) {
                return Math.max(accepted, 0);
            }

            boolean wasEmpty = fluid.isEmpty();
            fluid = wasEmpty ? resource.copyWithAmount(accepted) : fluid.copyWithAmount(fluid.getAmount() + accepted);
            locked += accepted;
            setChanged();
            if (wasEmpty) {
                sync();
            }
            if (side != null) {
                setFlow(side, true);
            }
            scheduleIfBusy();
            return accepted;
        }

        /** The view handed out on a face: fills land here, drains never do (only the flow drains). */
        IFluidHandler fillOnly(@Nullable Direction side) {
            return new IFluidHandler() {
                @Override
                public int getTanks() {
                    return 1;
                }

                @Override
                public FluidStack getFluidInTank(int tank) {
                    return fluid.copy();
                }

                @Override
                public int getTankCapacity(int tank) {
                    return CAPACITY;
                }

                @Override
                public boolean isFluidValid(int tank, FluidStack stack) {
                    return fluid.isEmpty() || FluidStack.isSameFluidSameComponents(fluid, stack);
                }

                @Override
                public int fill(FluidStack resource, FluidAction action) {
                    return ChannelTank.this.fill(resource, action, side);
                }

                @Override
                public FluidStack drain(int maxDrain, FluidAction action) {
                    return FluidStack.EMPTY;
                }

                @Override
                public FluidStack drain(FluidStack resource, FluidAction action) {
                    return FluidStack.EMPTY;
                }
            };
        }
    }

    /**
     * What an {@code out} side hands back: a handler that exists (so the neighbour knows there is
     * something to connect to) and takes nothing. Upstream returns its {@code EmptyFluidHandler} here
     * for the same reason.
     */
    private enum ClosedHandler implements IFluidHandler {
        INSTANCE;

        @Override
        public int getTanks() {
            return 0;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
