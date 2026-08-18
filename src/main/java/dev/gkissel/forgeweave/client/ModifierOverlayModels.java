package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;

import org.joml.Matrix4f;
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
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ModifierArt;
import dev.gkissel.forgeweave.tool.ToolArt;

/**
 * Renders what a tool's item model draws on top of its own layers: applied modifiers as overlay
 * layers (issue #257) and a bow's nocked ammo (T52, issue #483) -- upstream 1.12's {@code
 * BakedToolModel#addModifierQuads} and {@code BakedBowModel#addExtraQuads} adapted to NeoForge's
 * baked-model pipeline. The two live together here for upstream's own reason: they are two extra
 * quad lists appended by one baked model, resolved from one stack at one moment, and the ammo has to
 * know which draw stage the modifier overlays resolved to.
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
 *
 * <p><b>The nocked ammo</b> is the ammo item's <em>own baked model</em>, moved to the spot
 * {@link ToolArt#ammoPosition} names -- upstream wraps it in a Mantle {@code TRSRBakedModel} and
 * appends its quads, and {@link #nockedQuads} is that transform (rotation about the item's centre,
 * then the offset, which is what Mantle's {@code blockCenterToCorner} amounts to). It draws last, so
 * an arrow lies over both the tool and its overlays.
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

    /**
     * {@code ammo} is the ammo's <em>resolved model</em> rather than the stack (upstream keys on
     * item + meta + NBT): two stacks that bake the same model compose the same quads here, because
     * {@link #nockedQuads} drops the tint the stack could otherwise vary. {@code stage} carries the
     * ammo's position, which {@code base} alone does not -- a bow's model is one instance per item,
     * not per draw stage.
     */
    private record CacheKey(BakedModel base, List<ResourceLocation> overlays, @Nullable BakedModel ammo, int stage) {}

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
                // The tool's own model JSON carries overrides of its own -- the Broken one (issue
                // #284) -- so resolve those first and overlay whichever model they picked. Returning
                // originalModel unconditionally here is what would swallow them.
                BakedModel resolved = originalModel.getOverrides().resolve(originalModel, stack, level, entity, seed);
                if (resolved == null) {
                    resolved = originalModel;
                }
                int stage = drawStage(tool, stack, entity);
                List<ResourceLocation> overlays = overlaySprites(tool, stack, stage);
                BakedModel ammo = nockedAmmoModel(tool, stack, level, entity, stage);
                if (overlays.isEmpty() && ammo == null) {
                    return resolved;
                }
                BakedModel base = resolved;
                float[] ammoPosition = ToolArt.ammoPosition(tool, stage);
                return COMPOSED.computeIfAbsent(new CacheKey(base, overlays, ammo, stage),
                        cacheKey -> compose(base, overlays, ammo, ammoPosition));
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
    private static List<ResourceLocation> overlaySprites(String tool, ItemStack stack, int stage) {
        List<ResourceLocation> overlays = List.of();
        for (ModifierEntry entry : ForgeweaveModifiers.of(stack)) {
            String texture = ModifierArt.overlay(tool, entry.id(), stage);
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

    /**
     * Which pull stage's overlay art {@code stack} draws (M3.5 issue #400), 0 for "not drawn" --
     * upstream's {@code modifier_suffix}, which its own model loader resolves at the same moment it
     * resolves the draw-stage <em>layers</em>. The two have to agree, so this reads the same two item
     * properties the model's {@code overrides} do rather than the stack's state directly.
     */
    private static int drawStage(String tool, ItemStack stack, @Nullable LivingEntity entity) {
        if (ToolArt.hasLoadedState(tool) && ForgeweaveItemProperties.loaded(stack) > 0.0f) {
            return ToolArt.LOADED_STAGE;
        }
        return ForgeweaveItemProperties.pulling(stack, entity) > 0.0f
                ? ToolArt.drawStage(tool, ForgeweaveItemProperties.pull(stack, entity))
                : 0;
    }

    /**
     * The model of the ammo {@code stack} draws nocked (T52, issue #483), or {@code null} where
     * nothing is nocked: upstream's {@code IAmmoUser#getAmmoToRender}, guarded by whether this tool
     * has an {@code ammoPosition} in this state at all -- a crossbow being cranked has none, and no
     * melee tool ever does.
     */
    @Nullable
    private static BakedModel nockedAmmoModel(String tool, ItemStack stack, @Nullable ClientLevel level,
            @Nullable LivingEntity entity, int stage) {
        if (ToolArt.ammoPosition(tool, stage) == null || !(stack.getItem() instanceof BowItem bow)) {
            return null;
        }
        ItemStack ammo = bow.ammoToRender(stack, entity);
        return ammo.isEmpty() ? null : Minecraft.getInstance().getItemRenderer().getModel(ammo, level, entity, 0);
    }

    /**
     * The ammo's quads moved into place: upstream's {@code TRSRBakedModel(ammoModel, pos, rot, 1f)},
     * whose transform Mantle applies about the item's centre rather than its corner -- without that
     * the ammo's {@code rot [0, 180, 0]} would swing it clean out of the model.
     *
     * <p>The tint index is dropped, which upstream had no need to do: Forgeweave tints a tool's
     * layers per material ({@code ForgeweaveItemColors#toolMaterialTint}) where upstream stitched a
     * sprite per material, and an arrow's own quads carry tint index 0 -- the bow's first limb -- so
     * left alone they would come out limb-coloured. The cost is that a tipped arrow shows its tip
     * untinted; it is still the arrow that would fire.
     */
    private static List<BakedQuad> nockedQuads(BakedModel ammo, float[] position) {
        Transformation transformation = new Transformation(new Matrix4f()
                .translation(0.5f, 0.5f, 0.5f)
                .translate(position[0], position[1], position[2])
                .rotateXYZ((float) Math.toRadians(position[3]), (float) Math.toRadians(position[4]),
                        (float) Math.toRadians(position[5]))
                .translate(-0.5f, -0.5f, -0.5f));
        List<BakedQuad> placed = QuadTransformers.applying(transformation)
                .process(ammo.getQuads(null, null, RandomSource.create(0L)));
        List<BakedQuad> untinted = new ArrayList<>(placed.size());
        for (BakedQuad quad : placed) {
            untinted.add(new BakedQuad(quad.getVertices(), -1, quad.getDirection(), quad.getSprite(),
                    quad.isShade(), quad.hasAmbientOcclusion()));
        }
        return untinted;
    }

    /** The tool's own quads, one untinted baked layer per overlay, then the nocked ammo on top. */
    private static BakedModel compose(BakedModel base, List<ResourceLocation> overlays, @Nullable BakedModel ammo,
            @Nullable float[] ammoPosition) {
        List<BakedQuad> quads = new ArrayList<>(base.getQuads(null, null, RandomSource.create(0L)));
        for (ResourceLocation overlay : overlays) {
            TextureAtlasSprite sprite = blockAtlasSprite(overlay);
            // Tint index -1: overlays are untinted; the part layers' material tint must not bleed in.
            quads.addAll(UnbakedGeometryHelper.bakeElements(
                    UnbakedGeometryHelper.createUnbakedItemElements(-1, sprite),
                    material -> sprite, OVERLAY_DEPTH_STATE));
        }
        if (ammo != null && ammoPosition != null) {
            quads.addAll(nockedQuads(ammo, ammoPosition));
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
