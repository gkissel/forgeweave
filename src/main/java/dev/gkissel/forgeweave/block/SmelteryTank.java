package dev.gkissel.forgeweave.block;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A smeltery's molten contents: an <em>ordered list</em> of fluids sharing one capacity, ported from
 * upstream 1.12's {@code SmelteryTank} (NOTICE.md).
 *
 * <p>The order is the feature, not an implementation detail. Index 0 is the bottom of the melt, and
 * the bottom fluid is the one a drain pours and the one the GUI's fluid column draws lowest;
 * everything above it is layered on top in list order. Clicking a fluid in the smeltery GUI (issue
 * #101) moves it to index 0 via {@link #moveToBottom}, which is upstream's {@code moveFluidToBottom}
 * and the only reason a player can choose <em>which</em> metal comes out of a mixed smeltery.
 *
 * <p>Issue #95 shipped the core with a single-fluid {@code
 * net.neoforged.neoforge.fluids.capability.templates.FluidTank} as a placeholder, which cannot
 * express any of that -- a smeltery holding iron would have refused to accept copper at all. This
 * replaces it, keeping the fill/drain surface {@link SearedDrainBlockEntity} and the
 * melting work (#96) use unchanged: {@link #fill} still returns the amount actually accepted (0 when
 * there is no room), and {@link #getCapacity()}/{@link #getFluidAmount()} still describe the whole
 * store, which is what #96 gates melting on.
 *
 * <p>{@code onChanged} fires on every content or order change so the block entity can mark itself
 * dirty and push a block update; the GUI reads the client-side copy of the block entity rather than
 * a menu payload of its own, exactly as upstream's smeltery screen reads its tile.
 */
public class SmelteryTank extends TransferBridge.FluidStore {
    private static final String TAG_FLUIDS = "fluids";
    /** NeoForge {@code FluidTank}'s own key, which issue #95's single-fluid tank saved under. */
    private static final String LEGACY_TAG_FLUID = "Fluid";

    private final List<FluidStack> fluids = new ArrayList<>();
    private final Runnable onChanged;
    private int capacity;

    public SmelteryTank(int capacity, Runnable onChanged) {
        this.capacity = capacity;
        this.onChanged = onChanged;
    }

    /** The melt, bottom first. A read-only view, not a copy -- the GUI reads it every frame. */
    public List<FluidStack> fluids() {
        return Collections.unmodifiableList(fluids);
    }

    /** The bottom fluid -- what a drain pours -- or {@link FluidStack#EMPTY} when the smeltery is dry. */
    public FluidStack getFluid() {
        return fluids.isEmpty() ? FluidStack.EMPTY : fluids.get(0);
    }

    public int getFluidAmount() {
        int total = 0;
        for (FluidStack fluid : fluids) {
            total += fluid.getAmount();
        }
        return total;
    }

    public int getCapacity() {
        return capacity;
    }

    /**
     * Resizes the store to a new interior volume, discarding anything that no longer fits from the
     * <em>top</em> of the melt -- the bottom fluid a player selected is the last thing to be lost.
     */
    public void setCapacity(int capacity) {
        this.capacity = capacity;
        int excess = getFluidAmount() - capacity;
        boolean changed = false;
        for (int i = fluids.size() - 1; i >= 0 && excess > 0; i--) {
            FluidStack fluid = fluids.get(i);
            int removed = Math.min(excess, fluid.getAmount());
            excess -= removed;
            changed = true;
            if (removed >= fluid.getAmount()) {
                fluids.remove(i);
            } else {
                fluid.shrink(removed);
            }
        }
        if (changed) {
            onChanged.run();
        }
    }

    /**
     * Upstream's {@code moveFluidToBottom}: the clicked fluid becomes the drain fluid.
     *
     * @return whether the order actually changed, so a no-op click sends no block update
     */
    public boolean moveToBottom(int index) {
        if (index <= 0 || index >= fluids.size()) {
            return false; // out of range, or already the bottom fluid
        }
        fluids.add(0, fluids.remove(index));
        onChanged.run();
        return true;
    }

    /* Fill and drain (TransferBridge.FluidStore) */

    @Override
    public int getTanks() {
        return Math.max(1, fluids.size());
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank >= 0 && tank < fluids.size() ? fluids.get(tank).copy() : FluidStack.EMPTY;
    }

    /**
     * The whole store's capacity for every index: the fluids share one space, so there is no
     * meaningful per-fluid limit to report (upstream's {@code getTankProperties} says the same thing
     * by handing the first tank all the unused room).
     */
    @Override
    public int getTankCapacity(int tank) {
        return capacity;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    /**
     * Upstream's {@code fill}: a fluid already present grows, a new one is layered on top, and both
     * are capped by the room left across the whole store.
     */
    @Override
    public int fill(FluidStack resource, boolean simulate) {
        if (resource.isEmpty()) {
            return 0;
        }
        int usable = Math.min(capacity - getFluidAmount(), resource.getAmount());
        if (usable <= 0) {
            return 0;
        }
        if (simulate) {
            return usable;
        }
        for (FluidStack fluid : fluids) {
            if (FluidStack.isSameFluidSameComponents(fluid, resource)) {
                fluid.grow(usable);
                onChanged.run();
                return usable;
            }
        }
        fluids.add(resource.copyWithAmount(usable));
        onChanged.run();
        return usable;
    }

    /** Upstream's {@code drain(int)}: whatever is at the bottom is what comes out. */
    @Override
    public FluidStack drain(int maxDrain, boolean simulate) {
        return fluids.isEmpty() ? FluidStack.EMPTY : drain(getFluid().copyWithAmount(maxDrain), simulate);
    }

    @Override
    public FluidStack drain(FluidStack resource, boolean simulate) {
        ListIterator<FluidStack> iterator = fluids.listIterator();
        while (iterator.hasNext()) {
            FluidStack fluid = iterator.next();
            if (!FluidStack.isSameFluidSameComponents(fluid, resource)) {
                continue;
            }
            int drainable = Math.min(resource.getAmount(), fluid.getAmount());
            if (!simulate) {
                fluid.shrink(drainable);
                if (fluid.isEmpty()) {
                    iterator.remove();
                }
                onChanged.run();
            }
            return resource.copyWithAmount(drainable);
        }
        return FluidStack.EMPTY;
    }

    /* Transactions (see TransferBridge): a deep copy of the melt, and putting one back. */

    private final TransferBridge.State<List<FluidStack>> transferState = TransferBridge.State.of(this::snapshot, this::restore);

    /** The melt's one transaction journal; every store over this tank (drain, duct, core) must use it. */
    TransferBridge.State<List<FluidStack>> transferState() {
        return transferState;
    }

    @Override
    protected TransferBridge.State<?> state() {
        return transferState;
    }

    /** This tank as the transfer API sees it: itself. */
    public ResourceHandler<FluidResource> transferHandler() {
        return this;
    }

    List<FluidStack> snapshot() {
        List<FluidStack> copy = new ArrayList<>(fluids.size());
        for (FluidStack fluid : fluids) {
            copy.add(fluid.copy());
        }
        return copy;
    }

    void restore(List<FluidStack> snapshot) {
        fluids.clear();
        fluids.addAll(snapshot);
        onChanged.run();
    }

    /* Saving and loading. Capacity is not saved: the block entity recomputes it from the interior. */

    public void serialize(ValueOutput output) {
        ValueOutput.TypedOutputList<FluidStack> list = output.list(TAG_FLUIDS, FluidStack.CODEC);
        for (FluidStack fluid : fluids) {
            list.add(fluid);
        }
    }

    public void deserialize(ValueInput input) {
        fluids.clear();
        Optional<ValueInput.TypedInputList<FluidStack>> list = input.list(TAG_FLUIDS, FluidStack.CODEC);
        if (list.isPresent()) {
            for (FluidStack fluid : list.get()) {
                if (!fluid.isEmpty()) {
                    fluids.add(fluid);
                }
            }
            return;
        }
        readLegacySingleFluid(input);
    }

    /** {@link #serialize} into a plain compound, the shape 1.21.1 saved. */
    public CompoundTag writeToNBT(HolderLookup.Provider registries, CompoundTag tag) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        serialize(output);
        tag.merge(output.buildResult());
        return tag;
    }

    /** {@link #deserialize} from a plain compound. */
    public void readFromNBT(HolderLookup.Provider registries, CompoundTag tag) {
        deserialize(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
    }

    /**
     * Reads the single-fluid shape issue #95 saved before this class existed.
     *
     * <p>#95 stored the tank as a NeoForge {@code
     * net.neoforged.neoforge.fluids.capability.templates.FluidTank}, whose {@code writeToNBT} puts
     * one fluid under {@code "Fluid"} rather than a list. Every world saved on master before #101
     * has that shape, and without this the smeltery silently comes back empty -- a full 9x9 of
     * molten metal quietly deleted on upgrade, which no error would ever have surfaced.
     *
     * <p>Read-only compatibility: the next save writes the list form, so this converts on load and
     * costs nothing afterwards.
     */
    private void readLegacySingleFluid(ValueInput input) {
        input.read(LEGACY_TAG_FLUID, FluidStack.CODEC).filter(fluid -> !fluid.isEmpty()).ifPresent(fluids::add);
    }
}
