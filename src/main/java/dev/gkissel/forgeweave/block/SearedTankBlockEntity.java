package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
    private static final String TAG_CORE = "core";

    /** Which core this tank belongs to, if any -- set by its {@link TankOwner} on a scan (#97; a smeltery core or, since #442, a seared furnace). */
    @Nullable
    private BlockPos corePos;

    /** So {@link #onContentsChanged} can tell a fill from a drain; only a fill needs to wake the core. */
    private int lastFluidAmount;

    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
                // #97: a fill (not a drain -- the smeltery's own fuel consumption must not re-arm
                // itself) is what needs to wake a core that stopped ticking for want of fuel. Nothing
                // else notices a wall tank far from the core changing; see
                // SmelteryControllerBlockEntity#armMeltTick.
                if (corePos != null && getFluidAmount() > lastFluidAmount
                        && level.getBlockEntity(corePos) instanceof TankOwner core) {
                    core.armMeltTick();
                }
                lastFluidAmount = getFluidAmount();
            }
        }
    };

    public SearedTankBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.SEARED_TANK.get(), pos, state);
    }

    public FluidTank tank() {
        return tank;
    }

    /**
     * Which core claimed this tank, or {@code null} if none ever did. Read by {@link SmelteryScan} so
     * a second smeltery cannot take a tank a still-formed one already owns (#288).
     */
    @Nullable
    public BlockPos core() {
        return corePos;
    }

    /** Called by {@link SmelteryControllerBlockEntity} for every wall tank in a structure it just formed (#97). */
    public void setCore(BlockPos corePos) {
        if (!corePos.equals(this.corePos)) {
            this.corePos = corePos;
            setChanged();
        }
    }

    /** Upstream's {@code comparatorStrength}. */
    public int comparatorStrength() {
        return tank.getCapacity() == 0 ? 0 : 15 * tank.getFluidAmount() / tank.getCapacity();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_TANK, tank.writeToNBT(registries, new CompoundTag()));
        if (corePos != null) {
            tag.put(TAG_CORE, NbtUtils.writeBlockPos(corePos));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound(TAG_TANK));
        lastFluidAmount = tank.getFluidAmount();
        corePos = tag.contains(TAG_CORE) ? NbtUtils.readBlockPos(tag, TAG_CORE).orElse(null) : null;
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
