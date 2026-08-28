package dev.gkissel.forgeweave.jei;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 *
 * <p>Issue #785: the background, arrow and fuel-tank icon are derived from upstream's own {@code
 * AlloyRecipeCategory} (`~/development/minecraft/references/tinkers-1.20` @ de26560d, MIT --
 * NOTICE.md), so every coordinate below is upstream's real pixel position rather than a value
 * computed from {@link JeiCategoryChrome#GUTTER}. Upstream lays two-to-many inputs out horizontally
 * across a shared width via {@code drawVariableFluids}; this category instead keeps its own fixed
 * vertical-ish spacing for up to {@link #MAX_DISPLAYED_INPUTS} tanks (M2 ships exactly two), spaced
 * to land inside upstream's own input zone (x=19..90, y=11) rather than reproducing that variable
 * width-splitting algorithm for a fixed, small input count.
 */
final class AlloyingCategory implements IRecipeCategory<AlloyRecipe> {
    static final RecipeType<AlloyRecipe> TYPE = RecipeType.create(Forgeweave.MODID, "alloying", AlloyRecipe.class);

    /** Upstream {@code AlloyRecipeCategory}'s own background: `textures/gui/jei/alloy.png`, (0,0,172,62). */
    static final ResourceLocation BACKGROUND_LOC = JeiCategoryGeometry.ALLOYING.background();
    static final int WIDTH = JeiCategoryGeometry.ALLOYING.width();
    static final int HEIGHT = JeiCategoryGeometry.ALLOYING.height();

    private static final int TANK_SIZE = 16;
    /** ponytail: M2 ships exactly two-input alloys; a modpack recipe with more only shows its first three. */
    private static final int MAX_DISPLAYED_INPUTS = 3;
    /** Upstream's input zone starts at x=19,y=11 and spans to x=67 (`drawVariableFluids` width 48). */
    private static final int[] INPUT_X = {19, 37, 55};
    private static final int INPUT_Y = 11;
    /** Upstream's own output slot position -- `builder.addSlot(OUTPUT, 137, 11)`. */
    private static final int OUTPUT_X = 137;
    private static final int OUTPUT_Y = 11;
    /** Upstream's own arrow x -- `arrow.draw(graphics, 90, 21)`. */
    private static final int ARROW_X = 90;
    /** Upstream's render-only fuel display slot -- `builder.addSlot(RENDER_ONLY, 94, 43, ...)`. */
    private static final int FLAME_X = 94;
    private static final int FLAME_Y = 43;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable flame;
    private final IDrawable background;

    AlloyingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.STANDARD_CORE.get()));
        arrow = helper.getRecipeArrow();
        flame = helper.getRecipeFlameFilled();
        background = helper.createDrawable(BACKGROUND_LOC, 0, 0, WIDTH, HEIGHT);
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
            builder.addInputSlot(INPUT_X[i], INPUT_Y)
                    .setFluidRenderer(input.getAmount(), false, TANK_SIZE, TANK_SIZE)
                    .addFluidStack(input.getFluid(), input.getAmount());
        }

        FluidStack result = recipe.result();
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y)
                .setFluidRenderer(result.getAmount(), false, TANK_SIZE, TANK_SIZE)
                .addFluidStack(result.getFluid(), result.getAmount())
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.category.forgeweave.alloying.ratio_note")));
    }

    @Override
    public void draw(AlloyRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        flame.draw(guiGraphics, FLAME_X, FLAME_Y);
        arrow.draw(guiGraphics, ARROW_X, (HEIGHT - arrow.getHeight()) / 2);
    }
}
