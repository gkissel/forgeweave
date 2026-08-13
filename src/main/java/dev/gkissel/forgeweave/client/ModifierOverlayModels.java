package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ModifierArt;

/**
 * Renders applied modifiers as overlay layers on the tool item (issue #257), upstream 1.12's
 * {@code BakedToolModel#addModifierQuads} adapted to NeoForge's baked-model pipeline.
 *
 * <p><b>Upstream behavior, mirrored here:</b> every modifier on the tool that has overlay art
 * renders, in application order, with <b>no cap</b> -- upstream walks the whole base modifiers tag
 * list and appends quads for each id its {@code modifierParts} map knows ({@code
 * addModifierQuads}); a modifier without art simply draws nothing. The overlays are untinted
 * (upstream bakes them through a plain {@code ItemLayerModel} retexture; only {@code
 * hasTexturePerMaterial} modifiers tint, and Forgeweave ships none).
 *
 * <p><b>Mechanism:</b> the tools' generated {@code item/handheld} layer models stay untouched;
 * {@link ModelEvent.ModifyBakingResult} wraps each tool's baked model so its {@link ItemOverrides}
 * resolve per stack -- the same seam vanilla uses for bow pull states, and the least invasive hook
 * over the existing generated models. A stack with overlay-bearing modifiers resolves to a cached
 * composed model whose unculled quad list is the tool's own quads plus one baked sprite layer per
 * modifier ({@link UnbakedGeometryHelper#createUnbakedItemElements}, the same quads a model JSON
 * layer would bake to). The overlay sprites are stitched by the existing {@code derived/tools}
 * directory atlas source ({@code assets/minecraft/atlases/blocks.json}), which walks its
 * subdirectories.
 *
 * <p><b>Z-fighting:</b> upstream scales every modifier layer up slightly in depth so it always sits
 * above the tool's own layers ({@code ModifierModel#bakeModels}, {@code s = 0.025}); the {@link
 * #OVERLAY_DEPTH_STATE} scale reproduces that (vanilla applies model-state transforms about the
 * block center, so a bare z-scale is upstream's translate-plus-scale in one). As upstream, all
 * overlays share the one transform: two overlapping overlay pixels resolve by draw order, which is
 * application order.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModifierOverlayModels {

    /** Upstream {@code ModifierModel#bakeModels}'s {@code s}: how much deeper the overlay box is. */
    private static final float OVERLAY_DEPTH_GROWTH = 0.025f;

    /** See the class javadoc; {@code ModelState}'s uv-lock default is untouched. */
    private static final ModelState OVERLAY_DEPTH_STATE = new ModelState() {
        private final Transformation transformation =
                new Transformation(null, null, new Vector3f(1.0f, 1.0f, 1.0f + OVERLAY_DEPTH_GROWTH), null);

        @Override
        public Transformation getRotation() {
            return transformation;
        }
    };

    /**
     * Composed models by (tool model, overlay list), so a frame re-render is a map hit -- upstream
     * cached the same way ({@code BakedToolModel}'s Guava cache). Cleared on every rebake, since
     * cached quads hold sprites of the previous atlas.
     */
    private static final Map<CacheKey, BakedModel> COMPOSED = new ConcurrentHashMap<>();

    private record CacheKey(BakedModel base, List<ResourceLocation> overlays) {}

    @SubscribeEvent
    static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        COMPOSED.clear();
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            ModelResourceLocation key =
                    ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(entry.tool().get()));
            BakedModel base = event.getModels().get(key);
            if (base != null) {
                event.getModels().put(key, new OverlayAwareModel(base, entry.constants().id()));
            }
        }
    }

    /**
     * The wrapper every tool model gets: identical to the tool's own model until its overrides
     * resolve a stack that carries overlay-bearing modifiers.
     */
    private static final class OverlayAwareModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                    @Nullable LivingEntity entity, int seed) {
                List<ResourceLocation> overlays = overlaySprites(tool, stack);
                if (overlays.isEmpty()) {
                    return originalModel;
                }
                return COMPOSED.computeIfAbsent(new CacheKey(originalModel, overlays),
                        cacheKey -> compose(originalModel, overlays));
            }
        };
        private final String tool;

        OverlayAwareModel(BakedModel original, String tool) {
            super(original);
            this.tool = tool;
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }

    /**
     * The overlay sprites {@code stack}'s modifiers draw, in application order (upstream's own
     * order: the base modifiers tag list is append-only). A sprite missing from the atlas is
     * skipped defensively, as upstream skips ids its {@code modifierParts} map lacks --
     * {@code ModifierArtTest} is what makes that unreachable for shipped modifiers.
     */
    private static List<ResourceLocation> overlaySprites(String tool, ItemStack stack) {
        List<ResourceLocation> overlays = List.of();
        for (ModifierEntry entry : ForgeweaveModifiers.of(stack)) {
            String texture = ModifierArt.overlay(tool, entry.id());
            if (texture == null) {
                continue;
            }
            ResourceLocation sprite = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, texture);
            if (blockAtlasSprite(sprite).contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                continue;
            }
            if (overlays.isEmpty()) {
                overlays = new ArrayList<>(2);
            }
            overlays.add(sprite);
        }
        return overlays;
    }

    /** The tool's own quads plus one untinted baked layer per overlay; see the class javadoc. */
    private static BakedModel compose(BakedModel base, List<ResourceLocation> overlays) {
        List<BakedQuad> quads = new ArrayList<>(base.getQuads(null, null, RandomSource.create(0L)));
        for (ResourceLocation overlay : overlays) {
            TextureAtlasSprite sprite = blockAtlasSprite(overlay);
            // Tint index -1: overlays are untinted; the part layers' material tint must not bleed in.
            quads.addAll(UnbakedGeometryHelper.bakeElements(
                    UnbakedGeometryHelper.createUnbakedItemElements(-1, sprite),
                    material -> sprite, OVERLAY_DEPTH_STATE));
        }
        List<BakedQuad> composedQuads = List.copyOf(quads);
        return new BakedModelWrapper<>(base) {
            @Override
            public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
                return side == null ? composedQuads : super.getQuads(state, side, rand);
            }

            @Override
            public ItemOverrides getOverrides() {
                return ItemOverrides.EMPTY; // Already resolved; there is nothing further to resolve.
            }

            // BakedModelWrapper delegates these two to the *original* model object, which would drop
            // the overlay quads the moment the renderer follows the returned model.
            @Override
            public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
                super.applyTransform(context, poseStack, leftHand);
                return this;
            }

            @Override
            public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
                return List.of(this);
            }
        };
    }

    private static TextureAtlasSprite blockAtlasSprite(ResourceLocation sprite) {
        return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(sprite);
    }

    private ModifierOverlayModels() {}
}
