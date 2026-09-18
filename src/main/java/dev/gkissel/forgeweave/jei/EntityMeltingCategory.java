package dev.gkissel.forgeweave.jei;

import java.util.HashMap;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.recipe.EntityMeltingRecipe;

/**
 * Entity melting: what a living entity standing in a smeltery melts into (issue #931, {@code
 * entity_melting_recipe} registry read by {@link EntityMeltingRecipe#find}, plus one synthetic row
 * for {@link EntityMeltingRecipe#defaultResult}). Previously invisible in JEI entirely -- the
 * mechanic had no category at all, the same gap #890 closed for smeltery fuel and pour-to-transform.
 *
 * <p>Upstream has no entity-melting mechanic in the 1.12 generation this project otherwise mirrors,
 * so the 1.20 clone's {@code plugin.jei.entity.EntityMeltingRecipeCategory} is the parity target, and
 * this category uses its panel and positions ({@link JeiCategoryGeometry#ENTITY_MELTING}, already on
 * the derived {@code melting.png}): the entity in the left basin's 32x32 opening, the damage in
 * hearts against the heart icon, the fluid in the right basin, the usable fuels in the tank under the
 * arrow. Issue #1029: it used to borrow the item melting panel, where the damage sentence ran under
 * the tank and the entity hung over an item slot and out of the recipe.
 *
 * <p>The one thing not mirrored is how the entity gets on screen. Upstream renders it through a
 * Mantle custom JEI ingredient type ({@code EntityIngredientRenderer}), machinery this project has no
 * equivalent of. This category draws it directly in {@link #draw} with vanilla's own {@code
 * InventoryScreen#renderEntityInInventory} (the lower-level call, not the mouse-following wrapper:
 * that one calls {@code GuiGraphics#enableScissor} with coordinates in absolute window-pixel space,
 * which would clip at the wrong place here since JEI has already translated the pose stack to the
 * category's on-screen position by the time {@link #draw} runs). It needs only a live {@link
 * LivingEntity} instance, which {@link EntityType#create} builds against the client level for
 * rendering alone; it is never added to the world.
 */
final class EntityMeltingCategory implements IRecipeCategory<EntityMeltingDisplay> {
    static final RecipeType<EntityMeltingDisplay> TYPE =
            RecipeType.create(Forgeweave.MODID, "entity_melting", EntityMeltingDisplay.class);

    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.ENTITY_MELTING;
    private static final int WIDTH = PANEL.width();
    private static final int HEIGHT = PANEL.height();

    // Every position below is upstream's own (EntityMeltingRecipeCategory), against its own panel.
    /** The left basin's opening: upstream's entity slot at (19, 11), {@code EntityIngredientRenderer(32)}. */
    private static final int ENTITY_X = 19;
    private static final int ENTITY_Y = 11;
    private static final int ENTITY_BOX = 32;
    /** Leaves a 2px margin inside the opening, in both directions, so a wide mob fits as well as a tall one. */
    private static final float ENTITY_FIT = ENTITY_BOX - 4f;

    /** The right basin's opening: upstream's output slot, a 16x32 fluid column. */
    private static final int FLUID_X = 115;
    private static final int FLUID_Y = 11;
    private static final int FLUID_WIDTH = 16;
    private static final int FLUID_HEIGHT = 32;
    /** The fuel tank under the arrow: upstream's catalyst slot and its 16x16 overlay crop. */
    private static final int FUEL_X = 75;
    private static final int FUEL_Y = 43;
    private static final int FUEL_SIZE = 16;
    private static final int FUEL_OVERLAY_U = 150;
    private static final int FUEL_OVERLAY_V = 74;
    private static final int ICON_U = 174;
    private static final int ICON_V = 41;
    private static final int ARROW_U = 150;
    private static final int ARROW_V = 41;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_X = 71;
    private static final int ARROW_Y = 21;
    private static final int ARROW_TICKS = 200;

