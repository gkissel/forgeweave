package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The faucet (docs/SCOPE.md M2 issue #100), ported from upstream 1.12's {@code TileFaucet}
 * (NOTICE.md). Right-clicking starts a pour from whatever fluid handler the faucet faces -- a
 * smeltery drain, a seared tank -- into whatever is directly below it, usually a casting table or
 * basin.
 *
 * <p>Upstream's flow, kept exactly:
 *
 * <ul>
 *   <li>A pour moves {@value #TRANSACTION_AMOUNT} mB at a time (upstream's {@code
 *       Material.VALUE_Ingot}), but only as much of that as the target will actually take -- so a
 *       casting table wanting 288 mB takes two transactions, and one wanting nothing takes none.
 *   <li>That amount leaves the source <em>immediately</em> and is buffered here, then trickles into
 *       the target at {@value #LIQUID_TRANSFER} mB per tick, which is what makes a full ingot take
 *       24 ticks to pour.
 *   <li>When the buffer empties the faucet starts another transaction, and keeps going until either
 *       the source runs dry, the target refuses, or a second right-click asks it to stop after the
 *       transaction in flight.
 * </ul>
 *
 * <p><b>Redstone (#207).</b> A powered faucet does not need any of that. It pours whenever its
 * source has fluid and its target has room, and when either is momentarily not true it waits and
 * looks again -- upstream 1.20's {@code POWERED} faucet state, which is how upstream itself made a
 * powered faucet keep up with a smeltery that is still melting. No pulse loop is involved: the power
 * level is read at each transaction boundary rather than remembered from an edge. Dropping the
 * signal sets the same stop flag a second right-click sets, so the transaction in flight lands and
 * the faucet then stops.
 *
 * <p><b>No block-entity ticker.</b> Like the casting blocks, an idle faucet must cost nothing
 * (docs/SCOPE.md M2 performance budget), so pouring runs on a vanilla scheduled block tick that
 * re-books itself each tick while there is fluid in the buffer; upstream instead ticks every faucet
 * in the world forever and returns immediately when not pouring.
 */
public class FaucetBlockEntity extends BlockEntity {
    /** Upstream's {@code TileFaucet.LIQUID_TRANSFER}: millibuckets moved into the target per tick. */
    public static final int LIQUID_TRANSFER = 6;

    /** Upstream's {@code TileFaucet.TRANSACTION_AMOUNT}: {@code Material.VALUE_Ingot}, drained from the source in one go. */
    public static final int TRANSACTION_AMOUNT = 144;

    /** Upstream's two-tick delay between a redstone pulse and the pour it starts. */
    private static final int REDSTONE_DELAY_TICKS = 2;

    private static final String TAG_DRAINED = "drained";
    private static final String TAG_STOP = "stop";
    /** Upstream 1.20's {@code lastRedstone}: without it a reload forgets the edge it has to stop on. */
    private static final String TAG_REDSTONE = "redstone";

    private boolean pouring;
    private boolean stopRequested;
    private boolean lastRedstoneState;
    /** What was pulled from the source and has not reached the target yet. */
    private FluidStack buffered = FluidStack.EMPTY;

    public FaucetBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.FAUCET.get(), pos, state);
    }

    public boolean isPouring() {
        return pouring;
    }

    /** What is mid-pour, for renderers and tests. */
    public FluidStack buffered() {
        return buffered;
    }

    /**
     * Upstream's {@code activate}: starts a pour, or asks a running one to stop once its current
     * transaction has drained.
     *
     * @return whether the faucet is pouring after this call
     */
    public boolean activate() {
        if (level == null || level.isClientSide) {
            return pouring;
        }
        if (pouring) {
            stopRequested = true;
            return true;
        }
        beginTransaction();
        return pouring;
    }

    /**
     * Upstream's {@code handleRedstone}: a rising edge starts a pour after a short delay. The
     * falling edge is #207's: it asks a running pour to stop once its current transaction has
     * drained, exactly as a second right-click does, and lets a powered-but-waiting faucet fall out
     * of {@link #beginTransaction}'s retry loop on its next look.
     */
    public void handleRedstone(boolean hasSignal) {
        if (hasSignal == lastRedstoneState) {
            return;
        }
        lastRedstoneState = hasSignal;
        if (level == null || level.isClientSide) {
            return;
        }
        if (hasSignal) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), REDSTONE_DELAY_TICKS);
        } else if (pouring) {
            stopRequested = true;
            setChanged();
        }
    }

    /** Whether redstone is telling this faucet to keep going, read fresh rather than remembered. */
    private boolean isPowered() {
        return level != null && level.hasNeighborSignal(worldPosition);
    }

    /** The scheduled tick: one {@value #LIQUID_TRANSFER} mB step, or the handoff to the next transaction. */
    void pourStep() {
        if (level == null || level.isClientSide || !pouring) {
            return;
        }
        if (buffered.isEmpty()) {
            if (stopRequested) {
                reset();
            } else {
                beginTransaction();
            }
            return;
        }
        pour();
        scheduleNextStep();
    }

    /**
     * Upstream's {@code doTransfer}: simulate a drain from the source and a fill into the target, and
     * only when both say yes actually pull exactly what the target accepted. Anything else stops the
     * faucet -- an empty source, a missing target, a target that wants nothing.
     */
    private void beginTransaction() {
        if (!buffered.isEmpty() || level == null) {
            return;
        }
        Direction facing = getBlockState().getValue(FaucetBlock.FACING);
        IFluidHandler source = handlerAt(worldPosition.relative(facing), facing.getOpposite());
        IFluidHandler target = handlerAt(worldPosition.below(), Direction.UP);
        if (source != null && target != null) {
            FluidStack available = source.drain(TRANSACTION_AMOUNT, IFluidHandler.FluidAction.SIMULATE);
            if (!available.isEmpty()) {
                int accepted = target.fill(available, IFluidHandler.FluidAction.SIMULATE);
                if (accepted > 0) {
                    buffered = source.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                    pouring = true;
                    pour();
                    scheduleNextStep();
                    sync();
                    return;
                }
            }
        }
        reset();
        // #207, upstream 1.20's POWERED state: a powered faucet with nothing to move right now is
        // not done, it is waiting -- for the smeltery to melt the next ingot, or for the finished
        // cast below to be carried off -- so it looks again next tick instead of going idle. An
        // unpowered one stops here, which is why an idle faucet still costs nothing.
        if (isPowered()) {
            scheduleNextStep();
        }
    }

    /** Upstream's {@code pour}: one step of the buffered fluid into the block below. */
    private void pour() {
        IFluidHandler target = handlerAt(worldPosition.below(), Direction.UP);
        if (target == null) {
            // Upstream: the receiving block vanished, and the buffered fluid goes with it.
            reset();
            return;
        }
        FluidStack step = buffered.copyWithAmount(Math.min(buffered.getAmount(), LIQUID_TRANSFER));
        int filled = target.fill(step, IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            buffered.shrink(filled);
        }
        setChanged();
    }

    private void scheduleNextStep() {
        if (level != null && !level.isClientSide) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    private void reset() {
        pouring = false;
        stopRequested = false;
        // #207: the remembered signal deliberately survives a reset. Upstream 1.12 cleared it so the
        // next neighbour update looked like a fresh rising edge and re-armed the faucet; a faucet
        // that keeps looking while powered has no use for that, and clearing it would throw away the
        // falling edge that stops one.
        buffered = FluidStack.EMPTY;
        setChanged();
        sync();
    }

    @Nullable
    private IFluidHandler handlerAt(BlockPos pos, Direction side) {
        return level == null || !level.isLoaded(pos)
                ? null
                : level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Both keys go in unconditionally, empty buffer and all. This same tag is what getUpdateTag
        // sends, and NeoForge's IBlockEntityExtension#onDataPacket drops any packet whose tag is
        // empty -- so an idle faucet, which had nothing else to write, was telling every client
        // exactly nothing when its pour ended, and they went on drawing the stream forever (#200).
        // saveOptional/parseOptional is the same never-empty pair SmelteryControllerBlockEntity
        // already uses for its fuel-gauge fluid, and it still reads a pre-#200 save that has no
        // "drained" key at all: getCompound returns an empty tag and parseOptional makes it EMPTY.
        tag.put(TAG_DRAINED, buffered.saveOptional(registries));
        tag.putBoolean(TAG_STOP, stopRequested);
        tag.putBoolean(TAG_REDSTONE, lastRedstoneState);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffered = FluidStack.parseOptional(registries, tag.getCompound(TAG_DRAINED));
        pouring = !buffered.isEmpty();
        stopRequested = pouring && tag.getBoolean(TAG_STOP);
        lastRedstoneState = tag.getBoolean(TAG_REDSTONE);
    }

    /**
     * A faucet loaded mid-pour has no scheduled tick left (a chunk unload drops it), so it books one
     * as soon as it is back in a level -- upstream's always-on ticker picks the pour up for free.
     *
     * <p>A powered-but-waiting faucet (#207) needs nothing here: its retry is a vanilla scheduled
     * block tick, and those are saved with the chunk. Deliberately no {@code hasNeighborSignal} call
     * on load either -- upstream needs a redstone edge to arm a faucet too, and polling six
     * neighbours from inside a chunk load is how chunk-load cascades start.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (pouring) {
            scheduleNextStep();
        }
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
}
