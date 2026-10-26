package dev.gkissel.forgeweave.emi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;

/**
 * EMI's fill button for a station whose recipe slots never move (issue #971): the Crafting Station's
 * grid, the Part Builder's pattern and material, the Modifier Worktable's first reagent. The JEI
 * plugin's matching transfer infos, in EMI's terms: a recipe's inputs go into {@code craftingSlots}
 * in order, sourced from those same slots plus every slot from {@code firstInventorySlot} to {@code
 * inventoryEnd}, which takes in a station's side inventory as well as the player's.
 */
final class SlotRangeRecipeHandler<T extends AbstractContainerMenu> implements StandardRecipeHandler<T> {
    private final Predicate<EmiRecipe> supports;
    private final int[] craftingSlots;
    private final int firstInventorySlot;
    private final ToIntFunction<T> inventoryEnd;

    SlotRangeRecipeHandler(Predicate<EmiRecipe> supports, int[] craftingSlots, int firstInventorySlot,
            ToIntFunction<T> inventoryEnd) {
        this.supports = supports;
        this.craftingSlots = craftingSlots.clone();
        this.firstInventorySlot = firstInventorySlot;
        this.inventoryEnd = inventoryEnd;
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return supports.test(recipe);
    }

    @Override
    public List<Slot> getCraftingSlots(T menu) {
        List<Slot> slots = new ArrayList<>(craftingSlots.length);
        for (int index : craftingSlots) {
            slots.add(menu.getSlot(index));
        }
        return slots;
    }

    @Override
    public List<Slot> getInputSources(T menu) {
        List<Slot> slots = getCraftingSlots(menu);
        for (int i = firstInventorySlot; i < inventoryEnd.applyAsInt(menu); i++) {
            slots.add(menu.getSlot(i));
        }
        return slots;
    }
}
