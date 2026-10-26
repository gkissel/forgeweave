package dev.gkissel.forgeweave.emi;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;

import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.menu.ToolStationTabs;

/**
 * EMI's fill button for the Tool Station and Tool Forge (issue #971): assembly, repair, modifier
 * application and embossing. It does what the JEI plugin's four Tool Station transfer handlers do
 * between them. The station's slots move and change what they accept with the selected tab, so a
 * fill first selects the recipe's tab through the same {@link ToolStationMenu#clickMenuButton} path
 * the sidebar buttons use, then places the inputs:
 *
 * <ul>
 *   <li>assembly: one part per slot from the first, on the tool's own tab;
 *   <li>repair: the tool and the repair item in the head and binding slots, on the repair tab;
 *   <li>modifier application and embossing: the reagents (the donor part first, for embossing) in
 *       the free slots from the binding slot on, on the repair tab, which is also the modify tab.
 * </ul>
 *
 * <p>The tab switch only happens on a real fill, never while EMI is checking whether the recipe can
 * be made, for {@code AssemblyTransferHandler}'s reason: switching tabs because a recipe is on screen
 * would move the open station's slots under the player.
 */
final class ToolStationRecipeHandler implements StandardRecipeHandler<ToolStationMenu> {
    /** The station's free slots in the order {@code ToolAssemblyRecipes} reads a modifier or embossing from. */
    private static final int[] FREE_SLOTS = {
            ToolStationMenu.BINDING_SLOT, ToolStationMenu.HANDLE_SLOT,
            ToolStationMenu.EXTRA_SLOT_1, ToolStationMenu.EXTRA_SLOT_2, ToolStationMenu.EXTRA_SLOT_3};

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        EmiRecipeCategory category = recipe.getCategory();
        return recipe instanceof AssemblyEmiRecipe
                || category == ForgeweaveEmiCategories.TOOL_REPAIR
                || category == ForgeweaveEmiCategories.MODIFIER_APPLICATION
                || category == ForgeweaveEmiCategories.EMBOSSING;
    }

    @Override
    public List<Slot> getCraftingSlots(ToolStationMenu menu) {
        List<Slot> slots = new ArrayList<>(ToolStationMenu.INPUT_SLOTS);
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            slots.add(menu.getSlot(i));
        }
        return slots;
    }

    @Override
    public List<Slot> getCraftingSlots(EmiRecipe recipe, ToolStationMenu menu) {
        int count = recipe.getInputs().size();
        List<Slot> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            slots.add(menu.getSlot(slotFor(recipe, i)));
        }
        return slots;
    }

    @Override
    public List<Slot> getInputSources(ToolStationMenu menu) {
        List<Slot> slots = getCraftingSlots(menu);
        for (int i = ToolStationMenu.CONTAINER_SLOTS; i < menu.slots.size(); i++) {
            slots.add(menu.getSlot(i));
        }
        return slots;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<ToolStationMenu> context) {
        if (recipe.getCategory() == ForgeweaveEmiCategories.LARGE_TOOL_ASSEMBLY && !context.getScreenHandler().isForge()) {
            // Large tools and, since issue #1006, the heavy armor set: Tool Forge only (issue #152).
            return false;
        }
        return tabFor(recipe) >= 0 && StandardRecipeHandler.super.canCraft(recipe, context);
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<ToolStationMenu> context) {
        selectTab(context.getScreenHandler(), tabFor(recipe));
        return StandardRecipeHandler.super.craft(recipe, context);
    }

    /** The menu slot the recipe's {@code input}-th input goes in. */
    private static int slotFor(EmiRecipe recipe, int input) {
        if (recipe instanceof AssemblyEmiRecipe) {
            return input;
        }
        if (recipe.getCategory() == ForgeweaveEmiCategories.TOOL_REPAIR) {
            return input == 0 ? ToolStationMenu.HEAD_SLOT : ToolStationMenu.BINDING_SLOT;
        }
        return FREE_SLOTS[input];
    }

    /** The tab a recipe is made on: the tool's own for assembly, the repair tab for everything else. */
    private static int tabFor(EmiRecipe recipe) {
        return recipe instanceof AssemblyEmiRecipe assembly
                ? ToolStationTabs.indexOfTool(assembly.tool())
                : ToolStationTabs.REPAIR;
    }

    /** Clicks {@code tab} through the same path {@code ToolStationScreen}'s sidebar buttons use. */
    private static void selectTab(ToolStationMenu menu, int tab) {
        if (tab < 0 || menu.getSelectedTab() == tab) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (menu.clickMenuButton(minecraft.player, tab)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tab);
        }
    }
}
