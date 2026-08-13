package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;

import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.menu.ToolStationTabs;

/**
 * JEI's [+] button for embossing recipes into the Tool Station (docs/SCOPE.md M3 issue #165),
 * following {@link ModifierApplicationTransferHandler}'s shape: embossing lives on the same repair
 * tab, so this always forces that tab first. Unlike modifier application, embossing does have a
 * recipe-specific input in the tool's own free slots -- the donor part -- so every declared input
 * slot is filled, donor first: {@link EmbossingCategory} lays the donor at the station's binding
 * slot and the (at most four) reagents after it, the same order {@code
 * menu.ToolAssemblyRecipes#resolveEmbossing} reads the station's free slots in, so the donor always
 * lands where {@code modifier.Embossing#resolve} looks for it first.
 */
final class EmbossingTransferHandler implements IRecipeTransferInfo<ToolStationMenu, EmbossingDisplay>,
        IRecipeTransferHandler<ToolStationMenu, EmbossingDisplay> {
    private static final int[] FREE_SLOTS = {
            ToolStationMenu.BINDING_SLOT, ToolStationMenu.HANDLE_SLOT,
            ToolStationMenu.EXTRA_SLOT_1, ToolStationMenu.EXTRA_SLOT_2, ToolStationMenu.EXTRA_SLOT_3};

    private final IRecipeTransferHandler<ToolStationMenu, EmbossingDisplay> delegate;

    EmbossingTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.delegate = helper.createUnregisteredRecipeTransferHandler(this);
    }

    @Override
    public Class<ToolStationMenu> getContainerClass() {
        return ToolStationMenu.class;
    }

    @Override
    public Optional<MenuType<ToolStationMenu>> getMenuType() {
        return Optional.of(ForgeweaveMenus.TOOL_STATION.get());
    }

    @Override
    public RecipeType<EmbossingDisplay> getRecipeType() {
        return EmbossingCategory.TYPE;
    }

    @Override
    public boolean canHandle(ToolStationMenu container, EmbossingDisplay recipe) {
        return true;
    }

    /** Donor first, then one slot per reagent -- {@link EmbossingCategory#setRecipe}'s own order. */
    @Override
    public List<Slot> getRecipeSlots(ToolStationMenu container, EmbossingDisplay recipe) {
        List<Slot> slots = new ArrayList<>(1 + recipe.reagents().size());
        for (int i = 0; i < 1 + recipe.reagents().size(); i++) {
            slots.add(container.getSlot(FREE_SLOTS[i]));
        }
        return slots;
    }

    @Override
    public List<Slot> getInventorySlots(ToolStationMenu container, EmbossingDisplay recipe) {
        List<Slot> slots = new ArrayList<>();
        for (int i = ToolStationMenu.CONTAINER_SLOTS; i < container.slots.size(); i++) {
            slots.add(container.getSlot(i));
        }
        return slots;
    }

    @Override
    public IRecipeTransferError transferRecipe(ToolStationMenu container, EmbossingDisplay recipe,
            IRecipeSlotsView recipeSlotsView, Player player, boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            selectRepairTab(container);
        }
        return delegate.transferRecipe(container, recipe, recipeSlotsView, player, maxTransfer, doTransfer);
    }

    /** Clicks the repair/modify tab through the same path {@code ToolStationScreen}'s sidebar buttons use. */
    private static void selectRepairTab(ToolStationMenu container) {
        if (container.getSelectedTab() == ToolStationTabs.REPAIR) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (container.clickMenuButton(minecraft.player, ToolStationTabs.REPAIR)) {
            minecraft.gameMode.handleInventoryButtonClick(container.containerId, ToolStationTabs.REPAIR);
        }
    }
}
