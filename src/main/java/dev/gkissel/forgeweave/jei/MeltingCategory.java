package dev.gkissel.forgeweave.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.TemperatureText; // #276
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * Smeltery melting recipes: an item melts into a fluid amount at a required temperature
 * (docs/SCOPE.md M2 issue #96, JEI category per issue #109). Icon is the Standard Core block item;
 * melting happens automatically inside the smeltery tank rather than at a station with input slots,
 * so unlike the M1 categories this one gets no recipe-click transfer button (docs/SCOPE.md M1 issue
 * #40's transfer only applies where a menu actually has slots to fill).
 *
 * <p>The amount shown is the recipe's own base value; an ore input's real smeltery yield is
 * multiplied by the core tier (issue #99's {@code SmelteryCore#yieldMultiplier}). That multiplier is
 * a structure property, not a recipe property, so rather than baking a specific multiplier into the
 * displayed number this category explains it via a tooltip on the fluid slot -- shown only for ore
 * inputs, matching docs/SCOPE.md M2's "ingot re-melts 1:1" rule for everything else.
 *
 * <p>Issue #785 derived this category's background from upstream's own {@code
 * AbstractMeltingCategory}/{@code MeltingCategory} (`~/development/minecraft/references/tinkers-1.20`
 * @ de26560d, MIT -- NOTICE.md). Issue #804 finishes the job:
 *
 * <ul>
 *   <li>The output is upstream's real {@code setFluidRenderer(.., 32, 32).setOverlay(tankOverlay, 0,
 *       0)} rather than a flat 16x16 swatch adrift in a mostly-empty tank silhouette. The overlay
 *       crop is already inside the derived `melting.png` (it is the same 256x256 upstream file), so
 *       drawing it needs no new asset and no new NOTICE.md row.
 *   <li>The arrow is upstream's own animated crop drawn at its (56,18), not JEI's generic arrow
 *       drawn vertically centred on top of the arrow the background already has baked in.
 *   <li>The temperature is centred on x=56 the way {@code AbstractMeltingCategory#draw} centres it,
 *       and is the bare number {@link TemperatureText} already produces for the smeltery tooltip.
 *       It used to be drawn left-anchored with an added "Temperature: " label, which ran the string
 *       across the whole row and under the output fluid.
 * </ul>
 *
 * <p>Issue #893 closes the one deviation #804 above still called out: the fuel column is now upstream's
 * own live tank, cycling every {@code smeltery_fuel} registry entry hot enough for the recipe
 * ({@link MeltingRecipes}'s own filter mirrors upstream's {@code SmeltingRecipeWrapper} constructor's
 * temperature check), in the same 12x32 recess the flame icon used to sit in. Upstream also has a
 * solid-fuel slot below the tank; Forgeweave's smeltery burns fluids only (docs/SCOPE.md M2), so
 * there is no second slot to add.
 */
final class MeltingCategory implements IRecipeCategory<MeltingDisplay> {
    static final RecipeType<MeltingDisplay> TYPE =
            RecipeType.create(Forgeweave.MODID, "melting", MeltingDisplay.class);

    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.MELTING;
    private static final int WIDTH = PANEL.width();
    private static final int HEIGHT = PANEL.height();

    /** Upstream's own item input slot -- `builder.addSlot(INPUT, 24, 18)`. */
    private static final int INPUT_X = 24;
    private static final int INPUT_Y = 18;
    /** Upstream's own fluid output -- `builder.addSlot(OUTPUT, 96, 4).setFluidRenderer(.., 32, 32)`. */
    private static final int OUTPUT_X = 96;
    private static final int OUTPUT_Y = 4;
    private static final int OUTPUT_SIZE = 32;
    /** Upstream's own tank overlay -- `createDrawable(melting.png, 132, 0, 32, 32)`. */
    private static final int OVERLAY_U = 132;
    private static final int OVERLAY_V = 0;
    /** Upstream's own arrow -- `drawableBuilder(melting.png, 150, 41, 24, 17)`, drawn at (56,18). */
    private static final int ARROW_U = 150;
    private static final int ARROW_V = 41;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_X = 56;
    private static final int ARROW_Y = 18;
    /**
     * Upstream animates this arrow over the recipe's own melting time; {@code MeltingDisplay} carries
     * no time (Forgeweave's smeltery melts on a fixed tick budget, docs/SCOPE.md M2 #96), so the
     * sweep runs on one fixed period for every row instead.
     */
    private static final int ARROW_TICKS = 100;
    /**
     * Upstream's own liquid-fuel tank -- `builder.addSlot(RENDER_ONLY, 4, 4).setFluidRenderer(1, false, 12, 32)`.
     * Issue #893: a real render-only fluid slot now, cycling {@link MeltingDisplay#fuels()} instead of
     * the flame icon that used to sit here.
     */
    private static final int FUEL_X = 4;
    private static final int FUEL_Y = 4;
    private static final int FUEL_WIDTH = 12;
    private static final int FUEL_HEIGHT = 32;
    /** Upstream forces the tank display to always read full regardless of the fuel's own registered amount. */
    private static final int FUEL_DISPLAY_AMOUNT = 1;
    /** Upstream's own temperature row -- `int x = 56 - font.width(tempString) / 2; drawString(.., x, 3, ..)`. */
    private static final int TEMPERATURE_CENTER_X = 56;
    private static final int TEMPERATURE_Y = 3;
    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable background;
    private final IDrawable tankOverlay;

    MeltingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.STANDARD_CORE.get()));
        arrow = helper.drawableBuilder(PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT)
                .buildAnimated(ARROW_TICKS, StartDirection.LEFT, false);
        background = JeiCategoryChrome.panel(helper, PANEL);
        tankOverlay = helper.createDrawable(PANEL.background(), OVERLAY_U, OVERLAY_V, OUTPUT_SIZE, OUTPUT_SIZE);
    }

    @Override
    public RecipeType<MeltingDisplay> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.forgeweave.melting");
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
    public void setRecipe(IRecipeLayoutBuilder builder, MeltingDisplay recipe, IFocusGroup focuses) {
        builder.addInputSlot(INPUT_X, INPUT_Y).addItemStacks(recipe.inputs());
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y)
                .setFluidRenderer(recipe.amount(), false, OUTPUT_SIZE, OUTPUT_SIZE)
                .setOverlay(tankOverlay, 0, 0)
                .addFluidStack(recipe.fluid(), recipe.amount())
                .addRichTooltipCallback((view, tooltip) -> {
                    if (recipe.ore()) {
                        tooltip.add(Component.translatable("jei.category.forgeweave.melting.core_multiplier"));
                    }
                });

        // Issue #893: the fuel column upstream's own tank cycles -- every smeltery_fuel entry hot
        // enough for this recipe (MeltingRecipes#acceptedFuels). Render-only: the smeltery reads the
        // registry directly at melt time, so nothing here is a real recipe input.
        IRecipeSlotBuilder fuelSlot = builder.addSlot(RecipeIngredientRole.RENDER_ONLY, FUEL_X, FUEL_Y)
                .setFluidRenderer(FUEL_DISPLAY_AMOUNT, false, FUEL_WIDTH, FUEL_HEIGHT);
        for (SmelteryFuel fuel : recipe.fuels()) {
            fuelSlot.addFluidStack(fuel.fluid(), FUEL_DISPLAY_AMOUNT);
        }
        fuelSlot.addRichTooltipCallback((view, tooltip) -> view.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK)
                .ifPresent(displayed -> recipe.fuels().stream()
                        .filter(fuel -> fuel.fluid() == displayed.getFluid())
                        .findFirst()
                        .ifPresent(fuel -> tooltip.add(Component.translatable(
                                "jei.category.forgeweave.melting.fuel_temperature", TemperatureText.format(fuel.temperature()))))));
    }

    @Override
    public void draw(MeltingDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);

        Font font = Minecraft.getInstance().font;
        JeiCategoryChrome.drawCentered(guiGraphics, font, TemperatureText.format(recipe.temperature()),
                TEMPERATURE_CENTER_X, TEMPERATURE_Y, TEXT_COLOR, false);
    }
}
