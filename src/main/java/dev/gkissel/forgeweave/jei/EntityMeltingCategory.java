package dev.gkissel.forgeweave.jei;

import java.util.HashMap;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
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
 * so like {@link SmelteryFuelCategory} and {@link CoreTransformCategory} this borrows {@link
 * JeiCategoryGeometry#MELTING}'s panel rather than adding new art. The 1.20 clone's own JEI plugin
 * does have one ({@code plugin.jei.entity.EntityMeltingRecipeCategory}, MIT -- read for the idea,
 * never copied, per CLAUDE.md's "adapt the rendering approach" instruction on this issue): it renders
 * the entity through a Mantle-supplied custom JEI ingredient type
 * ({@code slimeknights.mantle.plugin.jei.entity.EntityIngredientRenderer}), machinery this project
 * has no equivalent of and that issue #931 does not ask for wholesale. Instead this category draws
 * the entity directly in {@link #draw}, the same way {@link MeltingCategory} draws its temperature
 * text and {@link SmelteryFuelCategory} draws its animated arrow -- vanilla's own {@code
 * InventoryScreen#renderEntityInInventory} (the lower-level call, not the mouse-following wrapper:
 * that one calls {@code GuiGraphics#enableScissor} with coordinates in absolute window-pixel space,
 * which would clip at the wrong place here since JEI has already translated the pose stack to the
 * category's on-screen position by the time {@link #draw} runs) needs only a live {@link
 * LivingEntity} instance, which {@link EntityType#create} builds against the client level for
 * rendering purposes alone -- it is never added to the world.
 */
final class EntityMeltingCategory implements IRecipeCategory<EntityMeltingDisplay> {
    static final RecipeType<EntityMeltingDisplay> TYPE =
            RecipeType.create(Forgeweave.MODID, "entity_melting", EntityMeltingDisplay.class);

    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.MELTING;
    private static final int WIDTH = PANEL.width();
    private static final int HEIGHT = PANEL.height();

    /** Open area on the panel's left where {@link MeltingCategory}'s item input slot would sit. */
    private static final int ENTITY_CENTER_X = 24;
    private static final int ENTITY_CENTER_Y = 30;
    private static final int ENTITY_HOVER_X0 = 4;
    private static final int ENTITY_HOVER_Y0 = 2;
    private static final int ENTITY_HOVER_X1 = 44;
    private static final int ENTITY_HOVER_Y1 = 38;
    /** Rendered pixel height every entity is scaled to, regardless of its own bounding box. */
    private static final float ENTITY_TARGET_HEIGHT = 28f;

    /** Melting's own fluid output tank -- see {@link MeltingCategory}'s own constants for the source rect. */
    private static final int FLUID_X = 96;
    private static final int FLUID_Y = 4;
    private static final int FLUID_SIZE = 32;
    private static final int OVERLAY_U = 132;
    private static final int OVERLAY_V = 0;
    /** Melting's own arrow crop -- see {@link MeltingCategory}'s own constants for the source rect. */
    private static final int ARROW_U = 150;
    private static final int ARROW_V = 41;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_X = 56;
    private static final int ARROW_Y = 18;
    private static final int ARROW_TICKS = 100;

    /** Melting's own temperature row, repurposed: every row deals the same flat damage, so this is fixed text. */
    private static final int DAMAGE_CENTER_X = 56;
    private static final int DAMAGE_Y = 3;
    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable background;
    private final IDrawable tankOverlay;
    private final Component damageText;
    /** One instance per entity type, built lazily and reused across frames rather than every draw. */
    private final Map<EntityType<?>, LivingEntity> renderEntities = new HashMap<>();

    EntityMeltingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(Items.ZOMBIE_SPAWN_EGG));
        arrow = helper.drawableBuilder(PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT)
                .buildAnimated(ARROW_TICKS, StartDirection.LEFT, false);
        background = JeiCategoryChrome.panel(helper, PANEL);
        tankOverlay = helper.createDrawable(PANEL.background(), OVERLAY_U, OVERLAY_V, FLUID_SIZE, FLUID_SIZE);
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
                .setFluidRenderer(recipe.amount(), false, FLUID_SIZE, FLUID_SIZE)
                .setOverlay(tankOverlay, 0, 0)
                .addFluidStack(recipe.fluid(), recipe.amount())
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.category.forgeweave.entity_melting.per_hit")));
    }

    @Override
    public void draw(EntityMeltingDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);
        JeiCategoryChrome.drawCentered(guiGraphics, Minecraft.getInstance().font, damageText,
                DAMAGE_CENTER_X, DAMAGE_Y, TEXT_COLOR, false);

        LivingEntity entity = renderEntity(recipe.primaryEntity());
        if (entity != null) {
            float scale = ENTITY_TARGET_HEIGHT / Math.max(1f, entity.getBbHeight());
            Vector3f offset = new Vector3f(0f, entity.getBbHeight() / 2f, 0f);
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
            InventoryScreen.renderEntityInInventory(guiGraphics, ENTITY_CENTER_X, ENTITY_CENTER_Y, scale, offset, pose, null, entity);
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, EntityMeltingDisplay recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX >= ENTITY_HOVER_X0 && mouseX <= ENTITY_HOVER_X1 && mouseY >= ENTITY_HOVER_Y0 && mouseY <= ENTITY_HOVER_Y1) {
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
