package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;

import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.ModifierWorktableMenu;
import dev.gkissel.forgeweave.modifier.Worktable;

/**
 * JEI's [+] button for a Modifier Worktable function (issue #1057): it fills the first reagent slot,
 * which is the only slot the recipe has a cost for. The tool is the player's own choice and the
 * second reagent slot is the sorting direction rather than a second cost, so neither is a transfer
 * target.
 *
 * <p>A custom {@link IRecipeTransferInfo} rather than the basic four-int registration, for
 * {@link CraftingStationTransferInfo}'s reason: the side-inventory panel's slot count varies per
 * placement, so the inventory range cannot be a fixed pair of numbers.
 */
final class WorktableTransferInfo implements IRecipeTransferInfo<ModifierWorktableMenu, WorktableDisplay> {
    @Override
    public Class<ModifierWorktableMenu> getContainerClass() {
        return ModifierWorktableMenu.class;
    }

    @Override
    public Optional<MenuType<ModifierWorktableMenu>> getMenuType() {
        return Optional.of(ForgeweaveMenus.MODIFIER_WORKTABLE.get());
    }

    @Override
    public RecipeType<WorktableDisplay> getRecipeType() {
        return WorktableCategory.TYPE;
    }

    @Override
    public boolean canHandle(ModifierWorktableMenu container, WorktableDisplay recipe) {
        return true;
    }

    @Override
    public List<Slot> getRecipeSlots(ModifierWorktableMenu container, WorktableDisplay recipe) {
        return List.of(container.getSlot(Worktable.INPUT_START));
    }

    @Override
    public List<Slot> getInventorySlots(ModifierWorktableMenu container, WorktableDisplay recipe) {
        List<Slot> slots = new ArrayList<>();
        for (int i = ModifierWorktableMenu.RESULT_SLOT + 1; i < container.slots.size(); i++) {
            slots.add(container.getSlot(i));
        }
        return slots;
    }
}
