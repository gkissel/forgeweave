package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

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
import net.minecraft.util.FastColor;
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
import dev.gkissel.forgeweave.modifier.Fortification;
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
 * addModifierQuads}); a modifier without art simply draws nothing. Every overlay is untinted
 * (upstream bakes it through a plain {@code ItemLayerModel} retexture) <b>except</b> fortification
 * (T70, issue #501): upstream's {@code ModFortify#hasTexturePerMaterial} bakes that one overlay
 * through {@code MaterialModel} instead, tinted to the fortifying material's color -- see
 * {@code ForgeweaveItemColors#FORTIFICATION_TINT_INDEX} and {@code #fortificationTint}.
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
     * item + meta + NBT), and {@code ammoTint} the colour that model's quads are baked in --
     * together they are everything about the ammo stack the composed quads can still vary by, since
     * {@link #nockedQuads} resolves the tint at bake time and clears the index (#600).
     * {@code stage} carries the ammo's position, which {@code base} alone does not -- a bow's model
     * is one instance per item, not per draw stage.
     *
     * <p>ponytail: {@code ammoTint} is tint index 0's colour only -- every vanilla arrow that
     * carries a colour at all (the tipped one) carries it there, and vanilla's own handler answers
     * {@code -1} for every higher index. Widen it to the full index list if a modded arrow ever
     * varies a second tinted layer independently.
     */
    private record CacheKey(BakedModel base, List<TintedOverlay> overlays, @Nullable BakedModel ammo, int stage,
            int ammoTint) {}

    /**
     * One overlay sprite plus the tint index its quad bakes at -- {@code -1} (untinted) for every
     * overlay except fortification's, which bakes at {@code ForgeweaveItemColors#FORTIFICATION_TINT_INDEX}
     * (T70, issue #501). Caching the composed model by this pair rather than the sprite alone is
     * still safe with a shared cache across different fortifying materials: the tint index is all a
     * baked quad ever carries, the actual color is resolved live from the rendered stack by the
     * registered {@code ItemColor} every frame (the same reason a shared dyed-leather or
     * stained-glass-pane model recolors correctly per stack), so two fortifications sharing a tool's
     * overlay sprite share one cache entry and still render each other's color correctly.
     */
    private record TintedOverlay(ResourceLocation sprite, int tint) {}

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
                List<TintedOverlay> overlays = overlaySprites(tool, stack, stage);
                ItemStack ammo = nockedAmmo(tool, stack, entity, stage);
                BakedModel ammoModel = ammo.isEmpty() ? null
                        : Minecraft.getInstance().getItemRenderer().getModel(ammo, level, entity, 0);
                if (overlays.isEmpty() && ammoModel == null) {
                    return resolved;
                }
                BakedModel base = resolved;
                float[] ammoPosition = ToolArt.ammoPosition(tool, stage);
                return COMPOSED.computeIfAbsent(new CacheKey(base, overlays, ammoModel, stage, ammoColor(ammo, 0)),
                        cacheKey -> compose(base, overlays, ammoModel, ammo, ammoPosition));
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
    private static List<TintedOverlay> overlaySprites(String tool, ItemStack stack, int stage) {
        List<TintedOverlay> overlays = List.of();
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
            int tint = Fortification.isFortification(entry.id()) ? ForgeweaveItemColors.FORTIFICATION_TINT_INDEX : -1;
            overlays.add(new TintedOverlay(sprite, tint));
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
     * The ammo {@code stack} draws nocked (T52, issue #483), or empty where nothing is nocked:
     * upstream's {@code IAmmoUser#getAmmoToRender}, guarded by whether this tool has an
     * {@code ammoPosition} in this state at all -- an undrawn bow has none (#600), nor does a
     * crossbow being cranked, nor any melee tool ever.
     */
    private static ItemStack nockedAmmo(String tool, ItemStack stack, @Nullable LivingEntity entity, int stage) {
        if (ToolArt.ammoPosition(tool, stage) == null || !(stack.getItem() instanceof BowItem bow)) {
            return ItemStack.EMPTY;
        }
        return bow.ammoToRender(stack, entity);
    }

    /**
     * What vanilla's own {@code ItemColors} handler answers for {@code ammo}'s tint index -- the
     * potion's colour for a tipped arrow, {@code -1} (white) for a plain or spectral one, and
     * whatever a modded arrow registered for itself. Forced opaque, because unlike the renderer
     * (which takes alpha from elsewhere) a vertex colour with alpha 0 draws nothing (#8).
     */
    private static int ammoColor(ItemStack ammo, int tintIndex) {
        return ammo.isEmpty() ? -1
                : FastColor.ARGB32.opaque(Minecraft.getInstance().getItemColors().getColor(ammo, tintIndex));
    }

    /**
     * The ammo's quads moved into place: upstream's {@code TRSRBakedModel(ammoModel, pos, rot, 1f)},
     * whose transform Mantle applies about the item's centre rather than its corner -- without that
     * the ammo's {@code rot [0, 180, 0]} would swing it clean out of the model.
     *
     * <p>The tint <em>index</em> has to be dropped, which upstream had no need to do: Forgeweave
     * tints a tool's layers per material ({@code ForgeweaveItemColors#toolMaterialTint}) where
     * upstream stitched a sprite per material, and an arrow's own quads carry tint index 0 -- which
     * on the composed bow means "the first limb" -- so left alone they would come out limb-coloured.
     * Dropping it without resolving it first is what made every tipped arrow render as the plain
     * white one (#600), so {@code ammoColors} is asked what the index meant and the answer is baked
     * into the quad's vertex colours, which the renderer multiplies by an untinted quad's implicit
     * white. {@link CacheKey} carries that colour, so two potions do not share one composed model.
     */
    static List<BakedQuad> nockedQuads(List<BakedQuad> ammoQuads, float[] position, IntUnaryOperator ammoColors) {
        Transformation transformation = new Transformation(new Matrix4f()
                .translation(0.5f, 0.5f, 0.5f)
                .translate(position[0], position[1], position[2])
                .rotateXYZ((float) Math.toRadians(position[3]), (float) Math.toRadians(position[4]),
                        (float) Math.toRadians(position[5]))
                .translate(-0.5f, -0.5f, -0.5f));
        List<BakedQuad> placed = QuadTransformers.applying(transformation).process(ammoQuads);
        List<BakedQuad> baked = new ArrayList<>(placed.size());
        for (BakedQuad quad : placed) {
            if (quad.isTinted()) {
                QuadTransformers.applyingColor(ammoColors.applyAsInt(quad.getTintIndex())).processInPlace(quad);
            }
            baked.add(new BakedQuad(quad.getVertices(), -1, quad.getDirection(), quad.getSprite(),
                    quad.isShade(), quad.hasAmbientOcclusion()));
        }
        return baked;
    }

    /**
     * The tool's own quads, one baked layer per overlay (untinted at -1, except fortification's at
     * {@code ForgeweaveItemColors#FORTIFICATION_TINT_INDEX} -- see {@link TintedOverlay}), then the
     * nocked ammo on top.
     */
    private static BakedModel compose(BakedModel base, List<TintedOverlay> overlays, @Nullable BakedModel ammo,
            ItemStack ammoStack, @Nullable float[] ammoPosition) {
        List<BakedQuad> quads = new ArrayList<>(base.getQuads(null, null, RandomSource.create(0L)));
        for (TintedOverlay overlay : overlays) {
            TextureAtlasSprite sprite = blockAtlasSprite(overlay.sprite());
            // The part layers' own material tint must never bleed into an untinted (-1) overlay.
            quads.addAll(UnbakedGeometryHelper.bakeElements(
                    UnbakedGeometryHelper.createUnbakedItemElements(overlay.tint(), sprite),
                    material -> sprite, OVERLAY_DEPTH_STATE));
        }
        if (ammo != null && ammoPosition != null) {
            quads.addAll(nockedQuads(ammo.getQuads(null, null, RandomSource.create(0L)), ammoPosition,
                    tintIndex -> ammoColor(ammoStack, tintIndex)));
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
