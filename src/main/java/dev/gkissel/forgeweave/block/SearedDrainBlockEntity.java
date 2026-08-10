package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
