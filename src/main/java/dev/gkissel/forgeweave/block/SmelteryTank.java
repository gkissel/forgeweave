package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

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
 * <p>Issue #95 shipped the core with a single-fluid {@link
 * net.neoforged.neoforge.fluids.capability.templates.FluidTank} as a placeholder, which cannot
 * express any of that -- a smeltery holding iron would have refused to accept copper at all. This
 * replaces it, keeping the {@link IFluidHandler} surface {@link SearedDrainBlockEntity} and the
 * melting work (#96) use unchanged: {@link #fill} still returns the amount actually accepted (0 when
 * there is no room), and {@link #getCapacity()}/{@link #getFluidAmount()} still describe the whole
 * store, which is what #96 gates melting on.
 *
 * <p>{@code onChanged} fires on every content or order change so the block entity can mark itself
 * dirty and push a block update; the GUI reads the client-side copy of the block entity rather than
 * a menu payload of its own, exactly as upstream's smeltery screen reads its tile.
 */
public class SmelteryTank implements IFluidHandler {
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

    /* IFluidHandler */

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
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        int usable = Math.min(capacity - getFluidAmount(), resource.getAmount());
        if (usable <= 0) {
            return 0;
        }
        if (action.simulate()) {
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
    public FluidStack drain(int maxDrain, FluidAction action) {
        return fluids.isEmpty() ? FluidStack.EMPTY : drain(getFluid().copyWithAmount(maxDrain), action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        ListIterator<FluidStack> iterator = fluids.listIterator();
        while (iterator.hasNext()) {
            FluidStack fluid = iterator.next();
            if (!FluidStack.isSameFluidSameComponents(fluid, resource)) {
                continue;
            }
            int drainable = Math.min(resource.getAmount(), fluid.getAmount());
            if (action.execute()) {
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

    /* Saving and loading. Capacity is not saved: the block entity recomputes it from the interior. */

    public CompoundTag writeToNBT(HolderLookup.Provider registries, CompoundTag tag) {
        ListTag list = new ListTag();
        for (FluidStack fluid : fluids) {
            list.add(fluid.save(registries));
        }
        tag.put(TAG_FLUIDS, list);
        return tag;
    }

    public void readFromNBT(HolderLookup.Provider registries, CompoundTag tag) {
        fluids.clear();
        if (tag.contains(TAG_FLUIDS, Tag.TAG_LIST)) {
            for (Tag entry : tag.getList(TAG_FLUIDS, Tag.TAG_COMPOUND)) {
                FluidStack.parse(registries, entry).filter(fluid -> !fluid.isEmpty()).ifPresent(fluids::add);
            }
            return;
        }
        readLegacySingleFluid(registries, tag);
    }

    /**
     * Reads the single-fluid shape issue #95 saved before this class existed.
     *
     * <p>#95 stored the tank as a NeoForge {@link
     * net.neoforged.neoforge.fluids.capability.templates.FluidTank}, whose {@code writeToNBT} puts
     * one fluid under {@code "Fluid"} rather than a list. Every world saved on master before #101
     * has that shape, and without this the smeltery silently comes back empty -- a full 9x9 of
     * molten metal quietly deleted on upgrade, which no error would ever have surfaced.
     *
     * <p>Read-only compatibility: the next save writes the list form, so this converts on load and
     * costs nothing afterwards.
     */
    private void readLegacySingleFluid(HolderLookup.Provider registries, CompoundTag tag) {
        FluidStack legacy = FluidStack.parseOptional(registries, tag.getCompound(LEGACY_TAG_FLUID));
        if (!legacy.isEmpty()) {
            fluids.add(legacy);
        }
    }
}
