package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;

import dev.gkissel.forgeweave.menu.CraftingStationMenu;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;

/**
 * JEI's [+] button for vanilla crafting recipes into the Crafting Station (docs/SCOPE.md M1 issue
 * #40): the recipe fills the 3x3 grid, and any slot after the station's own (grid + output) is a
 * valid source to pull ingredients from -- including the side-inventory panel, not just the player's
 * own inventory. Mirrors upstream 1.12's {@code CraftingStationRecipeTransferInfo} (NOTICE.md): same
 * "recipe = the grid, inventory = everything after the station's own slots" shape, adapted to JEI's
 * current API (a custom {@link IRecipeTransferInfo} rather than the basic 4-int registration helper,
 * since the side panel's slot count is dynamic per placement -- the basic helper needs a fixed
 * inventory-slot range).
 */
final class CraftingStationTransferInfo implements IRecipeTransferInfo<CraftingStationMenu, RecipeHolder<CraftingRecipe>> {
    @Override
    public Class<CraftingStationMenu> getContainerClass() {
        return CraftingStationMenu.class;
    }

    @Override
    public Optional<MenuType<CraftingStationMenu>> getMenuType() {
        return Optional.of(ForgeweaveMenus.CRAFTING_STATION.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public boolean canHandle(CraftingStationMenu container, RecipeHolder<CraftingRecipe> recipe) {
        return true;
    }

    @Override
    public List<Slot> getRecipeSlots(CraftingStationMenu container, RecipeHolder<CraftingRecipe> recipe) {
        List<Slot> slots = new ArrayList<>(CraftingStationMenu.GRID_SLOTS);
        for (int i = 0; i < CraftingStationMenu.GRID_SLOTS; i++) {
            slots.add(container.getSlot(i));
        }
        return slots;
    }

    @Override
    public List<Slot> getInventorySlots(CraftingStationMenu container, RecipeHolder<CraftingRecipe> recipe) {
        List<Slot> slots = new ArrayList<>();
        for (int i = CraftingStationMenu.CONTAINER_SLOTS; i < container.slots.size(); i++) {
            slots.add(container.getSlot(i));
        }
        return slots;
    }
}
