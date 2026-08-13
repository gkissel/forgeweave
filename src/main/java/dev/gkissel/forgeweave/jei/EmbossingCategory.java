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
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.Embossing;
import dev.gkissel.forgeweave.modifier.ModifierApplication;

/**
 * Embossing (docs/SCOPE.md M3 issues #154, #165): a donor tool part plus a fixed reagent set adds
 * the donor's material's traits to any assembled tool, without touching its stats -- {@code
 * modifier.Embossing}'s whole mechanic. Rides the same repair tab, and so the same station icon and
 * {@code RENDER_ONLY} "any tool" slot, as {@link RepairCategory}/{@link ModifierApplicationCategory}
 * ({@link ModifierApplicationCategory#ANY_TOOL} -- embossing has no recipe-specific tool requirement
 * either, only "not already embossed" (see the drawn rule below).
 *
 * <p>At most four reagent slots are ever laid out: embossing spends every one of the station's five
 * free input slots ({@code menu.ToolAssemblyRecipes}' free-slot javadoc), and the donor part always
 * claims one of them, so {@code modifier.EmbossingRecipe#reagents} can never exceed four entries for
 * any datapack this station's five slots could actually satisfy.
 */
final class EmbossingCategory implements IRecipeCategory<EmbossingDisplay> {
    static final RecipeType<EmbossingDisplay> TYPE =
            RecipeType.create(Forgeweave.MODID, "embossing", EmbossingDisplay.class);

    private static final int WIDTH = 220;
    private static final int HEIGHT = 38;
    private static final int SLOT_PITCH = 20;
    private static final int SLOT_Y = 10;
    private static final int TOOL_X = 0;
    private static final int DONOR_X = SLOT_PITCH;
    private static final int REAGENTS_X = DONOR_X + SLOT_PITCH;
    private static final int MAX_REAGENT_SLOTS = 4;
    private static final int ARROW_X = REAGENTS_X + MAX_REAGENT_SLOTS * SLOT_PITCH + 2;
    private static final int TEXT_X = ARROW_X + 24;
    private static final int NAME_Y = 6;
    private static final int RULE_Y = 20;
    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable icon;
    private final IDrawable arrow;

    EmbossingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.TOOL_STATION.get()));
        arrow = helper.getRecipeArrow();
    }

    @Override
    public RecipeType<EmbossingDisplay> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.forgeweave.embossing");
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
    public void setRecipe(IRecipeLayoutBuilder builder, EmbossingDisplay recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, TOOL_X, SLOT_Y).addItemStacks(ModifierApplicationCategory.ANY_TOOL);
        builder.addInputSlot(DONOR_X, SLOT_Y).addItemStacks(recipe.donorParts());
        for (int i = 0; i < recipe.reagents().size(); i++) {
            builder.addInputSlot(REAGENTS_X + i * SLOT_PITCH, SLOT_Y).addIngredients(recipe.reagents().get(i));
        }
    }

    @Override
    public void draw(EmbossingDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        arrow.draw(guiGraphics, ARROW_X, (HEIGHT - arrow.getHeight()) / 2);

        Font font = Minecraft.getInstance().font;
        Component name = ModifierApplication.name(Embossing.idFor(recipe.material()));
        Component rule = Component.translatable("jei.category.forgeweave.embossing.one_per_tool");
        guiGraphics.drawString(font, name, TEXT_X, NAME_Y, TEXT_COLOR, false);
        guiGraphics.drawString(font, rule, TEXT_X, RULE_Y, TEXT_COLOR, false);
    }
}
