package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/** Tool Station assembly recipes: one part slot per part -> tool. Icon is the Tool Station block item. */
final class AssemblyCategory implements IRecipeCategory<AssemblyRecipe> {
    static final RecipeType<AssemblyRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "tool_assembly", AssemblyRecipe.class);

    private static final int WIDTH = 64;
    private static final int SLOT_PITCH = 20;
    /** Tall enough for the longest part list in the roster (four parts, the Tool Forge tier). */
    private static final int HEIGHT = 4 * SLOT_PITCH - 2;

    private final IDrawable icon;
    private final IDrawable arrow;

    AssemblyCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.TOOL_STATION.get()));
        arrow = helper.getRecipeArrow();
    }

    @Override
    public RecipeType<AssemblyRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.forgeweave.tool_assembly");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AssemblyRecipe recipe, IFocusGroup focuses) {
        // One slot per part, stacked in the station's own slot order -- two for M3's two-part
        // weapons, four for the Tool Forge tier (issue #155), so the column grows with the tool.
        for (int slot = 0; slot < recipe.parts().size(); slot++) {
            builder.addInputSlot(0, slot * SLOT_PITCH).addItemStacks(recipe.parts().get(slot));
        }
        builder.addOutputSlot(WIDTH - 18, (HEIGHT - 18) / 2).addItemStack(recipe.result());
    }

    @Override
    public void draw(AssemblyRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        arrow.draw(guiGraphics, 2 * SLOT_PITCH + 2, (HEIGHT - arrow.getHeight()) / 2);
    }
}
