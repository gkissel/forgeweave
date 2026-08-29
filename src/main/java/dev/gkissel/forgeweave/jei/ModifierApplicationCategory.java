package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
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
import dev.gkissel.forgeweave.client.book.ModifyPageContent;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.Modifier;
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
 * RecipeIngredientRole#RENDER_ONLY} slot cycling the representative tool stacks this recipe's
 * modifier actually accepts, shown purely for context; {@link ModifierApplicationTransferHandler}
 * never targets it, and being {@code RENDER_ONLY} rather than an input keeps the recipe's real
 * inputs (the reagents) the only slots transfer has to account for.
 *
 * <p>The resulting modifier has no item form for an output slot, so its name ({@link
 * ModifierApplication#name}, the same trait-style {@code modifier.<namespace>.<path>.name} key
 * {@code ToolTooltip} and {@code InfoPanel} already use) and level cap ({@link
 * ModifierRecipe#levelsReached}, the same datapack-driven per-level schedule the Tool Station itself
 * resolves against -- ADR-0004 decision 1) are drawn as text instead -- in exactly the two places
 * upstream draws a modifier's name and level line. See {@link ModifierPanel} for the layout and for
 * why #785's "no upstream counterpart" reading of {@code ModifierRecipeCategory} was wrong.
 */
final class ModifierApplicationCategory implements IRecipeCategory<ModifierRecipe> {
    static final RecipeType<ModifierRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "modifier_application", ModifierRecipe.class);

    /**
     * Bare tool icons, same set as {@code RepairRecipes}' -- repair (unlike modifier application) has
     * no per-recipe restriction on which tool it targets. Package-visible so {@link EmbossingCategory}
     * -- the repair tab's third RENDER_ONLY tool mechanic, which likewise applies to any tool -- reuses
     * it rather than redeclaring the same three-item list (issue #165).
     *
     * <p>Issue #794: this catalog is <b>not</b> "any tool accepts any modifier" -- the three harvest
     * tools here are all {@code Category.HARVEST}, so before this issue's fix they were the row shown
     * for every modifier recipe here regardless of restriction, including armor-only and
     * projectile-only ones no harvest tool could ever take. {@link #setRecipe} now asks {@link
     * ModifyPageContent#compatibleEntries} per recipe instead of reusing this constant.
     */
    static final List<ItemStack> ANY_TOOL = List.of(
            new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get()),
            new ItemStack(ForgeweaveItems.TOOL_SHOVEL.get()),
            new ItemStack(ForgeweaveItems.TOOL_HATCHET.get()));

    /**
     * Issue #781: an AND recipe ({@code require_all_reagents}) needs one slot per declared reagent,
     * side by side, in place of the OR reading's single cycling slot -- and {@link IRecipeCategory}
     * has no per-recipe {@code getWidth()}, so the panel is sized for the worst case once. Upstream's
     * own five input slots are exactly that worst case: every one of the Tool Station's five free
     * input slots ({@code menu.ToolStationMenu#INPUT_SLOTS}) could be a distinct AND reagent here,
     * since (unlike embossing) no donor part claims one of them first.
     */
    private static final int MAX_REAGENT_SLOTS = ModifierPanel.INPUT_SLOTS.length;

    private static final int WIDTH = ModifierPanel.WIDTH;
    private static final int HEIGHT = ModifierPanel.HEIGHT;

    private final IDrawable icon;
    private final IDrawable background;

    ModifierApplicationCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.TOOL_STATION.get()));
        background = JeiCategoryChrome.panel(helper, ModifierPanel.PANEL);
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
        List<ItemStack> tools = catalystTools(recipe);
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, ModifierPanel.TOOL_X, ModifierPanel.TOOL_Y)
                .addItemStacks(tools);
        if (recipe.requireAllReagents()) {
            // Issue #781: an AND recipe needs every reagent visible at once, so each gets its own
            // slot -- unlike the OR reading below, no cycling within a slot. recipe.reagentSlotCount()
            // (also used by the guide book's ModifyPageContent) agrees this is reagents().size() slots.
            List<ModifierRecipe.Reagent> reagents = recipe.reagents();
            int shown = Math.min(recipe.reagentSlotCount(), MAX_REAGENT_SLOTS);
            for (int i = 0; i < shown; i++) {
                builder.addInputSlot(ModifierPanel.INPUT_SLOTS[i][0], ModifierPanel.INPUT_SLOTS[i][1])
                        .addIngredients(reagents.get(i).ingredient());
            }
        } else {
            // Every accepted reagent cycles through the one input slot (issue #259: haste shows
            // redstone dust and the 9-unit redstone block as alternatives, the way a tag ingredient cycles).
            IIngredientAcceptor<?> reagentSlot =
                    builder.addInputSlot(ModifierPanel.INPUT_SLOTS[0][0], ModifierPanel.INPUT_SLOTS[0][1]);
            for (ModifierRecipe.Reagent reagent : recipe.reagents()) {
                reagentSlot.addIngredients(reagent.ingredient());
            }
        }
        // Upstream's own second tool slot: the same tool, now carrying the modifier. Forgeweave has no
        // "tool with modifier" stack to build, so this shows the same catalog -- RENDER_ONLY like the
        // input side, so an item lookup never claims modifier application produces a bare tool (#794).
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, ModifierPanel.RESULT_X, ModifierPanel.RESULT_Y)
                .addItemStacks(tools);
    }

    @Override
    public void draw(ModifierRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);

        Font font = Minecraft.getInstance().font;
        Component name = ModifierApplication.name(recipe.modifier());
        Component levelCap = Component.translatable(
                "jei.category.forgeweave.modifier_application.level_cap", recipe.levelsReached(recipe.maxLevel()));
        JeiCategoryChrome.drawCentered(guiGraphics, font,
                JeiCategoryChrome.trimToWidth(font, name, ModifierPanel.NAME_WIDTH),
                ModifierPanel.NAME_CENTER_X, ModifierPanel.NAME_Y, ModifierPanel.NAME_COLOR, true);
        JeiCategoryChrome.drawCentered(guiGraphics, font,
                JeiCategoryChrome.trimToWidth(font, levelCap, ModifierPanel.LEVEL_WIDTH),
                ModifierPanel.LEVEL_CENTER_X, ModifierPanel.LEVEL_Y, ModifierPanel.TEXT_COLOR, false);
    }

    /**
     * The render-only tool slot's cycling catalog for {@code recipe} (issue #794): every tool this
     * recipe's modifier actually accepts ({@link ModifyPageContent#compatibleEntries}), the same set
     * the guide book's modifier page picks its single illustration from -- {@link #ANY_TOOL} is not a
     * safe default here since it is all {@code Category.HARVEST} and an armor-only or projectile-only
     * modifier accepts none of them. Falls back to {@link #ANY_TOOL} only when the recipe's modifier
     * id isn't registered at all (a stale/malformed recipe JSON), so the slot still shows something
     * rather than an empty cycle.
     */
    private static List<ItemStack> catalystTools(ModifierRecipe recipe) {
        Modifier modifier = ForgeweaveModifiers.get(recipe.modifier());
        if (modifier == null) {
            return ANY_TOOL;
        }
        HolderLookup.Provider registries = Minecraft.getInstance().level == null ? null
                : Minecraft.getInstance().level.registryAccess();
        return ModifyPageContent.compatibleEntries(registries, modifier).stream()
                .map(entry -> new ItemStack(entry.tool().get()))
                .toList();
    }
}
