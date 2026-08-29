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
 * Tool Station repair recipes: damaged tool + head-material repair item -> repaired tool. Icon is
 * the Tool Station block item.
 *
 * <p>Upstream has no repair JEI category of its own -- its Tool Station repair is handled entirely
 * in-GUI, with no recipe view -- so per issue #804's rule for a category upstream lacks, this takes
 * the nearest upstream panel's conventions: {@code SeveringCategory}'s plain "inputs, arrow, one
 * output" row, `createDrawable(tinker_station.png, 0, 78, 100, 38)`
 * (`~/development/minecraft/references/tinkers-1.20` @ de26560d, MIT -- NOTICE.md), with the output
 * at that category's own (76,11). Its input side is blank art -- upstream renders a 32x32 entity
 * there -- so the two input slots get upstream's own 18x18 slot frame drawn under them, the way
 * {@code ToolBuildingCategory} frames its own frameless slots ({@link JeiCategoryChrome#slotFrame}).
 *
 * <p>#785 instead cropped the Part Builder row to 72px, which cut it off mid-arrow and left the
 * output frame's left edge dangling at the row's right border.
 */
final class RepairCategory implements IRecipeCategory<RepairRecipe> {
    static final RecipeType<RepairRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "tool_repair", RepairRecipe.class);

    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.REPAIR;
    private static final int WIDTH = PANEL.width();
    private static final int HEIGHT = PANEL.height();

    /** Aligned on upstream's own output row so the whole line reads across the baked arrow. */
    private static final int TOOL_X = 3;
    private static final int REPAIR_ITEM_X = 25;
    private static final int INPUT_Y = 11;
    /** Upstream {@code SeveringCategory}'s own output slot -- `addSlot(OUTPUT, 76, 11)`. */
    private static final int RESULT_X = 76;
    private static final int RESULT_Y = 11;

    private final IDrawable icon;
    private final IDrawable background;
    private final IDrawable slotFrame;

    RepairCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.TOOL_STATION.get()));
        background = JeiCategoryChrome.panel(helper, PANEL);
        slotFrame = JeiCategoryChrome.slotFrame(helper);
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
        builder.addInputSlot(TOOL_X, INPUT_Y).addItemStacks(recipe.tools());
        builder.addInputSlot(REPAIR_ITEM_X, INPUT_Y).addItemStacks(recipe.repairItems());
        builder.addOutputSlot(RESULT_X, RESULT_Y).addItemStacks(recipe.repairedTools());
    }

    @Override
    public void draw(RepairRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // The arrow and the output slot's frame are baked into the panel; the input side is blank art.
        background.draw(guiGraphics, 0, 0);
        JeiCategoryChrome.drawSlotFrame(slotFrame, guiGraphics, TOOL_X, INPUT_Y);
        JeiCategoryChrome.drawSlotFrame(slotFrame, guiGraphics, REPAIR_ITEM_X, INPUT_Y);
    }
}
