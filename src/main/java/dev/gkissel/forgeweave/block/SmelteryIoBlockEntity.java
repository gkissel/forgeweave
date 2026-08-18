package dev.gkissel.forgeweave.block;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A smeltery wall block that re-exposes something of its core's to whatever is outside the walls:
 * the drain (fluids, issue #95), the duct (filtered fluids, issue #277) and the chute (items,
 * issue #277).
 *
 * <p>Upstream reaches its smeltery through the "servant" tile entity every structure block carries
 * ({@code SmelteryInputOutputBlockEntity} in the 1.20 clone); Forgeweave has no servants (see {@link
 * SmelteryControllerBlockEntity}), so the core hands each I/O block its own position whenever a scan
 * succeeds, and the block checks on use that the core is still there and still formed. An I/O block
 * that was never part of a formed structure -- or whose structure has since broken -- exposes
 * nothing.
 *
 * <p>This class is the part all three share: which core claimed it, saving that across a reload,
 * asking for one on load, and handing subclasses the still-formed core to read.
 */
public abstract class SmelteryIoBlockEntity extends BlockEntity {
    private static final String TAG_CORE = "core";

    @Nullable
    private BlockPos corePos;

    protected SmelteryIoBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Which core claimed this block, or {@code null} if none ever did. Read by {@link SmelteryScan} so
     * a second smeltery cannot take an I/O block a still-formed one already owns (#288).
     */
    @Nullable
    public BlockPos core() {
        return corePos;
    }

    /** Called by {@link SmelteryControllerBlockEntity} for every I/O block in a structure it just formed. */
    public void setCore(BlockPos corePos) {
        if (corePos.equals(this.corePos)) {
            return;
        }
        this.corePos = corePos;
        setChanged();
        if (level != null) {
            // What this block hands out is a function of its core, so a claim changes the answer --
            // and a hopper or pipe next to a chute holds a BlockCapabilityCache that will otherwise
            // keep the "no core, nothing here" answer it got before the smeltery formed.
            level.invalidateCapabilities(worldPosition);
        }
    }

    /**
     * The formed structure's fluid store, whichever kind of controller claimed this block -- a
     * smeltery core or, since parity audit T44 (issue #475), a seared reservoir. Upstream's drain
     * reads {@code ISmelteryTankHandler} for the same reason: both multiblocks pour through it.
     *
     * @return {@code null} while this block is not part of a formed structure
     */
    @Nullable
    protected SmelteryTank formedTank() {
        if (level == null || corePos == null || !level.isLoaded(corePos)) {
            return null;
        }
        return level.getBlockEntity(corePos) instanceof SmelteryTankHost host && host.isFormed()
                ? host.tank()
                : null;
    }

    /** The core this block serves while it is part of a formed structure, else {@code null}. */
    @Nullable
    protected SmelteryControllerBlockEntity formedCore() {
        if (level == null || corePos == null || !level.isLoaded(corePos)) {
            return null;
        }
        return level.getBlockEntity(corePos) instanceof SmelteryControllerBlockEntity core && core.isFormed()
                ? core
                : null;
    }

    /**
     * #183 -- an I/O block added to a smeltery that is <em>already standing</em> has no way to hear
     * about it. {@link SmelteryControllerBlockEntity} only rescans when a block next to the core
     * changes, when a player uses the core, or when something reads its {@code structure()}; an idle
     * smeltery has no reader, and this block cannot become that reader until it knows which core to
     * read. Building the smeltery first and swapping a wall block for a drain afterwards -- the order
     * every player builds in -- therefore left the drain unlinked forever: no fluid handler, and a
     * faucet hanging off it that moved nothing at all on right-click.
     *
     * <p>So an unlinked I/O block asks, once, as it enters the level: every core near enough for it to
     * be in its walls rescans, and whichever one finds it claims it through {@link #setCore}. A
     * smeltery is at most {@link SmelteryScan#MAX_SIZE} + 2 blocks across, so "near enough" is this
     * chunk and the eight around it. The core stays the only thing that decides what a smeltery is --
     * the I/O block just asks it to look again.
     *
     * <p>ponytail: a sweep of the loaded block entities in nine chunks, run only by an I/O block with
     * no core -- which, once one claims it, is never again, because the core position is saved. A
     * block that belongs to no smeltery at all repeats it once per chunk load; index cores by chunk if
     * that ever shows up in a profile.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (corePos != null || level == null || level.isClientSide) {
            return;
        }
        ChunkPos chunk = new ChunkPos(worldPosition);
        for (int x = chunk.x - 1; x <= chunk.x + 1; x++) {
            for (int z = chunk.z - 1; z <= chunk.z + 1; z++) {
                if (!level.hasChunk(x, z)) {
                    continue;
                }
                // Copied: a rescan assigns I/O blocks and tanks, which can promote block entities into
                // the very map being walked.
                for (BlockEntity blockEntity : List.copyOf(level.getChunk(x, z).getBlockEntities().values())) {
                    if (blockEntity instanceof SmelteryTankHost host) {
                        host.updateStructure();
                    }
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (corePos != null) {
            tag.put(TAG_CORE, NbtUtils.writeBlockPos(corePos));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        corePos = tag.contains(TAG_CORE) ? NbtUtils.readBlockPos(tag, TAG_CORE).orElse(null) : null;
    }
}
