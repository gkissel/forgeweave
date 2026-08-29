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
 * {@code menu.PartBuilderMenu}'s four slots (pattern, material, output, change). Icon is the Part
 * Builder block item.
 *
 * <p>Issue #804: this now draws upstream's own {@code PartBuilderCategory} panel whole --
 * `createDrawable(tinker_station.png, 0, 117, 121, 46)` (`~/development/minecraft/references/
 * tinkers-1.20` @ de26560d, MIT -- NOTICE.md) -- with its four slots at upstream's own positions.
 * #785 took the same crop but declared the category 72px wide, which cut that panel off mid-arrow
 * and left a stub of the output slot's frame hanging at the row's right edge, and then laid a 2x2
 * grid of its own on top of the four slot frames baked into the art.
 *
 * <p>Deviation: upstream's third slot (46,16) holds the pattern <em>type</em> it renders through a
 * custom {@code PatternIngredientRenderer}, which is why the panel has a parchment baked in behind
 * it. Forgeweave has no separate pattern-type ingredient (its pattern is a real item, in the first
 * slot) and does have a shard-change output upstream lacks, so that slot carries the change instead
 * -- keeping the row's left-to-right reading in slot order rather than parking an output outside the
 * panel. Upstream's two text rows (the material name at (3,2) and the pattern cost at (3,35)) are
 * left blank: Forgeweave's part recipes carry neither a per-material display name for this row nor a
 * material cost, and both are already in the slots' own tooltips.
 */
final class PartCraftingCategory implements IRecipeCategory<PartCraftingRecipe> {
    static final RecipeType<PartCraftingRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "part_crafting", PartCraftingRecipe.class);

    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.PART_CRAFTING;
    private static final int WIDTH = PANEL.width();
    private static final int HEIGHT = PANEL.height();

    /** Upstream's own slots -- `addSlot(INPUT, 4, 16)` pattern, `(25, 16)` material, `(46, 16)` pattern type. */
    private static final int PATTERN_X = 4;
    private static final int MATERIAL_X = 25;
    private static final int CHANGE_X = 46;
    private static final int INPUT_Y = 16;
    /** Upstream's own result slot -- `addSlot(OUTPUT, 96, 15)`. */
    private static final int RESULT_X = 96;
    private static final int RESULT_Y = 15;

    private final IDrawable icon;
    private final IDrawable background;

    PartCraftingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.PART_BUILDER.get()));
        background = JeiCategoryChrome.panel(helper, PANEL);
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
        builder.addInputSlot(PATTERN_X, INPUT_Y).addItemStack(recipe.pattern());
        builder.addInputSlot(MATERIAL_X, INPUT_Y).addItemStacks(recipe.materialInputs());
        builder.addOutputSlot(RESULT_X, RESULT_Y).addItemStack(recipe.result());
        builder.addOutputSlot(CHANGE_X, INPUT_Y).addItemStacks(recipe.changeOutputs());
    }

    @Override
    public void draw(PartCraftingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // The arrow and all four slot frames are baked into upstream's own panel; nothing to overlay.
        background.draw(guiGraphics, 0, 0);
    }
}
