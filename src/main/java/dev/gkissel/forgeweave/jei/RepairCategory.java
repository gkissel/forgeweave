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

    private static final int GUTTER = JeiCategoryChrome.GUTTER;
    private static final int WIDTH = 64 + 2 * GUTTER;
    private static final int HEIGHT = 38 + 2 * GUTTER;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable background;

    RepairCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.TOOL_STATION.get()));
        arrow = helper.getRecipeArrow();
        background = JeiCategoryChrome.panel(WIDTH, HEIGHT);
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
        builder.addInputSlot(GUTTER, GUTTER).addItemStacks(recipe.tools());
        builder.addInputSlot(GUTTER, GUTTER + 20).addItemStacks(recipe.repairItems());
        builder.addOutputSlot(WIDTH - 18 - GUTTER, (HEIGHT - 18) / 2).addItemStacks(recipe.repairedTools());
    }

    @Override
    public void draw(RepairRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrow.draw(guiGraphics, GUTTER + 22, (HEIGHT - arrow.getHeight()) / 2);
    }
}
