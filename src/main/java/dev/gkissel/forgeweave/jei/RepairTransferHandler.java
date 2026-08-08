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
 * JEI's [+] button for Tool Repair recipes into the Tool Station (issue #79's JEI parity fix):
 * same shape as {@link AssemblyTransferHandler} -- the head/binding/handle slots only sit and
 * accept repair items when the repair tab is selected, so the tab always has to be forced there
 * first, through the same {@link ToolStationMenu#clickMenuButton} path the sidebar buttons use.
 *
 * <p>Unlike assembly, the repair tab is a single fixed destination rather than one of several
 * (there is no "wrong" repair recipe to pick a tab for), so this always selects {@link
 * ToolStationTabs#REPAIR} rather than resolving a tab per recipe.
 */
final class RepairTransferHandler implements IRecipeTransferInfo<ToolStationMenu, RepairRecipe>,
        IRecipeTransferHandler<ToolStationMenu, RepairRecipe> {
    private final IRecipeTransferHandler<ToolStationMenu, RepairRecipe> delegate;

    RepairTransferHandler(IRecipeTransferHandlerHelper helper) {
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
    public RecipeType<RepairRecipe> getRecipeType() {
        return RepairCategory.TYPE;
    }

    @Override
    public boolean canHandle(ToolStationMenu container, RepairRecipe recipe) {
        return true;
    }

    /** {@link RepairRecipe} only models one repair item (the material's cheapest repair stack), so only the first item slot is filled. */
    @Override
    public List<Slot> getRecipeSlots(ToolStationMenu container, RepairRecipe recipe) {
        return List.of(container.getSlot(ToolStationMenu.HEAD_SLOT), container.getSlot(ToolStationMenu.BINDING_SLOT));
    }

    @Override
    public List<Slot> getInventorySlots(ToolStationMenu container, RepairRecipe recipe) {
        List<Slot> slots = new ArrayList<>();
        for (int i = ToolStationMenu.CONTAINER_SLOTS; i < container.slots.size(); i++) {
            slots.add(container.getSlot(i));
        }
        return slots;
    }

    @Override
    public IRecipeTransferError transferRecipe(ToolStationMenu container, RepairRecipe recipe,
            IRecipeSlotsView recipeSlotsView, Player player, boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            selectRepairTab(container);
        }
        return delegate.transferRecipe(container, recipe, recipeSlotsView, player, maxTransfer, doTransfer);
    }

    /** Clicks the repair tab through the same path {@code ToolStationScreen}'s sidebar buttons use. */
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
