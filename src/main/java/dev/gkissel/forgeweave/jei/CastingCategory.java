package dev.gkissel.forgeweave.jei;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import dev.gkissel.forgeweave.casting.CastingRecipe;

/**
 * Shared layout for the Casting Table and Casting Basin JEI categories (docs/SCOPE.md M2 issue
 * #100's one-registry-two-stations shape, JEI split per issue #109): cast (if the recipe requires
 * one -- a basin block-casting recipe like {@code block_iron.json} requires an <em>empty</em> block
 * instead, so its display recipe just has no cast slot) plus a poured fluid produce the result item.
 * {@link CastingTableCategory} and {@link CastingBasinCategory} differ only in icon, title and
 * {@code RecipeType} id, so a shared base is what keeps that identical layout from being copied
 * twice rather than a speculative abstraction over unrelated categories.
 *
 * <p>Casting has no menu at all -- both blocks are operated in-world, by right-clicking with an item
 * held and pouring a fluid over them, not through a GUI with slots -- so unlike {@link
 * AssemblyCategory}/{@link RepairCategory} neither casting category gets a recipe-click transfer
 * button (docs/SCOPE.md M1 issue #40's transfer only applies where a menu has slots to fill).
 *
 * <p>Issue #785: background and arrow are derived from upstream's own {@code
 * AbstractCastingCategory} (`~/development/minecraft/references/tinkers-1.20` @ de26560d, MIT --
 * NOTICE.md). Upstream's fluid tank is a 32x32 render with a frame overlay; this category keeps its
 * own fixed 16x16 tank icon (no overlay asset derived), placed at upstream's tank origin.
 */
abstract class CastingCategory implements IRecipeCategory<CastingRecipe> {
    /** Upstream {@code AbstractCastingCategory}'s own background: `textures/gui/jei/casting.png`, (0,0,117,54). */
    static final ResourceLocation BACKGROUND_LOC = JeiCategoryGeometry.CASTING.background();
    static final int WIDTH = JeiCategoryGeometry.CASTING.width();
    static final int HEIGHT = JeiCategoryGeometry.CASTING.height();

    private static final int TANK_SIZE = 16;
    /** Upstream's cast slot -- `builder.addSlot(..., 38, 19)`. */
    private static final int CAST_X = 38;
    private static final int CAST_Y = 3;
    /** Upstream's fluid tank origin -- `builder.addSlot(INPUT, 3, 3, ...)`. */
    private static final int FLUID_X = 3;
    private static final int FLUID_Y = 21;
    /** Upstream's own output slot -- `builder.addSlot(OUTPUT, 93, 18)`. */
    private static final int OUTPUT_X = 93;
    private static final int OUTPUT_Y = 18;
    /** Upstream's own arrow position -- `cachedArrows...draw(graphics, 58, 18)`. */
    private static final int ARROW_X = 58;

    private final RecipeType<CastingRecipe> type;
    private final Component title;
    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable background;

    CastingCategory(IGuiHelper helper, RecipeType<CastingRecipe> type, ItemLike iconItem, String titleKey) {
        this.type = type;
        this.title = Component.translatable(titleKey);
        this.icon = helper.createDrawableItemStack(new ItemStack(iconItem));
        this.arrow = helper.getRecipeArrow();
        this.background = helper.createDrawable(BACKGROUND_LOC, 0, 0, WIDTH, HEIGHT);
    }

    @Override
    public RecipeType<CastingRecipe> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, CastingRecipe recipe, IFocusGroup focuses) {
        recipe.cast().ifPresent(cast -> {
            IRecipeSlotBuilder castSlot = builder.addInputSlot(CAST_X, CAST_Y).addIngredients(cast);
            castSlot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(recipe.consumesCast()
                    ? "jei.category.forgeweave.casting.cast_consumed"
                    : "jei.category.forgeweave.casting.cast_reusable")));
        });
        // One fluid for a normal recipe; every fluid the container takes for the fluid-agnostic
        // bucket recipe (#604), which JEI then cycles through in step with its filled results.
        IRecipeSlotBuilder fluidSlot = builder.addInputSlot(FLUID_X, FLUID_Y)
                .setFluidRenderer(recipe.amount(), false, TANK_SIZE, TANK_SIZE);
        IRecipeSlotBuilder resultSlot = builder.addOutputSlot(OUTPUT_X, OUTPUT_Y);
        for (Fluid poured : recipe.displayFluids()) {
            fluidSlot.addFluidStack(poured, recipe.amount());
            resultSlot.addItemStack(recipe.resultFor(poured));
        }
    }

    @Override
    public void draw(CastingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrow.draw(guiGraphics, ARROW_X, (HEIGHT - arrow.getHeight()) / 2);
    }
}
