package dev.gkissel.forgeweave.jei;

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

/** Tool Station repair recipes: damaged tool + head-material repair item -> repaired tool. Icon is the Tool Station block item. */
final class RepairCategory implements IRecipeCategory<RepairRecipe> {
    static final RecipeType<RepairRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "tool_repair", RepairRecipe.class);

    private static final int WIDTH = 64;
    private static final int HEIGHT = 38;

    private final IDrawable icon;
    private final IDrawable arrow;

    RepairCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.TOOL_STATION.get()));
        arrow = helper.getRecipeArrow();
    }

    @Override
    public RecipeType<RepairRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.forgeweave.tool_repair");
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
    public void setRecipe(IRecipeLayoutBuilder builder, RepairRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(0, 0).addItemStacks(recipe.tools());
        builder.addInputSlot(0, 20).addItemStack(recipe.repairItem());
        builder.addOutputSlot(WIDTH - 18, (HEIGHT - 18) / 2).addItemStacks(recipe.repairedTools());
    }

    @Override
    public void draw(RepairRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        arrow.draw(guiGraphics, 22, (HEIGHT - arrow.getHeight()) / 2);
    }
}
