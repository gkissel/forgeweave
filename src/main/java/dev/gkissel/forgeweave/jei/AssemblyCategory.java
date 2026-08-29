package dev.gkissel.forgeweave.jei;

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

/**
 * Tool Station/Tool Forge/Armor Station assembly recipes: one part slot per part -> tool or armor
 * piece. Three {@link RecipeType}s share this one class rather than each getting its own file
 * (docs/SCOPE.md M3 issue #165's Tool Forge catalyst split, extended by M4 issue #782's Armor
 * Station reversal): {@link #TYPE} for the tools the Tool Station itself builds, {@link #LARGE_TYPE}
 * for the {@code #forgeweave:large_tools} it refuses ({@code menu.ToolAssemblyRecipes#isLargeTool}),
 * and {@link #ARMOR_TYPE} for the {@code Category.ARMOR} entries neither tool block builds anymore
 * ({@code menu.ToolAssemblyRecipes#isArmorEntry}) -- registering them as separate catalyst-bearing
 * categories instead of one is what lets JEI show the right station as each recipe's location; there
 * is no single-category way to vary a recipe's catalyst list by the recipe.
 *
 * <p>Issue #785 derived the background from upstream's own {@code ToolBuildingCategory}
 * (`~/development/minecraft/references/tinkers-1.20` @ de26560d, MIT -- NOTICE.md) but kept a height
 * (86) that grew with the part count, so the crop read 20 rows past the end of upstream's real
 * 134x66 panel and pulled in whatever was underneath it in the atlas. Issue #804 pins the panel to
 * upstream's own `createDrawable(tinker_station.png, 122, 77, 134, 66)` and instead fits the part
 * column inside it: the parts fill the same blank left-hand zone upstream reserves for its 3D tool
 * preview (upstream's {@code itemCover} is 70x60 at (5,6)), as a centred grid at most two columns
 * wide, and the result goes in upstream's own output slot at {@code (WIDTH - 26, 23)}.
 *
 * <p>Deviation: upstream renders a big scaled 3D preview of the finished tool behind those slots and
 * takes each slot's position from the tool's own station layout. Forgeweave's parts have no such
 * per-tool layout to read, so the grid below is derived from the part count instead, and each slot
 * gets upstream's own 18x18 slot frame ({@code slotBorder}, which upstream likewise draws over that
 * zone because the art there has no frames).
 */
final class AssemblyCategory implements IRecipeCategory<AssemblyRecipe> {
    static final RecipeType<AssemblyRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "tool_assembly", AssemblyRecipe.class);
    static final RecipeType<AssemblyRecipe> LARGE_TYPE =
            RecipeType.create(Forgeweave.MODID, "large_tool_assembly", AssemblyRecipe.class);
    static final RecipeType<AssemblyRecipe> ARMOR_TYPE =
            RecipeType.create(Forgeweave.MODID, "armor_assembly", AssemblyRecipe.class);

    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.ASSEMBLY;
    private static final int WIDTH = PANEL.width();
    private static final int HEIGHT = PANEL.height();

    /** Upstream's own result slot -- `addSlot(OUTPUT, WIDTH - 26, 23)`. */
    private static final int RESULT_X = WIDTH - 26;
    private static final int RESULT_Y = 23;
    /** The blank zone upstream fills with its 3D preview -- `itemCover.draw(graphics, 5, 6)`, 70x60. */
    private static final int PART_ZONE_WIDTH = 70;
    /** One 16x16 slot plus the 1px its 18x18 frame adds on each side, plus 2px of breathing room. */
    private static final int SLOT_PITCH = 20;
    /** Two columns keep four parts (the Tool Forge tier) clear of upstream's arrow at x=74. */
    private static final int MAX_COLUMNS = 2;

    private final RecipeType<AssemblyRecipe> type;
    private final Component title;
    private final IDrawable icon;
    private final IDrawable background;
    private final IDrawable slotFrame;

    AssemblyCategory(IGuiHelper helper, RecipeType<AssemblyRecipe> type, Component title, ItemStack catalystIcon) {
        this.type = type;
        this.title = title;
        icon = helper.createDrawableItemStack(catalystIcon);
        background = JeiCategoryChrome.panel(helper, PANEL);
        slotFrame = JeiCategoryChrome.slotFrame(helper);
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
        // One slot per part, in the station's own slot order -- two for M3's two-part weapons, four
        // for the Tool Forge tier (issue #155), so the grid grows with the tool.
        int count = recipe.parts().size();
        for (int slot = 0; slot < count; slot++) {
            builder.addInputSlot(partX(count, slot), partY(count, slot)).addItemStacks(recipe.parts().get(slot));
        }
        builder.addOutputSlot(RESULT_X, RESULT_Y).addItemStack(recipe.result());
    }

    @Override
    public void draw(AssemblyRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // The arrow and the output slot's frame are baked into upstream's own panel; the part zone is blank.
        background.draw(guiGraphics, 0, 0);
        int count = recipe.parts().size();
        for (int slot = 0; slot < count; slot++) {
            JeiCategoryChrome.drawSlotFrame(slotFrame, guiGraphics, partX(count, slot), partY(count, slot));
        }
    }

    /** The grid is centred in {@link #PART_ZONE_WIDTH}, so two parts sit either side of its middle. */
    private static int partX(int count, int slot) {
        int columns = Math.min(count, MAX_COLUMNS);
        return (PART_ZONE_WIDTH - columns * SLOT_PITCH) / 2 + (slot % MAX_COLUMNS) * SLOT_PITCH;
    }

    /** ...and centred in the panel's own height, so a two-part tool lines up with the output slot. */
    private static int partY(int count, int slot) {
        int rows = (count + MAX_COLUMNS - 1) / MAX_COLUMNS;
        return (HEIGHT - rows * SLOT_PITCH) / 2 + (slot / MAX_COLUMNS) * SLOT_PITCH;
    }
}
