package dev.gkissel.forgeweave.jei;

import java.util.HashMap;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * The live entity the entity-melting category draws in its left basin (issue #931), shared by the
 * JEI and EMI categories (issue #971) and free of either's types for {@link RecipeViewerContent}'s
 * reason.
 *
 * <p>Upstream renders the entity through a Mantle custom JEI ingredient type ({@code
 * EntityIngredientRenderer}), machinery this project has no equivalent of, so this draws it directly
 * with vanilla's own {@code InventoryScreen#renderEntityInInventory} -- the lower-level call, not the
 * mouse-following wrapper: that one calls {@code GuiGraphicsExtractor#enableScissor} with coordinates in
 * absolute window-pixel space, which would clip at the wrong place here since the viewer has already
 * translated the pose stack to the category's on-screen position. It needs only a live {@link
 * LivingEntity} instance, which {@link EntityType#create} builds against the client level for
 * rendering alone; it is never added to the world.
 */
public final class EntityPreview {
    /** Leaves a 2px margin inside the box, in both directions, so a wide mob fits as well as a tall one. */
    private static final float MARGIN = 4f;

    /** One instance per entity type, built lazily and reused across frames rather than every draw. */
    private final Map<EntityType<?>, LivingEntity> renderEntities = new HashMap<>();

    /** Draws {@code type} fitted into the {@code size}x{@code size} box whose corner is {@code (x, y)}. */
    public void draw(GuiGraphicsExtractor guiGraphics, EntityType<?> type, int x, int y, int size) {
        LivingEntity entity = renderEntity(type);
        if (entity == null) {
            return;
        }
        // Fit the box both ways: a ghast or an iron golem is as wide as it is tall.
        float scale = (size - MARGIN) / Math.max(1f, Math.max(entity.getBbHeight(), entity.getBbWidth()));
        Vector3f offset = new Vector3f(0f, entity.getBbHeight() / 2f, 0f);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        InventoryScreen.renderEntityInInventory(guiGraphics, x + size / 2f, y + size / 2f, scale, offset, pose, null, entity);
    }

    /**
     * Lazily built and cached per type -- {@link EntityType#create} needs a level, so nothing renders
     * before a world is joined; in practice that never matters, since {@link EntityMeltingRecipes#build}
     * never produces a recipe to draw before then either.
     */
    private LivingEntity renderEntity(EntityType<?> type) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return renderEntities.computeIfAbsent(type, t -> t.create(level) instanceof LivingEntity living ? living : null);
    }
}
