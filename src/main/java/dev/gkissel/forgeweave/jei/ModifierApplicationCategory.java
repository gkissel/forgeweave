package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
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
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;

/**
 * Tool Station modifier application: a reagent applied to any assembled tool with a free slot adds
 * or levels up a modifier, up to its level cap (docs/SCOPE.md M2 issues #105-#108, JEI category per
 * issue #109). Icon is the Tool Station block item, same as {@link AssemblyCategory}/{@link
 * RepairCategory} -- modifier application is the repair tab's other half ({@code
 * menu.ToolStationMenu}'s class javadoc: "the repair tab is also the modify tab").
 *
 * <p>The tool slot has no recipe-specific input -- any assembled tool with a free modifier slot
 * qualifies ({@code modifier.ModifierApplication#resolve}) -- so it is a {@link
 * RecipeIngredientRole#RENDER_ONLY} slot cycling the same bare representative tool stacks {@link
 * RepairCategory} uses, shown purely for context; {@link ModifierApplicationTransferHandler} never
 * targets it, and being {@code RENDER_ONLY} rather than an input keeps the recipe's one real input
 * (the reagent) the only slot that transfer has to account for.
 *
 * <p>The resulting modifier has no item form for an output slot, so its name ({@link
 * ModifierApplication#name}, the same trait-style {@code modifier.<namespace>.<path>.name} key
 * {@code ToolTooltip} and {@code InfoPanel} already use) and level cap ({@link
 * ModifierRecipe#levelsReached}, the same datapack-driven per-level schedule the Tool Station itself
 * resolves against -- ADR-0004 decision 1) are drawn as text instead.
 */
final class ModifierApplicationCategory implements IRecipeCategory<ModifierRecipe> {
    static final RecipeType<ModifierRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "modifier_application", ModifierRecipe.class);

    /**
     * Bare tool icons, same set as {@code RepairRecipes}' -- any tool type accepts any modifier.
     * Package-visible so {@link EmbossingCategory} -- the repair tab's third RENDER_ONLY tool
     * mechanic -- reuses it rather than redeclaring the same three-item list (issue #165).
     */
    static final List<ItemStack> ANY_TOOL = List.of(
            new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get()),
            new ItemStack(ForgeweaveItems.TOOL_SHOVEL.get()),
            new ItemStack(ForgeweaveItems.TOOL_HATCHET.get()));

    private static final int GUTTER = JeiCategoryChrome.GUTTER;
    private static final int WIDTH = 140 + 2 * GUTTER;
    private static final int HEIGHT = 38 + 2 * GUTTER;
    private static final int SLOT_Y = 10 + GUTTER;
    private static final int ARROW_X = 42 + GUTTER;
    private static final int TEXT_X = 66 + GUTTER;
    private static final int NAME_Y = 6 + GUTTER;
    private static final int LEVEL_CAP_Y = 20 + GUTTER;
    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable background;

    ModifierApplicationCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.TOOL_STATION.get()));
        arrow = helper.getRecipeArrow();
        background = JeiCategoryChrome.panel(WIDTH, HEIGHT);
    }

    @Override
    public RecipeType<ModifierRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.forgeweave.modifier_application");
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
    public void setRecipe(IRecipeLayoutBuilder builder, ModifierRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, GUTTER, SLOT_Y).addItemStacks(ANY_TOOL);
        // Every accepted reagent cycles through the one input slot (issue #259: haste shows redstone
        // dust and the 9-unit redstone block as alternatives, the way a tag ingredient cycles).
        IIngredientAcceptor<?> reagentSlot = builder.addInputSlot(GUTTER + 20, SLOT_Y);
        for (ModifierRecipe.Reagent reagent : recipe.reagents()) {
            reagentSlot.addIngredients(reagent.ingredient());
        }
    }

    @Override
    public void draw(ModifierRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrow.draw(guiGraphics, ARROW_X, (HEIGHT - arrow.getHeight()) / 2);

        Font font = Minecraft.getInstance().font;
        Component name = ModifierApplication.name(recipe.modifier());
        Component levelCap = Component.translatable(
                "jei.category.forgeweave.modifier_application.level_cap", recipe.levelsReached(recipe.maxLevel()));
        guiGraphics.drawString(font, name, TEXT_X, NAME_Y, TEXT_COLOR, false);
        guiGraphics.drawString(font, levelCap, TEXT_X, LEVEL_CAP_Y, TEXT_COLOR, false);
    }
}
