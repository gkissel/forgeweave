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
 * JEI's [+] button for Tool Assembly recipes into the Tool Station (docs/SCOPE.md M1 issue #40's
 * follow-up): unlike {@link CraftingStationTransferInfo}/the Part Builder's basic transfer handler
 * (recipe slots are always at the same menu index), the Tool Station's head/binding/handle slots
 * move to the selected {@link ToolStationTabs.Tab}'s position and only accept that tab's head part
 * ({@link ToolStationMenu#accepts}), so a transfer first has to select the recipe's tool tab through
 * the same {@link ToolStationMenu#clickMenuButton} path the sidebar buttons use -- server-
 * authoritative, same as a real click -- before the ordinary slot-filling can succeed.
 *
 * <p>Implements both {@link IRecipeTransferInfo} (recipe/inventory slot ranges, same "recipe slots =
 * the station's own slots, inventory slots = everything after them including the side inventory"
 * shape {@link CraftingStationTransferInfo} uses) and {@link IRecipeTransferHandler} (the tab
 * selection) in one class: {@link #delegate} is JEI's own basic stack-matching/click-simulation
 * algorithm, built from {@code this} as the {@link IRecipeTransferInfo}, so this class only has to
 * add the one step upstream's own click path didn't need -- there is no upstream equivalent to port
 * (neither TinkersConstruct 1.12's {@code plugin/jei} package nor its own JEI transfer info has a
 * Tool Station entry; the reference `PssbleTrngle/TinkersJEI` cited for {@code AssemblyRecipes} has
 * no local source to derive from either), so this carries no NOTICE.md row.
 *
 * <p>ponytail: the tab switch only runs on the real click ({@code doTransfer}), not on JEI's
 * feasibility-check pass -- switching tabs merely because a recipe is rendered in the JEI overlay
 * would flicker the open station's slots every frame. The trade-off is that hovering the [+] button
 * for a recipe on the wrong tab may show a misleading "can't transfer" hint until it's actually
 * clicked, at which point the tab always switches first and the transfer then succeeds.
 */
final class AssemblyTransferHandler implements IRecipeTransferInfo<ToolStationMenu, AssemblyRecipe>,
        IRecipeTransferHandler<ToolStationMenu, AssemblyRecipe> {
    private final IRecipeTransferHandler<ToolStationMenu, AssemblyRecipe> delegate;

    AssemblyTransferHandler(IRecipeTransferHandlerHelper helper) {
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
    public RecipeType<AssemblyRecipe> getRecipeType() {
        return AssemblyCategory.TYPE;
    }

    @Override
    public boolean canHandle(ToolStationMenu container, AssemblyRecipe recipe) {
        return tabIndexFor(recipe) >= 0;
    }

    @Override
    public List<Slot> getRecipeSlots(ToolStationMenu container, AssemblyRecipe recipe) {
        return List.of(container.getSlot(ToolStationMenu.HEAD_SLOT),
                container.getSlot(ToolStationMenu.BINDING_SLOT), container.getSlot(ToolStationMenu.HANDLE_SLOT));
    }

    @Override
    public List<Slot> getInventorySlots(ToolStationMenu container, AssemblyRecipe recipe) {
        List<Slot> slots = new ArrayList<>();
        for (int i = ToolStationMenu.CONTAINER_SLOTS; i < container.slots.size(); i++) {
            slots.add(container.getSlot(i));
        }
        return slots;
    }

    @Override
    public IRecipeTransferError transferRecipe(ToolStationMenu container, AssemblyRecipe recipe,
            IRecipeSlotsView recipeSlotsView, Player player, boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            selectTab(container, recipe);
        }
        return delegate.transferRecipe(container, recipe, recipeSlotsView, player, maxTransfer, doTransfer);
    }

    /** Clicks the recipe's tool tab through the same path {@code ToolStationScreen}'s sidebar buttons use. */
    private static void selectTab(ToolStationMenu container, AssemblyRecipe recipe) {
        int tabIndex = tabIndexFor(recipe);
        if (tabIndex < 0 || container.getSelectedTab() == tabIndex) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (container.clickMenuButton(minecraft.player, tabIndex)) {
            minecraft.gameMode.handleInventoryButtonClick(container.containerId, tabIndex);
        }
    }

    /** The tab index this recipe's tool belongs to, or -1 if none matches (shouldn't happen -- see {@link AssemblyRecipes}). */
    private static int tabIndexFor(AssemblyRecipe recipe) {
        for (int i = 0; i < ToolStationTabs.TABS.size(); i++) {
            ToolStationTabs.Tab tab = ToolStationTabs.TABS.get(i);
            if (!tab.isRepair() && recipe.result().is(tab.tool().get())) {
                return i;
            }
        }
        return -1;
    }
}
