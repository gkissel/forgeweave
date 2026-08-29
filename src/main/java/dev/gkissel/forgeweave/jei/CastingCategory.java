package dev.gkissel.forgeweave.jei;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import dev.gkissel.forgeweave.casting.CastingRecipe;

/**
 * Shared layout for the Casting Table and Casting Basin JEI categories (docs/SCOPE.md M2 issue
 * #100's one-registry-two-stations shape, JEI split per issue #109): cast (if the recipe requires
 * one -- a basin block-casting recipe like {@code block_iron.json} requires an <em>empty</em> block
 * instead, so its display recipe just has no cast slot) plus a poured fluid produce the result item.
 * {@link CastingTableCategory} and {@link CastingBasinCategory} differ only in icon, title, station
 * block picture and {@code RecipeType} id, so a shared base is what keeps that identical layout from
 * being copied twice rather than a speculative abstraction over unrelated categories.
 *
 * <p>Casting has no menu at all -- both blocks are operated in-world, by right-clicking with an item
 * held and pouring a fluid over them, not through a GUI with slots -- so unlike {@link
 * AssemblyCategory}/{@link RepairCategory} neither casting category gets a recipe-click transfer
 * button (docs/SCOPE.md M1 issue #40's transfer only applies where a menu has slots to fill).
 *
 * <p>Issue #785 derived this background from upstream's own {@code AbstractCastingCategory}
 * (`~/development/minecraft/references/tinkers-1.20` @ de26560d, MIT -- NOTICE.md). Issue #804 makes
 * everything drawn on it upstream's too, all out of the same 256x256 `casting.png` (so no new asset
 * and no new NOTICE.md row):
 *
 * <ul>
 *   <li>The tank is upstream's 32x32 {@code setFluidRenderer} at (3,3) with its graduation overlay,
 *       not a 16x16 swatch parked halfway down the silhouette at y=21.
 *   <li>The cast sits at upstream's (38,19), under the faucet the background draws at (38,3) --
 *       it used to be drawn at (38,3), on top of that faucet.
 *   <li>The fluid pouring out of the faucet ({@code addSlot(RENDER_ONLY, 43, 8)}, 6px wide and
 *       taller when no cast is in the way) and the station block picture at (38,35) are drawn, so
 *       the middle of the panel is no longer bare art with nothing in it.
 *   <li>The arrow is upstream's animated crop at (58,18), and the cooling time is centred on x=72
 *       above it the way {@code AbstractCastingCategory#draw} centres its own.
 *   <li>The cast-consumed/cast-kept badge at (63,39) draws upstream's own icon; the tooltip that
 *       already said the same thing stays on the cast slot.
 * </ul>
 */
abstract class CastingCategory implements IRecipeCategory<CastingRecipe> {
    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.CASTING;
    private static final int WIDTH = PANEL.width();
    private static final int HEIGHT = PANEL.height();

    /** Upstream's fluid tank -- `builder.addSlot(INPUT, 3, 3).setFluidRenderer(.., 32, 32)`. */
    private static final int FLUID_X = 3;
    private static final int FLUID_Y = 3;
    private static final int TANK_SIZE = 32;
    /** Upstream's own tank overlay -- `createDrawable(casting.png, 133, 0, 32, 32)`. */
    private static final int OVERLAY_U = 133;
    private static final int OVERLAY_V = 0;
    /** Upstream's pouring fluid -- `builder.addSlot(RENDER_ONLY, 43, 8).setFluidRenderer(1, false, 6, h)`. */
    private static final int FAUCET_X = 43;
    private static final int FAUCET_Y = 8;
    private static final int FAUCET_WIDTH = 6;
    /** Upstream's `int h = 11; if (!recipe.hasCast()) h += 16;` -- the pour reaches further with no cast in the way. */
    private static final int FAUCET_HEIGHT_WITH_CAST = 11;
    private static final int FAUCET_HEIGHT_NO_CAST = 27;
    /** Upstream's cast slot -- `builder.addSlot(.., 38, 19)`. */
    private static final int CAST_X = 38;
    private static final int CAST_Y = 19;
    /** Upstream's own output slot -- `builder.addSlot(OUTPUT, 93, 18)`. */
    private static final int OUTPUT_X = 93;
    private static final int OUTPUT_Y = 18;
    /** Upstream's own arrow -- `drawableBuilder(casting.png, 117, 32, 24, 17)`, drawn at (58,18). */
    private static final int ARROW_U = 117;
    private static final int ARROW_V = 32;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_X = 58;
    private static final int ARROW_Y = 18;
    /** Upstream's station picture -- 16x16 out of `casting.png`, drawn at (38,35). */
    private static final int BLOCK_SIZE = 16;
    private static final int BLOCK_X = 38;
    private static final int BLOCK_Y = 35;
    /** Upstream's `(casting.png, 117, 0, 16, 16)` table / `(117, 16, ..)` basin column. */
    private static final int BLOCK_U = 117;
    /** Upstream {@code CastingTableCategory}'s own v -- `createDrawable(BACKGROUND_LOC, 117, 0, 16, 16)`. */
    static final int BLOCK_V_TABLE = 0;
    /** Upstream {@code CastingBasinCategory}'s own v -- `createDrawable(BACKGROUND_LOC, 117, 16, 16, 16)`. */
    static final int BLOCK_V_BASIN = 16;
    /** Upstream's cast badges -- `(casting.png, 141, 32, 13, 11)` consumed / `(141, 43, ..)` kept, at (63,39). */
    private static final int BADGE_U = 141;
    private static final int BADGE_CONSUMED_V = 32;
    private static final int BADGE_KEPT_V = 43;
    private static final int BADGE_WIDTH = 13;
    private static final int BADGE_HEIGHT = 11;
    private static final int BADGE_X = 63;
    private static final int BADGE_Y = 39;
    /** Upstream's cooling row -- `int x = 72 - font.width(coolingString) / 2; drawString(.., x, 2, ..)`. */
    private static final int COOLING_CENTER_X = 72;
    private static final int COOLING_Y = 2;
    private static final int TEXT_COLOR = 0x404040;
    private static final int TICKS_PER_SECOND = 20;

