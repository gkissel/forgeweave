package dev.gkissel.forgeweave.jei;

import java.util.HashMap;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * The live entity the entity-melting category draws in its left basin (issue #931), shared by the
 * JEI and EMI categories (issue #971) and free of either's types for {@link RecipeViewerContent}'s
 * reason.
 *
 * <p>Upstream renders the entity through a Mantle custom JEI ingredient type ({@code
 * EntityIngredientRenderer}), machinery this project has no equivalent of, so this draws it directly
 * with vanilla's own {@code GuiGraphicsExtractor#entity}, the call {@code InventoryScreen} itself
 * ends in, without the mouse-following rotation it adds on top. It needs only a live {@link
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
        // 26.1 draws entities as a picture-in-picture into a screen-space box centred on the old
        // render origin, and that box ignores the pose stack, so map the corner through it first.
        Vector2f corner = guiGraphics.pose().transformPosition(x, y, new Vector2f());
        int x0 = Math.round(corner.x);
        int y0 = Math.round(corner.y);
        EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 1f);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        guiGraphics.entity(state, scale, offset, pose, null, x0, y0, x0 + size, y0 + size);
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
        return renderEntities.computeIfAbsent(type, t -> {
            if (!(t.create(level, EntitySpawnReason.LOAD) instanceof LivingEntity living)) {
                return null;
            }
            // Never added to the level, so 26.2 never gives it an id; the renderer reads one. -1 is
            // what vanilla's BaseSpawner gives its own display entity.
            living.setId(-1);
            return living;
        });
    }
}
