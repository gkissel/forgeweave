package dev.gkissel.forgeweave.jei;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.recipe.CoreTransformRecipe;

/**
 * Pour-to-transform (issue #890, #845's {@code core_transform_recipe} registry): pouring
 * {@code fluid} over a {@code fromBlock} core turns it into {@code toBlock} once {@code amount} mB
 * has been poured. End and Deep Core have no crafting recipe at all -- {@code ForgeweaveItems}'
 * own javadoc on {@code END_CORE}/{@code DEEP_CORE} says so explicitly, "nothing in
 * ForgeweaveRecipeProvider for them" -- so before this category the only way to discover either was
 * to already know the mechanic; Standard and Nether Core need no such category, since their real
 * shaped-crafting recipes ({@code ForgeweaveRecipeProvider}) already surface in JEI's own vanilla
 * crafting category once {@code ForgeweaveItems.CRAFTING_STATION} is registered as its catalyst
 * ({@link ForgeweaveJeiPlugin#registerRecipeCatalysts}) -- confirmed, not fixed, by this issue.
 *
 * <p>Upstream has no pour-to-transform mechanic at all, so there is no category to derive from; this
 * borrows {@link JeiCategoryGeometry#CASTING}'s panel instead of adding a new one (the same "no
 * upstream counterpart" move {@code RepairCategory} and {@code EmbossingCategory} make), reusing its
 * fluid tank on the left and its cast/output slot positions for the from-block and to-block items.
 * The faucet -- the real in-world tool a player pours the fluid with -- stands in for the "station"
 * {@link CastingCategory} draws a table/basin picture for, at the very same on-panel position; the
 * category tab icon reuses the same faucet drawable rather than a second copy of it.
 */
final class CoreTransformCategory implements IRecipeCategory<CoreTransformRecipe> {
    static final RecipeType<CoreTransformRecipe> TYPE =
            RecipeType.create(Forgeweave.MODID, "core_transform", CoreTransformRecipe.class);

    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.CASTING;
    private static final int WIDTH = PANEL.width();
    private static final int HEIGHT = PANEL.height();

    /** Casting's own fluid tank -- `builder.addSlot(INPUT, 3, 3).setFluidRenderer(.., 32, 32)`. */
    private static final int FLUID_X = 3;
    private static final int FLUID_Y = 3;
    private static final int TANK_SIZE = 32;
    /** Casting's own tank overlay -- `createDrawable(casting.png, 133, 0, 32, 32)`. */
    private static final int OVERLAY_U = 133;
    private static final int OVERLAY_V = 0;
    /** Casting's own cast slot, repurposed for the from-block. */
    private static final int FROM_BLOCK_X = 38;
    private static final int FROM_BLOCK_Y = 19;
    /** Casting's own output slot, repurposed for the to-block. */
    private static final int TO_BLOCK_X = 93;
    private static final int TO_BLOCK_Y = 18;
    /** Casting's own arrow -- `drawableBuilder(casting.png, 117, 32, 24, 17)`, drawn at (58,18). */
    private static final int ARROW_U = 117;
    private static final int ARROW_V = 32;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_X = 58;
    private static final int ARROW_Y = 18;
    /**
     * Forgeweave has no fill-time concept for a core transform (it accumulates over real gameplay
     * time as the faucet drains its source, not over a fixed recipe duration), so the sweep runs on
     * one fixed period for every row, the same call {@link MeltingCategory} makes for the same reason.
     */
    private static final int ARROW_TICKS = 100;
    /** Casting's own station picture position, repurposed for the faucet item. */
    private static final int FAUCET_ICON_X = 38;
    private static final int FAUCET_ICON_Y = 35;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable background;
    private final IDrawable tankOverlay;

    CoreTransformCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(ForgeweaveItems.FAUCET.get()));
        arrow = helper.drawableBuilder(PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT)
                .buildAnimated(ARROW_TICKS, StartDirection.LEFT, false);
        background = JeiCategoryChrome.panel(helper, PANEL);
        tankOverlay = helper.createDrawable(PANEL.background(), OVERLAY_U, OVERLAY_V, TANK_SIZE, TANK_SIZE);
    }

    @Override
    public RecipeType<CoreTransformRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.forgeweave.core_transform");
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
    public void setRecipe(IRecipeLayoutBuilder builder, CoreTransformRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(FROM_BLOCK_X, FROM_BLOCK_Y).addItemStack(new ItemStack(recipe.fromBlock()));
        builder.addInputSlot(FLUID_X, FLUID_Y)
                .setFluidRenderer(recipe.amount(), false, TANK_SIZE, TANK_SIZE)
                .setOverlay(tankOverlay, 0, 0)
                .addFluidStack(recipe.fluid(), recipe.amount());
        builder.addOutputSlot(TO_BLOCK_X, TO_BLOCK_Y).addItemStack(new ItemStack(recipe.toBlock()));
    }

    @Override
    public void draw(CoreTransformRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);
        icon.draw(guiGraphics, FAUCET_ICON_X, FAUCET_ICON_Y);
    }
}
