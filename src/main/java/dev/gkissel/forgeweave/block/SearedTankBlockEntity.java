package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * The four-bucket fluid store behind every seared tank, gauge and window (docs/SCOPE.md M2 issue
 * #95), ported from upstream 1.12's {@code TileTank} (NOTICE.md) -- including its capacity of four
 * buckets and its comparator output scaled over that capacity.
 *
 * <p>One block entity type backs all three tank blocks: upstream models them as one block with a
 * {@code type} property and identical behaviour, differing only in appearance.
 *
 * <p>Contents survive being broken and replaced, the way upstream's {@code getDrops}/{@code
 * onBlockPlacedBy} pair keeps them on the item stack, using vanilla 1.21's implicit block-entity
 * components instead of raw stack NBT ({@link ForgeweaveDataComponents#FLUID_CONTENT}).
 */
public class SearedTankBlockEntity extends BlockEntity {
    /** Upstream {@code TileTank.CAPACITY}: four buckets. */
    public static final int CAPACITY = 4 * 1000;

    private static final String TAG_TANK = "tank";

    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    };

    public SearedTankBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.SEARED_TANK.get(), pos, state);
    }

    public FluidTank tank() {
        return tank;
    }

    /** Upstream's {@code comparatorStrength}. */
    public int comparatorStrength() {
        return tank.getCapacity() == 0 ? 0 : 15 * tank.getFluidAmount() / tank.getCapacity();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_TANK, tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound(TAG_TANK));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(ForgeweaveDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(tank.getFluid()));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        SimpleFluidContent stored = componentInput.get(ForgeweaveDataComponents.FLUID_CONTENT.get());
        if (stored != null) {
            tank.setFluid(stored.copy());
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

    /** Wires the fluid-handler capability for all three tank blocks; called from {@code Forgeweave}'s constructor. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ForgeweaveBlockEntities.SEARED_TANK.get(),
                (blockEntity, side) -> blockEntity.tank);
    }
}
