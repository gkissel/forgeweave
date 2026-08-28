package dev.gkissel.forgeweave.jei;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;

/**
 * Smeltery alloying recipes: two or more molten metals combine into an alloy while the tank holds
 * enough of each (docs/SCOPE.md M2 issue #98, JEI category per issue #109). Icon is the Standard
 * Core block item; like {@link MeltingCategory}, alloying happens automatically inside the tank
 * rather than at a station with input slots, so it gets no recipe-click transfer button either.
 *
 * <p>Reuses {@code recipe.AlloyRecipe} directly as the JEI recipe type instead of a display-only
 * wrapper record: its {@code inputs()}/{@code result()} are already exactly the ratio the smeltery
 * itself reads (that record's own javadoc warning -- "the amounts are a ratio, not a batch size" --
 * applies unchanged here), so a wrapper would only restate the same two fields.
 */
final class AlloyingCategory implements IRecipeCategory<AlloyRecipe> {
    static final RecipeType<AlloyRecipe> TYPE = RecipeType.create(Forgeweave.MODID, "alloying", AlloyRecipe.class);

    private static final int GUTTER = JeiCategoryChrome.GUTTER;
    private static final int WIDTH = 90 + 2 * GUTTER;
    private static final int HEIGHT = 60 + 2 * GUTTER;
    private static final int TANK_SIZE = 16;
    private static final int ROW_HEIGHT = 20;
    private static final int ARROW_X = 36 + GUTTER;
    private static final int FLAME_X = GUTTER + TANK_SIZE + 4;

    /** ponytail: M2 ships exactly two-input alloys; a modpack recipe with more only shows its first three. */
    private static final int MAX_DISPLAYED_INPUTS = 3;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable flame;
    private final IDrawable background;

    AlloyingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.STANDARD_CORE.get()));
        arrow = helper.getRecipeArrow();
        flame = helper.getRecipeFlameFilled();
        background = JeiCategoryChrome.panel(WIDTH, HEIGHT);
    }

    @Override
    public RecipeType<AlloyRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.forgeweave.alloying");
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
    public void setRecipe(IRecipeLayoutBuilder builder, AlloyRecipe recipe, IFocusGroup focuses) {
        int shown = Math.min(recipe.inputs().size(), MAX_DISPLAYED_INPUTS);
        for (int i = 0; i < shown; i++) {
            FluidStack input = recipe.inputs().get(i);
            builder.addInputSlot(GUTTER, GUTTER + i * ROW_HEIGHT)
                    .setFluidRenderer(input.getAmount(), false, TANK_SIZE, TANK_SIZE)
                    .addFluidStack(input.getFluid(), input.getAmount());
        }

        FluidStack result = recipe.result();
        builder.addOutputSlot(WIDTH - TANK_SIZE - 2 - GUTTER, (HEIGHT - TANK_SIZE) / 2)
                .setFluidRenderer(result.getAmount(), false, TANK_SIZE, TANK_SIZE)
                .addFluidStack(result.getFluid(), result.getAmount())
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.category.forgeweave.alloying.ratio_note")));
    }

    @Override
    public void draw(AlloyRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        flame.draw(guiGraphics, FLAME_X, (HEIGHT - flame.getHeight()) / 2);
        arrow.draw(guiGraphics, ARROW_X, (HEIGHT - arrow.getHeight()) / 2);
    }
}
