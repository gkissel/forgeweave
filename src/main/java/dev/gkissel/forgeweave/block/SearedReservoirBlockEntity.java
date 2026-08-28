package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.menu.SearedReservoirMenu;

/**
 * The seared reservoir: a closed seared cuboid that stores molten fluid and nothing else (parity
 * audit T44, issue #475), ported from upstream 1.12's {@code TileTinkerTank} (NOTICE.md).
 *
 * <p><b>Naming.</b> Upstream calls this multiblock the "Tinker Tank". CONTEXT.md's avoided
 * terminology rules that word out of every Forgeweave identifier and player-facing string, so the
 * block is the <em>seared reservoir</em>, named after the seared blocks it is built from the way the
 * seared furnace is.
 *
 * <p>It is the simplest of the three multiblocks: no fuel, no heat, no items, no ticking. All it
 * does is hold a {@link SmelteryTank} whose capacity is a function of the structure, and hand that
 * tank to whatever drain, duct or faucet is attached -- which is why it reuses the smeltery's own
 * tank rather than a single-fluid one: a reservoir layers fluids and lets the player pick which one
 * drains, exactly as a smeltery does.
 *
 * <p><b>Capacity</b> is upstream's {@code updateStructureInfo}: {@code (xd + 2) * (yd + 2) *
 * (zd + 2)} blocks times {@value #CAPACITY_PER_BLOCK} mB, where the {@code +2} deliberately counts
 * the walls, floor and ceiling -- upstream's own comment, "otherwise a 3x3x3 tank is way too little
 * capacity". A minimum 1x1x1 reservoir therefore holds 27 blocks' worth, 108 buckets.
 *
 * <p>Like the smeltery core and the seared furnace this block entity keeps no ongoing heartbeat once
 * formed -- there is nothing to melt or heat, so a formed reservoir goes right back to costing
 * nothing. It rescans on placement, on a neighbour change, on use, and whenever a stale answer is
 * read; while unformed it also keeps a bounded scheduled-tick recheck window open for a few seconds
 * after being touched directly (#772, see {@link #armSettleWindow()}), the same #757 gap the
 * smeltery core and the seared furnace controller both had.
 */
public class SearedReservoirBlockEntity extends BlockEntity implements StationMenuHost, TankOwner, SmelteryTankHost {
    /** Upstream {@code TileTinkerTank.CAPACITY_PER_BLOCK}: four buckets per block of structure. */
    public static final int CAPACITY_PER_BLOCK = 4 * 1000;

    /**
     * How stale {@link #structure()} may be before it rescans.
     *
     * <p>Package-private rather than {@code private}: {@link SearedReservoirControllerBlock#tick}
     * reuses it as the settle-window poll cadence (#772, mirroring {@link
     * SmelteryControllerBlockEntity#RESCAN_INTERVAL_TICKS}), so a rescan is always at least this
     * stale by the time that tick fires and {@link #structure()}'s own staleness check does the
     * actual work.
     */
    static final int RESCAN_INTERVAL_TICKS = 20;

    /**
     * How long an unformed reservoir keeps rechecking itself after a placement or a neighbour change
     * touches it directly, in ticks (#772 -- the same visual-sync gap #757 fixed for the smeltery
     * core). See {@link #armSettleWindow()}; same magnitude as {@link
     * SmelteryControllerBlockEntity#SETTLE_WINDOW_TICKS}.
     */
    private static final int SETTLE_WINDOW_TICKS = 100;

    private static final long NEVER = Long.MIN_VALUE;

    private static final String TAG_STRUCTURE = "structure";
    private static final String TAG_TANK = "tank";

    @Nullable
    private SmelteryStructure structure;
    private Component lastResult = Component.translatable(SearedReservoirScan.KEY_NOT_SCANNED);
    private long lastScanTick = NEVER;
    /** #772: end of the current settle window, or {@link #NEVER} if none is open. See {@link #armSettleWindow()}. */
    private long settleUntilTick = NEVER;

    private final SmelteryTank tank = new SmelteryTank(0, this::syncToClients);

