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

/**
 * Part Builder recipes: pattern + material input -> part + shard change (issue #45). Mirrors
 * {@code menu.PartBuilderMenu}'s four slots (pattern, material, output, change) as a 2x2 grid; the
 * material and change slots cycle together through every crafting-item option the material accepts.
 * Icon is the Part Builder block item.
 */
final class PartCraftingCategory implements IRecipeCategory<PartCraftingRecipe> {
    static final RecipeType<PartCraftingRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "part_crafting", PartCraftingRecipe.class);

    private static final int GUTTER = JeiCategoryChrome.GUTTER;
    private static final int WIDTH = 64 + 2 * GUTTER;
    private static final int HEIGHT = 38 + 2 * GUTTER;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable background;

    PartCraftingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.PART_BUILDER.get()));
        arrow = helper.getRecipeArrow();
        background = JeiCategoryChrome.panel(WIDTH, HEIGHT);
    }

    @Override
    public RecipeType<PartCraftingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.forgeweave.part_crafting");
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
    public void setRecipe(IRecipeLayoutBuilder builder, PartCraftingRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(GUTTER, GUTTER).addItemStack(recipe.pattern());
        builder.addInputSlot(GUTTER, GUTTER + 20).addItemStacks(recipe.materialInputs());
        builder.addOutputSlot(WIDTH - 18 - GUTTER, GUTTER).addItemStack(recipe.result());
        builder.addOutputSlot(WIDTH - 18 - GUTTER, GUTTER + 20).addItemStacks(recipe.changeOutputs());
    }

    @Override
    public void draw(PartCraftingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrow.draw(guiGraphics, GUTTER + 22, (HEIGHT - arrow.getHeight()) / 2);
    }
}
