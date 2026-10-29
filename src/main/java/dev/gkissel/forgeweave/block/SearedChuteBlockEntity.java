package dev.gkissel.forgeweave.block;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * A chute's link back to the smeltery core whose melting inventory it serves (docs/SCOPE.md M3.4
 * issue #277), ported from the 1.20 clone's {@code SmelteryInputOutputBlockEntity.ChuteBlockEntity}
 * (NOTICE.md) -- a maintainer-approved deviation from the 1.12 parity default, recorded on issue
 * #277: the 1.12 generation has only the plain drain.
 *
 * <p>Upstream's chute is nothing but the item-handler half of the same "re-expose the master's
 * capability" base the drain uses, and this is the same: it hands out {@link
 * SmelteryControllerBlockEntity#meltingContainer()} through {@link TransferBridge#items}, so
 * insertion goes through the melting inventory's own {@code canPlaceItem} (only what the smeltery can
 * melt, one item per interior block) and extraction takes a not-yet-melted item back out. There is
 * deliberately no pulling of its own: a chute is a port, not a hopper -- something outside has to
 * push or pull, exactly as upstream has it.
 */
public class SearedChuteBlockEntity extends SmelteryIoBlockEntity {

    public SearedChuteBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.SEARED_CHUTE.get(), pos, state);
    }

    /**
     * The smeltery's melting inventory, or {@code null} while the chute is not part of a formed
     * structure.
     *
     * <p>Built fresh each call because {@code meltingContainer()} is itself a live view that a resize
     * replaces the backing list of; caching one here would hand out a handler over a stale interior.
     */
    @Nullable
    public TransferBridge.ItemStore itemHandler() {
        SmelteryControllerBlockEntity core = formedCore();
        return core == null ? null : core.itemCapability();
    }

    /** {@link #itemHandler()} as the transfer API sees it; what the capability hands out. */
    @Nullable
    public ResourceHandler<ItemResource> itemCapability() {
        SmelteryControllerBlockEntity core = formedCore();
        return core == null ? null : core.itemCapability();
    }

    /** Wires the item-handler capability; called from {@code Forgeweave}'s constructor. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ForgeweaveBlockEntities.SEARED_CHUTE.get(),
                (blockEntity, side) -> blockEntity.itemCapability());
    }
}
