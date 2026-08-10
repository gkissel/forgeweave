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
import dev.gkissel.forgeweave.modifier.ModifierRecipe;

/**
 * JEI's [+] button for modifier application recipes into the Tool Station (docs/SCOPE.md M2 issue
 * #109), following {@link RepairTransferHandler}'s shape: the repair tab is also the modify tab
 * ({@code menu.ToolStationMenu}'s class javadoc), so this always forces that tab first through the
 * same {@link ToolStationMenu#clickMenuButton} path the sidebar buttons use, then fills the reagent.
 *
 * <p>Unlike a repair or assembly transfer, there is no recipe-specific item to put in the head slot
 * ({@link ToolStationMenu#HEAD_SLOT}) -- any assembled tool with a free modifier slot works ({@code
 * modifier.ModifierApplication#resolve}) -- so only the reagent slot is filled; {@link
 * ModifierApplicationCategory}'s tool icon is a {@code RENDER_ONLY} slot for exactly this reason,
 * and {@link #getRecipeSlots} correspondingly returns just the one input the category actually
 * declares.
 */
final class ModifierApplicationTransferHandler implements IRecipeTransferInfo<ToolStationMenu, ModifierRecipe>,
        IRecipeTransferHandler<ToolStationMenu, ModifierRecipe> {
    private final IRecipeTransferHandler<ToolStationMenu, ModifierRecipe> delegate;

    ModifierApplicationTransferHandler(IRecipeTransferHandlerHelper helper) {
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
    public RecipeType<ModifierRecipe> getRecipeType() {
        return ModifierApplicationCategory.TYPE;
    }

    @Override
    public boolean canHandle(ToolStationMenu container, ModifierRecipe recipe) {
        return true;
    }

    /** Only the reagent slot -- the tool slot is never JEI's to fill (see the class javadoc). */
    @Override
    public List<Slot> getRecipeSlots(ToolStationMenu container, ModifierRecipe recipe) {
        return List.of(container.getSlot(ToolStationMenu.BINDING_SLOT));
    }

    @Override
    public List<Slot> getInventorySlots(ToolStationMenu container, ModifierRecipe recipe) {
        List<Slot> slots = new ArrayList<>();
        for (int i = ToolStationMenu.CONTAINER_SLOTS; i < container.slots.size(); i++) {
            slots.add(container.getSlot(i));
        }
        return slots;
    }

    @Override
    public IRecipeTransferError transferRecipe(ToolStationMenu container, ModifierRecipe recipe,
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
