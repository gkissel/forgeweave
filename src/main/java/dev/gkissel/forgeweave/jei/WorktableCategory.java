package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import dev.gkissel.forgeweave.modifier.WorktableRecipe;

/**
 * The Modifier Worktable's two functions (issue #1057): a tool plus a reagent, and what the reagent
 * does to the modifiers already on it.
 *
 * <p>Upstream's {@code ModifierWorktableCategory} is a {@code 121x35} strip with no background art of
 * its own -- a tool slot at (23, 16), its two reagent slots at (43, 16) and (61, 16), the chosen
 * modifier at (82, 16), and the function's title across the top. The geometry carries over; the
 * modifier slot does not, because upstream shows a modifier there as a JEI ingredient of its own
 * registered type and Forgeweave has no such ingredient. That slot shows the leftovers instead (the
 * dry sponge), which is the only item this table ever produces besides the tool.
 */
final class WorktableCategory implements IRecipeCategory<WorktableDisplay> {
    static final RecipeType<WorktableDisplay> TYPE =
            RecipeType.create(Forgeweave.MODID, "modifier_worktable", WorktableDisplay.class);

    /** Upstream {@code ModifierWorktableCategory}'s own {@code (121, 35)}. */
    private static final int WIDTH = 121;
    private static final int HEIGHT = 35;

    private static final int TOOL_X = 23;
    private static final int FIRST_INPUT_X = 43;
    private static final int SECOND_INPUT_X = 61;
    private static final int RESULT_X = 82;
    private static final int SLOT_Y = 16;
    /** Upstream {@code createRecipeExtras}: the title at (3, 2), 0x404040. */
    private static final int TITLE_X = 3;
    private static final int TITLE_Y = 2;
    private static final int TITLE_COLOR = 0x404040;
    private static final int TITLE_WIDTH = 115;

    /** What the table can be shown working on: one harvest tool and one armor piece, cycling. */
    private static final List<ItemStack> TOOLS = List.of(
            new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get()),
            new ItemStack(ForgeweaveItems.ARMOR_HELMET.get()));

    private final IDrawable icon;
    private final IDrawable slotFrame;

    WorktableCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.MODIFIER_WORKTABLE.get()));
        slotFrame = JeiCategoryChrome.slotFrame(helper);
    }

    @Override
    public RecipeType<WorktableDisplay> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.forgeweave.modifier_worktable");
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
    public ResourceLocation getRegistryName(WorktableDisplay display) {
        return display.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WorktableDisplay display, IFocusGroup focuses) {
        WorktableRecipe recipe = display.recipe();
        // The table works on any assembled tool or armor piece, so this is context rather than an
        // input JEI could ever consume -- the same RENDER_ONLY reading ModifierApplicationCategory
        // gives its own tool slot (#794).
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, TOOL_X, SLOT_Y).addItemStacks(TOOLS);
        builder.addInputSlot(FIRST_INPUT_X, SLOT_Y).addIngredients(recipe.input());
        // The second reagent slot is the sorting direction, not a second cost: the same reagent goes
        // in either slot and the empty first one is what reads as backwards.
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, SECOND_INPUT_X, SLOT_Y).addIngredients(recipe.input());
        builder.addOutputSlot(RESULT_X, SLOT_Y).addItemStacks(recipe.leftovers());
    }

    @Override
    public void draw(WorktableDisplay display, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
            double mouseX, double mouseY) {
        JeiCategoryChrome.drawSlotFrame(slotFrame, guiGraphics, TOOL_X, SLOT_Y);
        JeiCategoryChrome.drawSlotFrame(slotFrame, guiGraphics, FIRST_INPUT_X, SLOT_Y);
        JeiCategoryChrome.drawSlotFrame(slotFrame, guiGraphics, SECOND_INPUT_X, SLOT_Y);
        JeiCategoryChrome.drawSlotFrame(slotFrame, guiGraphics, RESULT_X, SLOT_Y);
        Component title = Component.translatable(display.recipe().kind() == WorktableRecipe.Kind.SORT
                ? "jei.category.forgeweave.modifier_worktable.sort"
                : "jei.category.forgeweave.modifier_worktable.remove");
        guiGraphics.drawString(Minecraft.getInstance().font,
                JeiCategoryChrome.trimToWidth(Minecraft.getInstance().font, title, TITLE_WIDTH),
                TITLE_X, TITLE_Y, TITLE_COLOR, false);
    }
}
