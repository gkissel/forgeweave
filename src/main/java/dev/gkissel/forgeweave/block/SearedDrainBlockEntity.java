package dev.gkissel.forgeweave.block;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A drain's link back to the smeltery core whose fluids it serves (docs/SCOPE.md M2 issue #95),
 * ported from upstream 1.12's {@code TileDrain} (NOTICE.md).
 *
 * <p>Upstream reaches its smeltery through the "servant" tile entity every structure block carries;
 * Forgeweave has no servants (see {@link SmelteryControllerBlockEntity}), so the core hands each
 * drain its own position whenever a scan succeeds, and the drain checks on use that the core is
 * still there and still formed. A drain that was never part of a formed structure -- or whose
 * structure has since broken -- simply exposes no fluid handler.
 *
 * <p>A drain with no core asks for one as it enters the level ({@link #onLoad}), which is what makes
 * adding a drain to a smeltery that is already standing work at all.
 */
public class SearedDrainBlockEntity extends BlockEntity {
    private static final String TAG_CORE = "core";

    @Nullable
    private BlockPos corePos;

    public SearedDrainBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.SEARED_DRAIN.get(), pos, state);
    }

    /** Called by {@link SmelteryControllerBlockEntity} for every drain in a structure it just formed. */
    public void setCore(BlockPos corePos) {
        if (!corePos.equals(this.corePos)) {
            this.corePos = corePos;
            setChanged();
        }
    }

    /** The smeltery tank this drain serves, or {@code null} while it is not part of a formed structure. */
    @Nullable
    public IFluidHandler fluidHandler() {
        if (level == null || corePos == null || !level.isLoaded(corePos)) {
            return null;
        }
        return level.getBlockEntity(corePos) instanceof SmelteryControllerBlockEntity core && core.isFormed()
                ? core.tank()
                : null;
    }

    /**
     * #183 -- a drain added to a smeltery that is <em>already standing</em> has no way to hear about
     * it. {@link SmelteryControllerBlockEntity} only rescans when a block next to the core changes,
     * when a player uses the core, or when something reads its {@code structure()}; an idle smeltery
     * has no reader, and this drain cannot become that reader until it knows which core to read.
     * Building the smeltery first and swapping a wall block for a drain afterwards -- the order every
     * player builds in -- therefore left the drain unlinked forever: no fluid handler, and a faucet
     * hanging off it that moved nothing at all on right-click.
     *
     * <p>So an unlinked drain asks, once, as it enters the level: every core near enough for this
     * drain to be in its walls rescans, and whichever one finds it claims it through {@link
     * #setCore}. A smeltery is at most {@link SmelteryScan#MAX_SIZE} + 2 blocks across, so "near
     * enough" is this chunk and the eight around it. The core stays the only thing that decides what
     * a smeltery is -- the drain just asks it to look again.
     *
     * <p>ponytail: a sweep of the loaded block entities in nine chunks, run only by a drain with no
     * core -- which, once one claims it, is never again, because the core position is saved. A drain
     * that belongs to no smeltery at all repeats it once per chunk load; index cores by chunk if that
     * ever shows up in a profile.
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
                // Copied: a rescan assigns drains and tanks, which can promote block entities into
                // the very map being walked.
                for (BlockEntity blockEntity : List.copyOf(level.getChunk(x, z).getBlockEntities().values())) {
                    if (blockEntity instanceof SmelteryControllerBlockEntity core) {
                        core.updateStructure();
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

    /** Wires the fluid-handler capability; called from {@code Forgeweave}'s constructor. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ForgeweaveBlockEntities.SEARED_DRAIN.get(),
                (blockEntity, side) -> blockEntity.fluidHandler());
    }
}
