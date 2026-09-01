package dev.gkissel.forgeweave.jei;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.TemperatureText;

/**
 * Which fluids the smeltery burns as fuel, and at what rate (issue #890, {@code smeltery_fuel}
 * registry read by {@link dev.gkissel.forgeweave.recipe.SmelteryFuel#find}). Upstream has no JEI
 * category for this at all -- 1.12's registration is a hardcoded Java call
 * ({@code TinkerSmeltery.registerSmelteryFuel}, see {@code SmelteryFuel}'s own javadoc) with no JEI
 * plugin counterpart to derive from -- so this borrows {@link JeiCategoryGeometry#MELTING}'s panel
 * rather than adding a new one, the same "no upstream category of its own" move {@code RepairCategory}
 * and {@code EmbossingCategory} already make (see their own {@link JeiCategoryGeometry} entries).
 *
 * <p>Melting's own item-input/fluid-output/arrow/temperature layout reads naturally as "pour this
 * fuel in, get this burn rate at this temperature", so the item slot becomes the fuel's own bucket
 * (issue #890: "fluid, with bucket as catalyst icon") and the fluid slot becomes the amount burned
 * per drain rather than a melt result. Unlike {@link MeltingCategory} the arrow's period is real, not
 * an arbitrary fixed sweep: it is the recipe's own {@code duration} (smeltery melt-cycles one drain
 * lasts), cached per distinct value the same way {@link CastingCategory} caches its cooling-time
 * arrows. The left-hand fuel-tank recess {@link MeltingCategory}/{@code AlloyingCategory} fill with a
 * generic flame (what fuels <em>this</em> melt) has no equivalent here -- this category answers that
 * question, so it is left as the bare background art.
 */
final class SmelteryFuelCategory implements IRecipeCategory<SmelteryFuelDisplay> {
    static final RecipeType<SmelteryFuelDisplay> TYPE =
            RecipeType.create(Forgeweave.MODID, "smeltery_fuel", SmelteryFuelDisplay.class);

    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.MELTING;
    private static final int WIDTH = PANEL.width();
    private static final int HEIGHT = PANEL.height();

    /** Melting's own item input slot, repurposed for the fuel's bucket. */
    private static final int BUCKET_X = 24;
    private static final int BUCKET_Y = 18;
    /** Melting's own fluid output tank, repurposed for the amount burned per drain. */
    private static final int FLUID_X = 96;
    private static final int FLUID_Y = 4;
    private static final int FLUID_SIZE = 32;
    private static final int OVERLAY_U = 132;
    private static final int OVERLAY_V = 0;
    /** Melting's own arrow crop -- see {@link MeltingCategory}'s own constants for the source rect. */
    private static final int ARROW_U = 150;
    private static final int ARROW_V = 41;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_X = 56;
    private static final int ARROW_Y = 18;
    private static final int TEMPERATURE_CENTER_X = 56;
    private static final int TEMPERATURE_Y = 3;
    private static final int TEXT_COLOR = 0x404040;

    private final IGuiHelper helper;
    private final IDrawable icon;
    private final IDrawable background;
    private final IDrawable tankOverlay;
    /** One cached animated arrow per distinct {@code duration}, {@link CastingCategory}'s own idiom. */
    private final Map<Integer, IDrawable> arrows = new HashMap<>();

    SmelteryFuelCategory(IGuiHelper helper) {
        this.helper = helper;
        this.icon = helper.createDrawableItemStack(new ItemStack(Items.LAVA_BUCKET));
        this.background = JeiCategoryChrome.panel(helper, PANEL);
        this.tankOverlay = helper.createDrawable(PANEL.background(), OVERLAY_U, OVERLAY_V, FLUID_SIZE, FLUID_SIZE);
    }

    @Override
    public RecipeType<SmelteryFuelDisplay> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.forgeweave.smeltery_fuel");
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
    public void setRecipe(IRecipeLayoutBuilder builder, SmelteryFuelDisplay recipe, IFocusGroup focuses) {
        Item bucket = recipe.fluid().getBucket();
        if (bucket != Items.AIR) {
            builder.addInputSlot(BUCKET_X, BUCKET_Y).addItemStack(new ItemStack(bucket));
        }
        builder.addOutputSlot(FLUID_X, FLUID_Y)
                .setFluidRenderer(recipe.amount(), false, FLUID_SIZE, FLUID_SIZE)
                .setOverlay(tankOverlay, 0, 0)
                .addFluidStack(recipe.fluid(), recipe.amount())
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.category.forgeweave.smeltery_fuel.duration", recipe.duration()));
                    if (recipe.hotterThanLavaBy() > 0) {
                        tooltip.add(Component.translatable(
                                "jei.category.forgeweave.smeltery_fuel.vs_lava", recipe.hotterThanLavaBy()));
                    }
                });
    }

    @Override
    public void draw(SmelteryFuelDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrows.computeIfAbsent(recipe.duration(), ticks ->
                        helper.drawableBuilder(PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT)
                                .buildAnimated(ticks, StartDirection.LEFT, false))
                .draw(guiGraphics, ARROW_X, ARROW_Y);

        Font font = Minecraft.getInstance().font;
        JeiCategoryChrome.drawCentered(guiGraphics, font, TemperatureText.format(recipe.temperature()),
                TEMPERATURE_CENTER_X, TEMPERATURE_Y, TEXT_COLOR, false);
    }
}
