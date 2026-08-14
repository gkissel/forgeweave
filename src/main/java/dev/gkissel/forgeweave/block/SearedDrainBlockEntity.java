package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A drain's link back to the smeltery core whose fluids it serves (docs/SCOPE.md M2 issue #95),
 * ported from upstream 1.12's {@code TileDrain} (NOTICE.md).
 *
 * <p>Everything about finding and remembering that core lives in {@link SmelteryIoBlockEntity},
 * which the duct and chute (issue #277) share; all a drain adds is handing out the core's tank
 * unfiltered.
 */
public class SearedDrainBlockEntity extends SmelteryIoBlockEntity {

    public SearedDrainBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.SEARED_DRAIN.get(), pos, state);
    }

    /** The smeltery tank this drain serves, or {@code null} while it is not part of a formed structure. */
    @Nullable
    public IFluidHandler fluidHandler() {
        SmelteryControllerBlockEntity core = formedCore();
        return core == null ? null : core.tank();
    }

    /** Wires the fluid-handler capability; called from {@code Forgeweave}'s constructor. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ForgeweaveBlockEntities.SEARED_DRAIN.get(),
                (blockEntity, side) -> blockEntity.fluidHandler());
    }
}
