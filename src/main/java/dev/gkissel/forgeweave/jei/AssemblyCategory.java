package dev.gkissel.forgeweave.jei;

import java.util.List;

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

/**
 * Tool Station/Tool Forge assembly recipes: one part slot per part -> tool. Two {@link RecipeType}s
 * share this one class rather than each getting its own file (docs/SCOPE.md M3 issue #165's Tool
 * Forge catalyst split): {@link #TYPE} for the thirteen tools the Tool Station itself builds, and
 * {@link #LARGE_TYPE} for the eight {@code #forgeweave:large_tools} it refuses ({@code
 * menu.ToolAssemblyRecipes#isLargeTool}) -- registering them as two catalyst-bearing categories
 * instead of one is what lets JEI show the Tool Station as this recipe's location for the first and
 * only the Tool Forge for the second; there is no single-category way to vary a recipe's catalyst
 * list by the recipe.
 *
 * <p>Issue #785: the background is derived from upstream's own {@code ToolBuildingCategory}
 * (`~/development/minecraft/references/tinkers-1.20` @ de26560d, MIT -- NOTICE.md), the closest
 * upstream analog for "parts assemble into a tool". Upstream renders a scaled 3D preview of the
 * finished tool over a fixed 134x66 panel with fading slot-border overlays; this category keeps its
 * own flat item-slot column instead (upstream's panel has no room for a growing four-part list, and
 * the 3D-render/overlay machinery is not worth reproducing for a fixed background swap), so only
 * {@link #WIDTH} (upstream's real 134) is a direct derivation -- {@link #HEIGHT} still grows with the
 * tool's own part count the way it did before #785.
 */
final class AssemblyCategory implements IRecipeCategory<AssemblyRecipe> {
    static final RecipeType<AssemblyRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "tool_assembly", AssemblyRecipe.class);
    static final RecipeType<AssemblyRecipe> LARGE_TYPE =
            RecipeType.create(Forgeweave.MODID, "large_tool_assembly", AssemblyRecipe.class);

    /** Upstream {@code ToolBuildingCategory}'s own background rect inside `tinker_station.png`. */
    static final ResourceLocation BACKGROUND_LOC = JeiCategoryGeometry.ASSEMBLY.background();
    private static final int BACKGROUND_U = 122;
    private static final int BACKGROUND_V = 77;

    private static final int GUTTER = JeiCategoryChrome.GUTTER;
    /** Upstream {@code ToolBuildingCategory}'s own background width. */
    static final int WIDTH = JeiCategoryGeometry.ASSEMBLY.width();
    private static final int SLOT_PITCH = 20;
    /** Tall enough for the longest part list in the roster (four parts, the Tool Forge tier). */
    static final int HEIGHT = JeiCategoryGeometry.ASSEMBLY.height();

    private final RecipeType<AssemblyRecipe> type;
    private final Component title;
    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable background;

    AssemblyCategory(IGuiHelper helper, RecipeType<AssemblyRecipe> type, Component title, ItemStack catalystIcon) {
        this.type = type;
        this.title = title;
        icon = helper.createDrawableItemStack(catalystIcon);
        arrow = helper.getRecipeArrow();
        background = helper.createDrawable(BACKGROUND_LOC, BACKGROUND_U, BACKGROUND_V, WIDTH, HEIGHT);
    }

    @Override
    public RecipeType<AssemblyRecipe> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return title;
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
    public void setRecipe(IRecipeLayoutBuilder builder, AssemblyRecipe recipe, IFocusGroup focuses) {
        // One slot per part, stacked in the station's own slot order -- two for M3's two-part
        // weapons, four for the Tool Forge tier (issue #155), so the column grows with the tool.
        for (int slot = 0; slot < recipe.parts().size(); slot++) {
            builder.addInputSlot(GUTTER, GUTTER + slot * SLOT_PITCH).addItemStacks(recipe.parts().get(slot));
        }
        builder.addOutputSlot(WIDTH - 18 - GUTTER, (HEIGHT - 18) / 2).addItemStack(recipe.result());
    }

    @Override
    public void draw(AssemblyRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrow.draw(guiGraphics, GUTTER + 2 * SLOT_PITCH + 2, (HEIGHT - arrow.getHeight()) / 2);
    }
}
