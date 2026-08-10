package dev.gkissel.forgeweave.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
 */
final class MeltingCategory implements IRecipeCategory<MeltingDisplay> {
    static final RecipeType<MeltingDisplay> TYPE =
            RecipeType.create(Forgeweave.MODID, "melting", MeltingDisplay.class);

    private static final int WIDTH = 110;
    private static final int HEIGHT = 40;
    private static final int TANK_SIZE = 16;
    private static final int ARROW_X = 44;
    private static final int TEMPERATURE_Y = 23;
    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable icon;
    private final IDrawable arrow;

    MeltingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.STANDARD_CORE.get()));
        arrow = helper.getRecipeArrow();
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
        builder.addInputSlot(0, 1).addItemStacks(recipe.inputs());
        builder.addOutputSlot(WIDTH - TANK_SIZE - 2, 1)
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
        arrow.draw(guiGraphics, ARROW_X, (18 - arrow.getHeight()) / 2 + 1);

        Font font = Minecraft.getInstance().font;
        Component temperature = Component.translatable("jei.category.forgeweave.melting.temperature", recipe.temperature());
        guiGraphics.drawString(font, temperature, 0, TEMPERATURE_Y, TEXT_COLOR, false);
    }
}