    /** Upstream right-aligns the damage, in hearts, against the heart baked into the panel, in red. */
    private static final int DAMAGE_RIGHT_X = 84;
    private static final int DAMAGE_Y = 8;
    private static final int DAMAGE_COLOR = 0xFF0000;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable background;
    private final IDrawable fuelOverlay;
    private final String damageHearts;
    private final Component damageText;
    /** One instance per entity type, built lazily and reused across frames rather than every draw. */
    private final Map<EntityType<?>, LivingEntity> renderEntities = new HashMap<>();

    EntityMeltingCategory(IGuiHelper helper) {
        icon = helper.createDrawable(PANEL.background(), ICON_U, ICON_V, 16, 16);
        arrow = helper.drawableBuilder(PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT)
                .buildAnimated(ARROW_TICKS, StartDirection.LEFT, false);
        background = JeiCategoryChrome.panel(helper, PANEL);
        fuelOverlay = helper.createDrawable(PANEL.background(), FUEL_OVERLAY_U, FUEL_OVERLAY_V, FUEL_SIZE, FUEL_SIZE);
        // Upstream: Float.toString(damage / 2f), hearts rather than half-hearts.
        damageHearts = Float.toString(EntityMeltingRecipe.DAMAGE / 2f);
        // The recipe's own field is a float purely so EntityMeltingRecipe#DAMAGE reads as "half a
        // heart's worth of hearts" upstream-style; every shipped and default row is a whole 2.
        damageText = Component.translatable("jei.category.forgeweave.entity_melting.damage", (int) EntityMeltingRecipe.DAMAGE);
    }

    @Override
    public RecipeType<EntityMeltingDisplay> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.forgeweave.entity_melting");
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
    public void setRecipe(IRecipeLayoutBuilder builder, EntityMeltingDisplay recipe, IFocusGroup focuses) {
        builder.addOutputSlot(FLUID_X, FLUID_Y)
                .setFluidRenderer(recipe.amount(), false, FLUID_WIDTH, FLUID_HEIGHT)
                .addFluidStack(recipe.fluid(), recipe.amount())
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(damageText);
                    tooltip.add(Component.translatable("jei.category.forgeweave.entity_melting.per_hit"));
                });
        var fuelSlot = builder.addSlot(RecipeIngredientRole.CATALYST, FUEL_X, FUEL_Y)
                .setFluidRenderer(1, false, FUEL_SIZE, FUEL_SIZE)
                .setOverlay(fuelOverlay, 0, 0);
        for (Fluid fuel : recipe.fuels()) {
            fuelSlot.addFluidStack(fuel, 1);
        }
    }

    @Override
    public void draw(EntityMeltingDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);
        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, damageHearts, DAMAGE_RIGHT_X - font.width(damageHearts), DAMAGE_Y, DAMAGE_COLOR, false);

        LivingEntity entity = renderEntity(recipe.primaryEntity());
        if (entity != null) {
            // Fit the opening both ways: a ghast or an iron golem is as wide as it is tall.
            float scale = ENTITY_FIT / Math.max(1f, Math.max(entity.getBbHeight(), entity.getBbWidth()));
            Vector3f offset = new Vector3f(0f, entity.getBbHeight() / 2f, 0f);
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
            InventoryScreen.renderEntityInInventory(guiGraphics, ENTITY_X + ENTITY_BOX / 2f, ENTITY_Y + ENTITY_BOX / 2f,
                    scale, offset, pose, null, entity);
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, EntityMeltingDisplay recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX >= ENTITY_X && mouseX < ENTITY_X + ENTITY_BOX && mouseY >= ENTITY_Y && mouseY < ENTITY_Y + ENTITY_BOX) {
            tooltip.add(recipe.defaultRow()
                    ? Component.translatable("jei.category.forgeweave.entity_melting.default")
                    : recipe.primaryEntity().getDescription());
        }
    }

    /**
     * Lazily built and cached per type -- {@link EntityType#create} needs a {@link
     * net.minecraft.world.level.Level}, so nothing renders before a world is joined; in practice that
     * never matters, since {@link EntityMeltingRecipes#build} (via {@code ForgeweaveJeiPlugin}) never
     * produces a recipe to draw before then either.
     */
    private LivingEntity renderEntity(EntityType<?> type) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return renderEntities.computeIfAbsent(type, t -> {
            var created = t.create(level);
            return created instanceof LivingEntity living ? living : null;
        });
    }
}
