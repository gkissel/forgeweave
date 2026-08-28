package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 *
 * <p>Issue #785: modifier application has no upstream JEI category of its own either (upstream's
 * {@code ModifierRecipeCategory} models a completely different six-slot upgrade-socket layout), so
 * per the maintainer's decision this reuses the closest upstream background -- {@code
 * PartBuilderCategory}'s plain station panel -- tiled to this row's width via {@link
 * JeiCategoryChrome#stationPanel}, the same crop {@link EmbossingCategory} uses.
 */
final class ModifierApplicationCategory implements IRecipeCategory<ModifierRecipe> {
    static final RecipeType<ModifierRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "modifier_application", ModifierRecipe.class);

    static final ResourceLocation BACKGROUND_LOC = JeiCategoryGeometry.MODIFIER_APPLICATION.background();

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
    private static final int SLOT_PITCH = 20;
    private static final int TOOL_X = GUTTER;
    private static final int REAGENTS_X = TOOL_X + SLOT_PITCH;
    /**
     * Issue #781: an AND recipe ({@code require_all_reagents}) needs one slot per declared reagent,
     * side by side, in place of the OR reading's single cycling slot below -- {@link IRecipeCategory}
     * has no per-recipe {@code getWidth()}, so the panel is sized for the worst case once, the same
     * free-slot budget {@link EmbossingCategory} caps its own reagent row at: every one of the Tool
     * Station's five free input slots ({@code menu.ToolStationMenu#INPUT_SLOTS}) could be a distinct
     * AND reagent here, since (unlike embossing) no donor part claims one of them first.
     */
    private static final int MAX_REAGENT_SLOTS = 5;
    private static final int SLOT_Y = 10 + GUTTER;
    private static final int ARROW_X = REAGENTS_X + MAX_REAGENT_SLOTS * SLOT_PITCH + 2;
    private static final int TEXT_X = ARROW_X + 24;
    private static final int NAME_Y = 6 + GUTTER;
    private static final int LEVEL_CAP_Y = 20 + GUTTER;
    private static final int TEXT_COLOR = 0x404040;
    private static final int TEXT_WIDTH = 74;
    static final int WIDTH = TEXT_X + TEXT_WIDTH + GUTTER;
    static final int HEIGHT = 38 + 2 * GUTTER;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable background;

    ModifierApplicationCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.TOOL_STATION.get()));
        arrow = helper.getRecipeArrow();
        background = JeiCategoryChrome.stationPanel(helper, WIDTH, HEIGHT);
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
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, TOOL_X, SLOT_Y).addItemStacks(ANY_TOOL);
        if (recipe.requireAllReagents()) {
            // Issue #781: an AND recipe needs every reagent visible at once, so each gets its own
            // slot -- unlike the OR reading below, no cycling within a slot. recipe.reagentSlotCount()
            // (also used by the guide book's ModifyPageContent) agrees this is reagents().size() slots.
            List<ModifierRecipe.Reagent> reagents = recipe.reagents();
            for (int i = 0; i < recipe.reagentSlotCount(); i++) {
                builder.addInputSlot(REAGENTS_X + i * SLOT_PITCH, SLOT_Y).addIngredients(reagents.get(i).ingredient());
            }
        } else {
            // Every accepted reagent cycles through the one input slot (issue #259: haste shows
            // redstone dust and the 9-unit redstone block as alternatives, the way a tag ingredient cycles).
            IIngredientAcceptor<?> reagentSlot = builder.addInputSlot(REAGENTS_X, SLOT_Y);
            for (ModifierRecipe.Reagent reagent : recipe.reagents()) {
                reagentSlot.addIngredients(reagent.ingredient());
            }
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