    private final RecipeType<CastingRecipe> type;
    private final Component title;
    private final IGuiHelper helper;
    private final IDrawable icon;
    private final IDrawable background;
    private final IDrawable tankOverlay;
    private final IDrawable block;
    private final IDrawable castConsumed;
    private final IDrawable castKept;
    /**
     * Upstream keeps one animated arrow per distinct cooling time in a Guava {@code LoadingCache};
     * the same thing with the JDK's own map, since the only reason for a cache here is that an
     * animation's period is baked into the drawable at build time.
     */
    private final Map<Integer, IDrawable> arrows = new HashMap<>();

    CastingCategory(IGuiHelper helper, RecipeType<CastingRecipe> type, ItemLike iconItem, String titleKey, int blockV) {
        this.type = type;
        this.helper = helper;
        this.title = Component.translatable(titleKey);
        this.icon = helper.createDrawableItemStack(new ItemStack(iconItem));
        this.background = JeiCategoryChrome.panel(helper, PANEL);
        this.tankOverlay = helper.createDrawable(PANEL.background(), OVERLAY_U, OVERLAY_V, TANK_SIZE, TANK_SIZE);
        this.block = helper.createDrawable(PANEL.background(), BLOCK_U, blockV, BLOCK_SIZE, BLOCK_SIZE);
        this.castConsumed = helper.createDrawable(PANEL.background(), BADGE_U, BADGE_CONSUMED_V, BADGE_WIDTH, BADGE_HEIGHT);
        this.castKept = helper.createDrawable(PANEL.background(), BADGE_U, BADGE_KEPT_V, BADGE_WIDTH, BADGE_HEIGHT);
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
        boolean hasCast = recipe.cast().isPresent();
        recipe.cast().ifPresent(cast -> {
            IRecipeSlotBuilder castSlot = builder.addInputSlot(CAST_X, CAST_Y).addIngredients(cast);
            castSlot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(recipe.consumesCast()
                    ? "jei.category.forgeweave.casting.cast_consumed"
                    : "jei.category.forgeweave.casting.cast_reusable")));
        });
        // One fluid for a normal recipe; every fluid the container takes for the fluid-agnostic
        // bucket recipe (#604), which JEI then cycles through in step with its filled results.
        IRecipeSlotBuilder fluidSlot = builder.addInputSlot(FLUID_X, FLUID_Y)
                .setFluidRenderer(recipe.amount(), false, TANK_SIZE, TANK_SIZE)
                .setOverlay(tankOverlay, 0, 0);
        IRecipeSlotBuilder faucetSlot = builder.addSlot(RecipeIngredientRole.RENDER_ONLY, FAUCET_X, FAUCET_Y)
                .setFluidRenderer(1, false, FAUCET_WIDTH,
                        hasCast ? FAUCET_HEIGHT_WITH_CAST : FAUCET_HEIGHT_NO_CAST);
        IRecipeSlotBuilder resultSlot = builder.addOutputSlot(OUTPUT_X, OUTPUT_Y);
        for (Fluid poured : recipe.displayFluids()) {
            fluidSlot.addFluidStack(poured, recipe.amount());
            faucetSlot.addFluidStack(poured, recipe.amount());
            resultSlot.addItemStack(recipe.resultFor(poured));
        }
    }

    @Override
    public void draw(CastingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        block.draw(guiGraphics, BLOCK_X, BLOCK_Y);
        if (recipe.cast().isPresent()) {
            (recipe.consumesCast() ? castConsumed : castKept).draw(guiGraphics, BADGE_X, BADGE_Y);
        }

        int coolingTicks = coolingTicks(recipe);
        arrows.computeIfAbsent(coolingTicks, ticks ->
                        helper.drawableBuilder(PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT)
                                .buildAnimated(ticks, StartDirection.LEFT, false))
                .draw(guiGraphics, ARROW_X, ARROW_Y);

        Font font = Minecraft.getInstance().font;
        Component cooling = Component.translatable("jei.category.forgeweave.casting.cooling_time",
                coolingTicks / TICKS_PER_SECOND);
        JeiCategoryChrome.drawCentered(guiGraphics, font, cooling, COOLING_CENTER_X, COOLING_Y, TEXT_COLOR, false);
    }

    /** The pour this row illustrates is the first fluid it displays, the one the tank slot starts on. */
    private static int coolingTicks(CastingRecipe recipe) {
        return recipe.displayFluids().stream().findFirst()
                .map(recipe::cooldownTicks)
                .orElse(1);
    }
}
