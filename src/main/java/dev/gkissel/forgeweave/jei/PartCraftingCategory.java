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

/** Part Builder recipes: pattern + material items -> part. Icon is the Part Builder block item. */
final class PartCraftingCategory implements IRecipeCategory<PartCraftingRecipe> {
    static final RecipeType<PartCraftingRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "part_crafting", PartCraftingRecipe.class);

    private static final int WIDTH = 64;
    private static final int HEIGHT = 38;

    private final IDrawable icon;
    private final IDrawable arrow;

    PartCraftingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.PART_BUILDER.get()));
        arrow = helper.getRecipeArrow();
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
        builder.addInputSlot(0, 0).addItemStack(recipe.pattern());
        builder.addInputSlot(0, 20).addItemStack(recipe.material());
        builder.addOutputSlot(WIDTH - 18, (HEIGHT - 18) / 2).addItemStack(recipe.result());
    }

    @Override
    public void draw(PartCraftingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        arrow.draw(guiGraphics, 22, (HEIGHT - arrow.getHeight()) / 2);
    }
}
