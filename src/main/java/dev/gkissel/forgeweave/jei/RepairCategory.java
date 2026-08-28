package dev.gkissel.forgeweave.jei;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * Tool Station repair recipes: damaged tool + head-material repair item -> repaired tool. Icon is
 * the Tool Station block item.
 *
 * <p>Issue #785: upstream has no repair JEI category of its own (its Tool Station repair is handled
 * entirely in-GUI, no recipe view), so per the maintainer's decision this reuses the closest upstream
 * background -- {@code PartBuilderCategory}'s plain station panel -- via {@link
 * JeiCategoryChrome#stationPanel}, the same crop {@link PartCraftingCategory} uses, so the two read
 * as one family.
 */
final class RepairCategory implements IRecipeCategory<RepairRecipe> {
    static final RecipeType<RepairRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "tool_repair", RepairRecipe.class);

    static final ResourceLocation BACKGROUND_LOC = JeiCategoryGeometry.REPAIR.background();
    private static final int GUTTER = JeiCategoryChrome.GUTTER;
    static final int WIDTH = JeiCategoryGeometry.REPAIR.width();
    static final int HEIGHT = JeiCategoryGeometry.REPAIR.height();

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable background;

    RepairCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.TOOL_STATION.get()));
        arrow = helper.getRecipeArrow();
        background = JeiCategoryChrome.stationPanel(helper, WIDTH, HEIGHT);
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
