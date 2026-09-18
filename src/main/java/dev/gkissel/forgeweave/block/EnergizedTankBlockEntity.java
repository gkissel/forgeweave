package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * The energized tank: a smeltery wall block that heats the smeltery to the temperature of the fuel
 * it holds a sample of, and burns Forge Energy rather than the fuel to do it (docs/SCOPE.md M8,
 * D-M8-11; issue #972). Original Forgeweave design -- the 1.12 generation has no equivalent block,
 * so nothing here is derived from either clone.
 *
 * <p>It holds two things.
 *
 * <ul>
 *   <li><b>A fuel sample</b>, one bucket of a fluid that is never consumed. It says which fuel the
 *       tank is imitating, and that fluid's own {@link SmelteryFuel#temperature()} is the tank's
 *       temperature. A fluid the smeltery cannot burn is not a valid sample and the fill is refused
 *       outright rather than accepted and ignored, so a player who tries to pour water in finds out
 *       at the moment they try.
 *   <li><b>A Forge Energy buffer</b>, which is what actually burns. Mekanism, Powah or anything
 *       else exposing {@link Capabilities.EnergyStorage#BLOCK} drives it identically; there is no
 *       per-mod code anywhere in this class.
 * </ul>
 *
 * <p>The smeltery drives everything else. {@link SmelteryControllerBlockEntity} resolves which tank
 * pays and charges it once per melt tick that actually heated something -- see
 * {@link EnergizedHeat} for the two numbers involved and
 * {@link SmelteryControllerBlockEntity#meltTick()} for how the tank competes with a lit wall-tank
 * fuel. Like a seared tank, this block has no ticker of its own.
 *
 * <p>Off under {@link ForgeweaveConfig#ENERGIZED_TANK} the block is dormant, not gone: it still
 * loads, still holds its sample, its buffer and its overdrive setting, and still accepts energy;
 * only {@link #temperature()} answers zero, which is what takes it out of the smeltery's heat
 * resolution. That is D-M8-5's inert-not-destructive contract.
 */
public class EnergizedTankBlockEntity extends BlockEntity {
    /** One bucket, which is what "a sample" means; vanilla's own bucket volume, not a balance number. */
    public static final int SAMPLE_CAPACITY = 1000;

    private static final String TAG_SAMPLE = "sample";
    private static final String TAG_ENERGY = "energy";
    private static final String TAG_OVERDRIVE = "overdrive";
    private static final String TAG_CORE = "core";

    /** Which core claimed this tank, if any -- {@link SmelteryControllerBlockEntity}'s scan sets it (#972). */
    @Nullable
    private BlockPos corePos;

    private int energy;
    private boolean overdrive;

    private final FluidTank sample = new FluidTank(SAMPLE_CAPACITY, this::isValidSample) {
        @Override
        protected void onContentsChanged() {
            EnergizedTankBlockEntity.this.changed();
        }
    };

    private final IEnergyStorage buffer = new Buffer();

    public EnergizedTankBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.ENERGIZED_TANK.get(), pos, state);
    }

    /** The fuel sample, filled and emptied like any other tank (a bucket, a pipe, a faucet). */
    public FluidTank sample() {
        return sample;
    }

    /** The Forge Energy buffer, which is also what {@link Capabilities.EnergyStorage#BLOCK} hands out. */
    public IEnergyStorage buffer() {
        return buffer;
    }

    /** Whether the overdrive button is pressed. Saved with this block entity, so it survives a reload. */
    public boolean overdrive() {
        return overdrive;
    }

    /** @see SearedTankBlockEntity#core() */
    @Nullable
    public BlockPos core() {
        return corePos;
    }

    /** @see SearedTankBlockEntity#setCore(BlockPos) */
    public void setCore(BlockPos corePos) {
        if (!corePos.equals(this.corePos)) {
            this.corePos = corePos;
            setChanged();
        }
    }

    /**
     * The temperature this tank heats its smeltery to: its fuel sample's own working heat, or
     * {@code 0} when it holds no valid sample, when it is empty, or when the toggle is off.
     */
    public int temperature() {
        if (level == null || sample.isEmpty() || !ForgeweaveConfig.enabled(ForgeweaveConfig.ENERGIZED_TANK)) {
            return 0;
        }
        return SmelteryFuel.find(level.registryAccess(), sample.getFluid().getFluid())
                .map(SmelteryFuel::temperature)
                .orElse(0);
    }

    /** What one melt tick costs this tank right now, overdrive included. */
    public int costPerMeltTick() {
        return EnergizedHeat.costPerMeltTick(temperature(),
                ForgeweaveConfig.energizedTankRfPerMeltTickBase(),
                ForgeweaveConfig.energizedTankTemperatureDivisor(),
                overdrive, ForgeweaveConfig.energizedTankOverdriveCost());
    }

    /** This tank as the smeltery's hottest-tank selection sees it -- see {@link EnergizedHeat#pick}. */
    public EnergizedHeat.Source asHeatSource() {
        return new EnergizedHeat.Source(temperature(), costPerMeltTick(), energy);
    }

    /**
     * Spends one melt tick's energy. Called by {@link SmelteryControllerBlockEntity#meltTick()} only
     * on a tick that actually heated something, and only on the one tank that won the selection --
     * so a cooler tank standing in the same wall spends nothing.
     */
    public void payMeltTick() {
        int cost = costPerMeltTick();
        if (cost <= 0 || energy < cost) {
            return;
        }
        energy -= cost;
        changed();
    }

    /**
     * Presses the overdrive button, returning the new state. Mirrored onto
     * {@link EnergizedTankBlock#OVERDRIVE} so the block shows which way the button sits; this field
     * stays the authority, the block state is the view of it.
     */
    public boolean toggleOverdrive() {
        overdrive = !overdrive;
        changed();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(EnergizedTankBlock.OVERDRIVE)) {
                level.setBlock(worldPosition, state.setValue(EnergizedTankBlock.OVERDRIVE, overdrive),
                        Block.UPDATE_ALL);
            }
        }
        return overdrive;
    }

    /** Whether {@code stack} is a fluid the smeltery can burn, i.e. whether it is a valid sample. */
    private boolean isValidSample(FluidStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        return SmelteryFuel.find(level.registryAccess(), stack.getFluid()).isPresent();
    }

    /**
     * Marks dirty, syncs, and wakes the smeltery whose melt may have stopped for want of heat --
     * {@link SearedTankBlockEntity}'s own arrangement for a wall tank being refilled (#97). Every
     * change a player or a cable can make routes through here, so a generator coming online restarts
     * a stalled melt without anything polling.
     */
    private void changed() {
        setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        if (corePos != null && level.getBlockEntity(corePos) instanceof TankOwner core) {
            core.armMeltTick();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_SAMPLE, sample.writeToNBT(registries, new CompoundTag()));
        tag.putInt(TAG_ENERGY, energy);
        tag.putBoolean(TAG_OVERDRIVE, overdrive);
        if (corePos != null) {
            tag.put(TAG_CORE, NbtUtils.writeBlockPos(corePos));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sample.readFromNBT(registries, tag.getCompound(TAG_SAMPLE));
        energy = tag.getInt(TAG_ENERGY);
        overdrive = tag.getBoolean(TAG_OVERDRIVE);
        corePos = tag.contains(TAG_CORE) ? NbtUtils.readBlockPos(tag, TAG_CORE).orElse(null) : null;
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

    /** Wires the energy and fluid capabilities; called from {@code Forgeweave}'s constructor. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ForgeweaveBlockEntities.ENERGIZED_TANK.get(),
                (blockEntity, side) -> blockEntity.buffer);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ForgeweaveBlockEntities.ENERGIZED_TANK.get(),
                (blockEntity, side) -> blockEntity.sample);
    }

    /**
     * The buffer. A handful of lines rather than NeoForge's own {@code EnergyStorage}, because that
     * class fixes its capacity at construction and D-M8-8 wants every number to be a live config
     * value: this reads {@link ForgeweaveConfig#energizedTankBuffer()} on every query, so lowering
     * the option takes effect on a tank already in the ground instead of waiting for a reload.
     *
     * <p>Energy only ever leaves through {@link #payMeltTick()}. {@link #canExtract()} is false, so
     * a cable cannot pull a smeltery's heat back out into the grid that fed it.
     */
    private final class Buffer implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int accepted = Math.min(toReceive, getMaxEnergyStored() - energy);
            if (accepted <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += accepted;
                changed();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            // A buffer filled before the option was lowered reads as full rather than over-full.
            return Math.min(energy, getMaxEnergyStored());
        }

        @Override
        public int getMaxEnergyStored() {
            return ForgeweaveConfig.energizedTankBuffer();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
