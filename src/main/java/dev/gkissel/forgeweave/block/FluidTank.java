package dev.gkissel.forgeweave.block;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One fluid up to a capacity, with an optional validator: what NeoForge's {@code FluidTank} was before
 * 26.3 removed it, as a {@link TransferBridge.FluidStore}. Saves the way that class did, the fluid under
 * {@code "Fluid"} and nothing at all when empty, so every tank saved since 1.21.1 reads back unchanged.
 *
 * <p>{@link #onContentsChanged} runs after a fill or drain that moved something, and after a transaction
 * that moved something is rolled back. {@link #setFluid} and {@link #setCapacity} run no hook.
 */
public class FluidTank extends TransferBridge.FluidStore implements ValueIOSerializable {
    private static final String TAG_FLUID = "Fluid";

    private final Predicate<FluidStack> validator;
    private final TransferBridge.State<FluidStack> state = TransferBridge.tankState(this, this::onContentsChanged);
    private FluidStack fluid = FluidStack.EMPTY;
    private int capacity;

    public FluidTank(int capacity) {
        this(capacity, stack -> true);
    }

    public FluidTank(int capacity, Predicate<FluidStack> validator) {
        this.capacity = capacity;
        this.validator = validator;
    }

    @Nullable
    @Override
    protected TransferBridge.State<?> state() {
        return state;
    }

    protected void onContentsChanged() {}

    public FluidStack getFluid() {
        return fluid;
    }

    public void setFluid(FluidStack fluid) {
        this.fluid = fluid;
    }

    public int getFluidAmount() {
        return fluid.getAmount();
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public boolean isEmpty() {
        return fluid.isEmpty();
    }

    public int getSpace() {
        return Math.max(0, capacity - fluid.getAmount());
    }

    public boolean isFluidValid(FluidStack stack) {
        return validator.test(stack);
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return fluid;
    }

    @Override
    public int getTankCapacity(int tank) {
        return capacity;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, boolean simulate) {
        if (resource.isEmpty() || !isFluidValid(resource)
                || (!fluid.isEmpty() && !FluidStack.isSameFluidSameComponents(fluid, resource))) {
            return 0;
        }
        int filled = Math.min(capacity - fluid.getAmount(), resource.getAmount());
        if (filled <= 0) {
            return 0;
        }
        if (!simulate) {
            if (fluid.isEmpty()) {
                fluid = resource.copyWithAmount(filled);
            } else {
                fluid.grow(filled);
            }
            onContentsChanged();
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, boolean simulate) {
        if (resource.isEmpty() || !FluidStack.isSameFluidSameComponents(resource, fluid)) {
            return FluidStack.EMPTY;
        }
        return drain(resource.getAmount(), simulate);
    }

    @Override
    public FluidStack drain(int maxDrain, boolean simulate) {
        int drained = Math.min(maxDrain, fluid.getAmount());
        FluidStack stack = fluid.copyWithAmount(drained);
        if (!simulate && drained > 0) {
            fluid.shrink(drained);
            onContentsChanged();
        }
        return stack;
    }

    @Override
    public void serialize(ValueOutput output) {
        if (!fluid.isEmpty()) {
            output.store(TAG_FLUID, FluidStack.CODEC, fluid);
        }
    }

    @Override
    public void deserialize(ValueInput input) {
        fluid = input.read(TAG_FLUID, FluidStack.CODEC).orElse(FluidStack.EMPTY);
    }
}
