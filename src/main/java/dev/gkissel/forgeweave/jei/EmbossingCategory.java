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
 * ({@link ModifierApplicationCategory#ANY_TOOL}) -- embossing has no recipe-specific tool requirement
 * either, only "not already embossed" (see the drawn rule below).
 *
 * <p>At most four reagent slots are ever laid out: embossing spends every one of the station's five
 * free input slots ({@code menu.ToolAssemblyRecipes}' free-slot javadoc), and the donor part always
 * claims one of them, so {@code modifier.EmbossingRecipe#reagents} can never exceed four entries for
 * any datapack this station's five slots could actually satisfy. The donor takes the first of
 * upstream's five ring slots and the reagents take the rest -- see {@link ModifierPanel}, which
 * issue #804 adopted for this category and {@link ModifierApplicationCategory} alike: embossing is
 * the same picture as a modifier recipe (reagents plus a tool produce a named modifier on that
 * tool), so it borrows that panel rather than the tiled Part Builder row #785 gave it.
 */
final class EmbossingCategory implements IRecipeCategory<EmbossingDisplay> {
    static final RecipeType<EmbossingDisplay> TYPE =
            RecipeType.create(Forgeweave.MODID, "embossing", EmbossingDisplay.class);

    /** The donor part claims upstream's top ring slot; reagents fill the rest. */
    private static final int DONOR_SLOT = 0;
    private static final int MAX_REAGENT_SLOTS = ModifierPanel.INPUT_SLOTS.length - 1;

    private static final int WIDTH = ModifierPanel.WIDTH;
    private static final int HEIGHT = ModifierPanel.HEIGHT;

    private final IDrawable icon;
    private final IDrawable background;

    EmbossingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.TOOL_STATION.get()));
        background = JeiCategoryChrome.panel(helper, ModifierPanel.PANEL);
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
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, ModifierPanel.TOOL_X, ModifierPanel.TOOL_Y)
                .addItemStacks(ModifierApplicationCategory.ANY_TOOL);
        builder.addInputSlot(ModifierPanel.INPUT_SLOTS[DONOR_SLOT][0], ModifierPanel.INPUT_SLOTS[DONOR_SLOT][1])
                .addItemStacks(recipe.donorParts());
        int shown = Math.min(recipe.reagents().size(), MAX_REAGENT_SLOTS);
        for (int i = 0; i < shown; i++) {
            int[] slot = ModifierPanel.INPUT_SLOTS[DONOR_SLOT + 1 + i];
            builder.addInputSlot(slot[0], slot[1]).addIngredients(recipe.reagents().get(i));
        }
        // The same tool, now embossed; see ModifierApplicationCategory's own note on this slot.
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, ModifierPanel.RESULT_X, ModifierPanel.RESULT_Y)
                .addItemStacks(ModifierApplicationCategory.ANY_TOOL);
    }

    @Override
    public void draw(EmbossingDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);

        Font font = Minecraft.getInstance().font;
        Component name = ModifierApplication.name(Embossing.idFor(recipe.material()));
        Component rule = Component.translatable("jei.category.forgeweave.embossing.one_per_tool");
        JeiCategoryChrome.drawCentered(guiGraphics, font,
                JeiCategoryChrome.trimToWidth(font, name, ModifierPanel.NAME_WIDTH),
                ModifierPanel.NAME_CENTER_X, ModifierPanel.NAME_Y, ModifierPanel.NAME_COLOR, true);
        JeiCategoryChrome.drawCentered(guiGraphics, font,
                JeiCategoryChrome.trimToWidth(font, rule, ModifierPanel.LEVEL_WIDTH),
                ModifierPanel.LEVEL_CENTER_X, ModifierPanel.LEVEL_Y, ModifierPanel.TEXT_COLOR, false);
    }
}
