package dev.gkissel.forgeweave.emi;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.jei.JeiCategoryGeometry;

/**
 * One pour at a Casting Table or Casting Basin: the cast, if the recipe needs one, plus the poured
 * fluid -> the result. The JEI {@code CastingCategory}'s layout, upstream's {@code
 * AbstractCastingCategory}: the 32x32 tank at (3,3) under its graduation overlay, the fluid falling
 * from the faucet at (43,8), the cast at (38,19) with the station picture under it, the
 * consumed/kept badge, the animated arrow, the cooling time above it, and the result at (93,18).
 *
 * <p>One EMI recipe per poured fluid. JEI cycles the fluid-agnostic bucket recipe (#604) through
 * every fluid in one row, in step with its filled results; an EMI recipe has no way to tie a result
 * slot's cycle to a tank's, so each fluid gets its own row, with its own cooling time.
 */
final class CastingEmiRecipe extends BasicEmiRecipe {
    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.CASTING;
    private static final int FLUID_X = 3;
    private static final int FLUID_Y = 3;
    private static final int TANK_SIZE = 32;
    /** Upstream's own tank overlay -- `createDrawable(casting.png, 133, 0, 32, 32)`. */
    private static final int OVERLAY_U = 133;
    private static final int OVERLAY_V = 0;
    private static final int FAUCET_X = 43;
    private static final int FAUCET_Y = 8;
    private static final int FAUCET_WIDTH = 6;
    /** Upstream's `int h = 11; if (!recipe.hasCast()) h += 16;` -- the pour reaches further with no cast in the way. */
    private static final int FAUCET_HEIGHT_WITH_CAST = 11;
    private static final int FAUCET_HEIGHT_NO_CAST = 27;
    private static final int CAST_X = 38;
    private static final int CAST_Y = 19;
    private static final int OUTPUT_X = 93;
    private static final int OUTPUT_Y = 18;
    /** Upstream's own arrow -- `(casting.png, 117, 32, 24, 17)` at (58,18). */
    private static final int ARROW_U = 117;
    private static final int ARROW_V = 32;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_X = 58;
    private static final int ARROW_Y = 18;
    /** Upstream's station picture: a 16x16 crop at u=117 (v 0 table, 16 basin), drawn at (38,35). */
    private static final int BLOCK_SIZE = 16;
    private static final int BLOCK_U = 117;
    private static final int BLOCK_X = 38;
    private static final int BLOCK_Y = 35;
    static final int BLOCK_V_TABLE = 0;
    static final int BLOCK_V_BASIN = 16;
    /** Upstream's cast badges -- `(casting.png, 141, 32, 13, 11)` consumed / `(141, 43, ..)` kept, at (63,39). */
    private static final int BADGE_U = 141;
    private static final int BADGE_CONSUMED_V = 32;
    private static final int BADGE_KEPT_V = 43;
    private static final int BADGE_WIDTH = 13;
    private static final int BADGE_HEIGHT = 11;
    private static final int BADGE_X = 63;
    private static final int BADGE_Y = 39;
    private static final int COOLING_CENTER_X = 72;
    private static final int COOLING_Y = 2;
    private static final int TEXT_COLOR = 0x404040;
    private static final int TICKS_PER_SECOND = 20;

    private final CastingRecipe recipe;
    private final int blockV;
    private final int coolingTicks;
    private final EmiIngredient cast;
    private final EmiStack fluid;
    private final EmiStack result;

    CastingEmiRecipe(EmiRecipeCategory category, ResourceLocation id, CastingRecipe recipe, Fluid poured, int blockV) {
        super(category, id, PANEL.width(), PANEL.height());
        this.recipe = recipe;
        this.blockV = blockV;
        coolingTicks = recipe.cooldownTicks(poured);
        cast = recipe.cast().map(EmiIngredient::of).orElse(EmiStack.EMPTY);
        fluid = EmiStack.of(poured, recipe.amount());
        result = EmiStack.of(recipe.resultFor(poured));
        List<EmiIngredient> in = new ArrayList<>();
        if (!cast.isEmpty()) {
            in.add(cast);
        }
        in.add(fluid);
        inputs = List.copyOf(in);
        outputs = List.of(result);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiArt.panel(widgets, PANEL);
        EmiArt.texture(widgets, PANEL.background(), BLOCK_U, blockV, BLOCK_SIZE, BLOCK_SIZE, BLOCK_X, BLOCK_Y);
        boolean hasCast = !cast.isEmpty();
        if (hasCast) {
            EmiArt.texture(widgets, PANEL.background(), BADGE_U, recipe.consumesCast() ? BADGE_CONSUMED_V : BADGE_KEPT_V,
                    BADGE_WIDTH, BADGE_HEIGHT, BADGE_X, BADGE_Y);
        }
        EmiArt.arrow(widgets, PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT, ARROW_X, ARROW_Y, coolingTicks);
        EmiArt.centeredText(widgets, Component.translatable("jei.category.forgeweave.casting.cooling_time",
                coolingTicks / TICKS_PER_SECOND), COOLING_CENTER_X, COOLING_Y, TEXT_COLOR, false, PANEL.width());

        if (hasCast) {
            EmiArt.slot(widgets, cast, CAST_X, CAST_Y).appendTooltip(Component.translatable(recipe.consumesCast()
                    ? "jei.category.forgeweave.casting.cast_consumed"
                    : "jei.category.forgeweave.casting.cast_reusable"));
        }
        EmiArt.tank(widgets, fluid, FLUID_X, FLUID_Y, TANK_SIZE, TANK_SIZE, recipe.amount());
        EmiArt.texture(widgets, PANEL.background(), OVERLAY_U, OVERLAY_V, TANK_SIZE, TANK_SIZE, FLUID_X, FLUID_Y);
        // The pour itself, always drawn full: JEI's own render-only faucet slot.
        EmiArt.tank(widgets, fluid.copy(), FAUCET_X, FAUCET_Y, FAUCET_WIDTH,
                hasCast ? FAUCET_HEIGHT_WITH_CAST : FAUCET_HEIGHT_NO_CAST, recipe.amount());
        EmiArt.slot(widgets, result, OUTPUT_X, OUTPUT_Y).recipeContext(this);
    }
}
