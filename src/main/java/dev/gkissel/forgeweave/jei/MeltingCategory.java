package dev.gkissel.forgeweave.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
import dev.gkissel.forgeweave.client.TemperatureText; // #276
import dev.gkissel.forgeweave.item.ForgeweaveItems;

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
 * <p>Issue #785: background, arrow, flame column and the temperature text row are derived from
 * upstream's own {@code AbstractMeltingCategory}/{@code MeltingCategory}
 * (`~/development/minecraft/references/tinkers-1.20` @ de26560d, MIT -- NOTICE.md). Upstream's fuel
 * tank is 12px wide and its output a 32x32 fluid render with a frame overlay; this category keeps its
 * own fixed 16x16 icons at those same origins rather than deriving the overlay/fuel-strip assets too.
 */
final class MeltingCategory implements IRecipeCategory<MeltingDisplay> {
    static final RecipeType<MeltingDisplay> TYPE =
            RecipeType.create(Forgeweave.MODID, "melting", MeltingDisplay.class);

    /** Upstream {@code AbstractMeltingCategory}'s own background: `textures/gui/jei/melting.png`, (0,0,132,40). */
    static final ResourceLocation BACKGROUND_LOC = JeiCategoryGeometry.MELTING.background();
    static final int WIDTH = JeiCategoryGeometry.MELTING.width();
    static final int HEIGHT = JeiCategoryGeometry.MELTING.height();

    private static final int TANK_SIZE = 16;
    /** Upstream's own item input slot -- `builder.addSlot(INPUT, 24, 18)`. */
    private static final int INPUT_X = 24;
    private static final int INPUT_Y = 18;
    /** Upstream's own fluid output origin -- `builder.addSlot(OUTPUT, 96, 4, ...)`. */
    private static final int OUTPUT_X = 96;
    private static final int OUTPUT_Y = 4;
    /** Upstream's own arrow position -- `cachedArrows...draw(graphics, 56, 18)`. */
    private static final int ARROW_X = 56;
    /** Upstream's own fuel-tank column, to the left of the item slot -- `builder.addSlot(RENDER_ONLY, 4, 4, ...)`. */
    private static final int FLAME_X = 4;
    /** Upstream's own temperature text row -- `graphics.drawString(..., x, 3, ...)`. */
    private static final int TEMPERATURE_X = 4;
    private static final int TEMPERATURE_Y = 3;
    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable flame;
    private final IDrawable background;

    MeltingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.STANDARD_CORE.get()));
        arrow = helper.getRecipeArrow();
        flame = helper.getRecipeFlameFilled();
        background = helper.createDrawable(BACKGROUND_LOC, 0, 0, WIDTH, HEIGHT);
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
                .setFluidRenderer(recipe.amount(), false, TANK_SIZE, TANK_SIZE)
                .addFluidStack(recipe.fluid(), recipe.amount())
                .addRichTooltipCallback((view, tooltip) -> {
                    if (recipe.ore()) {
                        tooltip.add(Component.translatable("jei.category.forgeweave.melting.core_multiplier"));
                    }
                });
    }

    @Override
    public void draw(MeltingDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        flame.draw(guiGraphics, FLAME_X, (HEIGHT - flame.getHeight()) / 2);
        arrow.draw(guiGraphics, ARROW_X, (HEIGHT - arrow.getHeight()) / 2);

        Font font = Minecraft.getInstance().font;
        Component temperature = Component.translatable("jei.category.forgeweave.melting.temperature",
                TemperatureText.format(recipe.temperature()));
        guiGraphics.drawString(font, temperature, TEMPERATURE_X, TEMPERATURE_Y, TEXT_COLOR, false);
    }
}
