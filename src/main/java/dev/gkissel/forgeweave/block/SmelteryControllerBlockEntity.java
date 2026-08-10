package dev.gkissel.forgeweave.block;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A smeltery core's structure state and molten-metal tank (docs/SCOPE.md M2 issue #95), ported from
 * upstream 1.12's {@code TileMultiblock}/{@code TileSmeltery} (NOTICE.md).
 *
 * <p><b>This block entity has no ticker at all</b> -- {@link SmelteryControllerBlock} never
 * registers one, so an idle smeltery costs literally nothing per tick, which is what the SCOPE.md M2
 * release gate ("spark profile confirms idle smeltery ~= zero tick") asks for. Upstream instead
 * ticks forever: once a second while unformed to look for a structure, and once every 15 seconds
 * plus a one-block-per-second interior sweep while formed. Forgeweave replaces the polling with:
 *
 * <ul>
 *   <li><b>Events for anything touching the core</b> -- placement, a neighbour changing, a player
 *       using it (see {@link SmelteryControllerBlock}).
 *   <li><b>Revalidation on read</b> for everything else. {@link #structure()} rescans when its answer
 *       is more than {@value #RESCAN_INTERVAL_TICKS} ticks old, so a wall broken on the far side of a
 *       9x9 is noticed the next time something asks -- and nothing asks while the smeltery is idle.
 * </ul>
 *
 * <p>Forgeweave has no equivalent of upstream's per-structure-block "servant" tile entities (issue
 * #93 ships the seared blocks as plain blocks), which is what lets upstream be notified of a distant
 * wall break directly. Revalidation-on-read costs one scan per second of active work instead, which
 * is still strictly less often than upstream's own 15-second full recheck.
 */
public class SmelteryControllerBlockEntity extends BlockEntity {
    /**
     * Fluid capacity each interior block contributes, upstream's {@code CAPACITY_PER_BLOCK} of eight
     * ingots at 144 mB each.
     */
    public static final int CAPACITY_PER_BLOCK = 8 * 144;

    /**
     * How stale {@link #structure()} may be before it rescans.
     *
     * <p>ponytail: one second, matching upstream's unformed-poll rate and beating its 15-second
     * formed recheck. Drop it to 0 (rescan on every read) if a case turns up where a smeltery
     * working out of a broken structure for up to a second matters.
     */
    private static final int RESCAN_INTERVAL_TICKS = 20;

    private static final long NEVER_SCANNED = Long.MIN_VALUE;

    private static final String TAG_STRUCTURE = "structure";
    private static final String TAG_TANK = "tank";

    private final SmelteryCore core;
    private final FluidTank tank = new FluidTank(CAPACITY_PER_BLOCK);

    @Nullable
    private SmelteryStructure structure;
    private Component lastResult = Component.translatable(SmelteryScan.KEY_NOT_SCANNED);
    private long lastScanTick = NEVER_SCANNED;

    public SmelteryControllerBlockEntity(BlockPos pos, BlockState state, SmelteryCore core) {
        super(core.blockEntityType().get(), pos, state);
        this.core = core;
    }

    /** Which core tier this structure has; the melting work (#96/#99) multiplies yields by {@link SmelteryCore#yieldMultiplier()}. */
    public SmelteryCore core() {
        return core;
    }

    /** The molten-metal tank. Its capacity tracks the interior size; it is filled by melting (#96) and drained through a {@link SearedDrainBlock}. */
    public FluidTank tank() {
        return tank;
    }

    /** Why the last scan formed or failed, as a player-facing message. */
    public Component lastResult() {
        return lastResult;
    }

    /**
     * The formed structure, or {@code null} if this core has none. Rescans first when the cached
     * answer is stale (see the class javadoc); on the client this returns the last synced value
     * without scanning.
     */
    @Nullable
    public SmelteryStructure structure() {
        if (level != null && !level.isClientSide
                && (lastScanTick == NEVER_SCANNED || level.getGameTime() - lastScanTick >= RESCAN_INTERVAL_TICKS)) {
            updateStructure();
        }
        return structure;
    }

    public boolean isFormed() {
        return structure() != null;
    }

    /** Rescans now, regardless of how fresh the cached answer is. Server-side only. */
    public void updateStructure() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SmelteryControllerBlock)) {
            return;
        }

        lastScanTick = level.getGameTime();
        SmelteryScan.Result result = SmelteryScan.scan(level, worldPosition, state.getValue(SmelteryControllerBlock.FACING));
        lastResult = result.message();

        SmelteryStructure found = result.structure();
        if (!Objects.equals(found, structure)) {
            structure = found;
            resizeTank();
            setChanged();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        if (found != null) {
            assignDrains(result.drains());
        }
        if (state.getValue(SmelteryControllerBlock.ACTIVE) != (found != null)) {
            level.setBlock(worldPosition, state.setValue(SmelteryControllerBlock.ACTIVE, found != null), Block.UPDATE_ALL);
        }
    }

    /** Points every drain in the walls back at this core so it can serve the smeltery's fluids. */
    private void assignDrains(List<BlockPos> drains) {
        for (BlockPos pos : drains) {
            if (level != null && level.getBlockEntity(pos) instanceof SearedDrainBlockEntity drain) {
                drain.setCore(worldPosition);
            }
        }
    }

    /** Capacity follows the interior size; an interior that shrank spills nothing but caps what is held. */
    private void resizeTank() {
        tank.setCapacity(structure == null ? CAPACITY_PER_BLOCK : structure.interiorVolume() * CAPACITY_PER_BLOCK);
        if (tank.getFluidAmount() > tank.getCapacity()) {
            tank.getFluid().setAmount(tank.getCapacity());
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
                ? SmelteryStructure.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_STRUCTURE))
                        .resultOrPartial(error -> {})
                        .orElse(null)
                : null;
        tank.readFromNBT(registries, tag.getCompound(TAG_TANK));
        resizeTank();
    }

    /** Structure bounds and tank contents are what the client needs for the smeltery GUI and fluid rendering (#101). */
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
