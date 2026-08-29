package dev.gkissel.forgeweave.jei;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection;
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
 * <p>Issue #785 derived this category's background from upstream's own {@code AlloyRecipeCategory}
 * (`~/development/minecraft/references/tinkers-1.20` @ de26560d, MIT -- NOTICE.md). Issue #804
 * finishes the job: every position and size below is now that class's own, including the arrow,
 * which is upstream's animated crop out of the same `alloy.png` rather than JEI's generic one drawn
 * a pixel off centre on top of the arrow already baked into the background.
 *
 * <p>Two upstream details Forgeweave has no data for: upstream fills its fuel tank with the fluid
 * fuels hot enough for the recipe and centres the required temperature above the arrow, both read
 * off {@code MeltingFuel}/{@code AlloyRecipe#getTemperature}. Forgeweave's alloying is a pure
 * ratio ({@code recipe.AlloyRecipe} carries no temperature and Forgeweave has no fuel-fluid
 * registry), so the fuel tank gets JEI's own flame icon centred in it and the temperature line is
 * left out.
 */
final class AlloyingCategory implements IRecipeCategory<AlloyRecipe> {
    static final RecipeType<AlloyRecipe> TYPE = RecipeType.create(Forgeweave.MODID, "alloying", AlloyRecipe.class);

    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.ALLOYING;
    private static final int WIDTH = PANEL.width();
    private static final int HEIGHT = PANEL.height();

    /** ponytail: M2 ships exactly two-input alloys; a modpack recipe with more only shows its first three. */
    private static final int MAX_DISPLAYED_INPUTS = 3;
    /** Upstream's input zone -- `drawVariableFluids(builder, INPUT, 19, 11, 48, 32, ...)`. */
    private static final int INPUT_X = 19;
    private static final int INPUT_Y = 11;
    private static final int INPUT_TOTAL_WIDTH = 48;
    private static final int TANK_HEIGHT = 32;
    /** Upstream's own output slot -- `builder.addSlot(OUTPUT, 137, 11).setFluidRenderer(maxAmount, false, 16, 32)`. */
    private static final int OUTPUT_X = 137;
    private static final int OUTPUT_Y = 11;
    private static final int OUTPUT_WIDTH = 16;
    /** Upstream's own arrow -- `drawableBuilder(alloy.png, 172, 0, 24, 17).buildAnimated(200, LEFT, false)`, drawn at (90,21). */
    private static final int ARROW_U = 172;
    private static final int ARROW_V = 0;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_TICKS = 200;
    private static final int ARROW_X = 90;
    private static final int ARROW_Y = 21;
    /** Upstream's render-only fuel tank -- `builder.addSlot(RENDER_ONLY, 94, 43).setFluidRenderer(1, false, 16, 16)`. */
    private static final int FUEL_X = 94;
    private static final int FUEL_Y = 43;
    private static final int FUEL_SIZE = 16;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable flame;
    private final IDrawable background;

    AlloyingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.STANDARD_CORE.get()));
        arrow = helper.drawableBuilder(PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT)
                .buildAnimated(ARROW_TICKS, StartDirection.LEFT, false);
        flame = helper.getRecipeFlameFilled();
        background = JeiCategoryChrome.panel(helper, PANEL);
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
        int slotWidth = INPUT_TOTAL_WIDTH / shown;
        for (int i = 0; i < shown; i++) {
            FluidStack input = recipe.inputs().get(i);
            // Upstream's own drawVariableFluids: every slot but the last is exactly totalWidth/count
            // wide; the last absorbs the integer-division remainder so the row's total width is exact.
            boolean last = i == shown - 1;
            int width = last ? INPUT_TOTAL_WIDTH - slotWidth * (shown - 1) : slotWidth;
            builder.addInputSlot(INPUT_X + i * slotWidth, INPUT_Y)
                    .setFluidRenderer(input.getAmount(), false, width, TANK_HEIGHT)
                    .addFluidStack(input.getFluid(), input.getAmount());
        }

        FluidStack result = recipe.result();
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y)
                .setFluidRenderer(result.getAmount(), false, OUTPUT_WIDTH, TANK_HEIGHT)
                .addFluidStack(result.getFluid(), result.getAmount())
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.category.forgeweave.alloying.ratio_note")));
    }

    @Override
    public void draw(AlloyRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);
        // Centred in upstream's own 16x16 fuel tank, in place of the fuel fluids Forgeweave has none of.
        flame.draw(guiGraphics,
                FUEL_X + (FUEL_SIZE - flame.getWidth()) / 2,
                FUEL_Y + (FUEL_SIZE - flame.getHeight()) / 2);
    }
}