    public SearedReservoirBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.SEARED_RESERVOIR.get(), pos, state);
    }

    // ------------------------------------------------------------------ structure

    /** Why the last scan formed or failed, as a player-facing message. */
    public Component lastResult() {
        return lastResult;
    }

    /** The formed structure, or {@code null}; rescans a stale answer on the server. */
    @Nullable
    public SmelteryStructure structure() {
        if (level != null && !level.isClientSide
                && (lastScanTick == NEVER || level.getGameTime() - lastScanTick >= RESCAN_INTERVAL_TICKS)) {
            updateStructure();
        }
        return structure;
    }

    @Override
    public boolean isFormed() {
        return structure() != null;
    }

    @Override
    public SmelteryTank tank() {
        return tank;
    }

    /**
     * Upstream {@code TileTinkerTank#updateStructureInfo}: the whole cuboid including its shell.
     * {@link SmelteryStructure} measures the interior, so each axis gains the two blocks of wall.
     */
    public static int capacityFor(@Nullable SmelteryStructure structure) {
        return structure == null
                ? 0
                : (structure.width() + 2) * (structure.height() + 2) * (structure.depth() + 2) * CAPACITY_PER_BLOCK;
    }

    /** Rescans now. Server-side only. */
    @Override
    public void updateStructure() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SearedReservoirControllerBlock)) {
            return;
        }
        lastScanTick = level.getGameTime();
        // Upstream checkIfMultiblockCanBeRechecked: never fail a formed structure over unloaded chunks.
        SmelteryStructure current = structure;
        if (current != null && !level.hasChunksAt(current.interiorMin().offset(-1, -1, -1), current.interiorMax().offset(1, 1, 1))) {
            return;
        }

        SearedReservoirScan.Result result =
                SearedReservoirScan.scan(level, worldPosition, state.getValue(SearedReservoirControllerBlock.FACING));
        lastResult = result.message();
        SmelteryStructure found = result.structure();
        if (!Objects.equals(found, structure)) {
            structure = found;
            // setCapacity spills anything that no longer fits, from the top of the melt down.
            tank.setCapacity(capacityFor(found));
            setChanged();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        if (found != null) {
            // Upstream assigns its master to every structure block; Forgeweave only has somewhere to
            // put that on the tanks and I/O blocks, and it is what stops a smeltery next door from
            // taking a drain out of this reservoir's wall (#288's rule, SmelteryScan#claimedByAnotherCore).
            List<BlockPos> claimed = new ArrayList<>(result.io());
            claimed.addAll(result.tanks());
            for (BlockPos pos : claimed) {
                switch (level.getBlockEntity(pos)) {
                    case SmelteryIoBlockEntity io -> io.setCore(worldPosition);
                    case SearedTankBlockEntity wallTank -> wallTank.setCore(worldPosition);
                    case null, default -> { }
                }
            }
        }
        if (state.getValue(SearedReservoirControllerBlock.ACTIVE) != (found != null)) {
            level.setBlock(worldPosition, state.setValue(SearedReservoirControllerBlock.ACTIVE, found != null), Block.UPDATE_ALL);
        }
    }

    /**
     * #772: opens (or extends) a bounded recheck window for an unformed reservoir, and makes sure a
     * tick is actually scheduled to use it. Called after every {@link #updateStructure()} that
     * {@link SearedReservoirControllerBlock#onPlace}/{@code neighborChanged} triggers -- those only
     * reach a block adjacent to the controller itself, so completing the structure a few blocks
     * further out would otherwise sit unnoticed until a player clicks it, exactly {@link
     * SmelteryControllerBlockEntity#armSettleWindow()}'s #757 gap. A no-op once formed: a reservoir
     * needs no ongoing heartbeat once formed ({@link #armMeltTick} below is itself a no-op).
     */
    void armSettleWindow() {
        if (level == null || level.isClientSide || isFormed()) {
            return;
        }
        settleUntilTick = level.getGameTime() + SETTLE_WINDOW_TICKS;
        Block block = getBlockState().getBlock();
        if (!level.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            level.scheduleTick(worldPosition, block, RESCAN_INTERVAL_TICKS);
        }
    }

    /** Whether {@link #armSettleWindow()}'s recheck window is still open. */
    boolean settling() {
        return level != null && settleUntilTick != NEVER && level.getGameTime() < settleUntilTick;
    }

    /** A reservoir burns nothing, so there is no melt to wake ({@link TankOwner}'s other half). */
    @Override
    public void armMeltTick() {
        // nothing to arm
    }

    /**
     * Upstream's {@code SmelteryFluidClicked}: the clicked fluid in the GUI becomes the one a drain
     * pours. The index is untrusted and range-checked by {@link SmelteryTank#moveToBottom}.
     */
    public void selectDrainFluid(int index) {
        tank.moveToBottom(index);
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.forgeweave.seared_reservoir.name");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SearedReservoirMenu(containerId, playerInventory,
                ContainerLevelAccess.create(level, worldPosition), worldPosition);
    }

    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
    }

    // ------------------------------------------------------------------ persistence + sync

    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (structure != null) {
            SmelteryStructure.CODEC.encodeStart(NbtOps.INSTANCE, structure)
                    .resultOrPartial(error -> {})
                    .ifPresent(encoded -> tag.put(TAG_STRUCTURE, encoded));
        }
        tag.put(TAG_TANK, tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        structure = tag.contains(TAG_STRUCTURE)
                ? SmelteryStructure.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_STRUCTURE)).resultOrPartial(error -> {}).orElse(null)
                : null;
        // Capacity is a function of the structure and so is never saved -- restore it before the
        // fluids load, or setCapacity would trim a full reservoir down to a capacity of zero.
        tank.setCapacity(capacityFor(structure));
        tank.readFromNBT(registries, tag.getCompound(TAG_TANK));
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
