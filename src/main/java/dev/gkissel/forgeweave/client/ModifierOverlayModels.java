package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import javax.annotation.Nullable;

import com.mojang.math.Transformation;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.QuadTransforms;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.model.ForgeweaveQuads;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.Fortification;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ModifierArt;
import dev.gkissel.forgeweave.tool.ToolArt;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * What a tool's item model draws on top of its own layers: applied modifiers as overlay layers
 * (issue #257) and a bow's nocked ammo (T52, issue #483) -- upstream 1.12's {@code
 * BakedToolModel#addModifierQuads} and {@code BakedBowModel#addExtraQuads}. The two live together
 * for upstream's own reason: they are two extra quad lists appended by one model, resolved from one
 * stack at one moment, and the ammo has to know which draw stage the modifier overlays resolved to.
 *
 * <p><b>Upstream behavior, mirrored here:</b> every modifier on the tool that has overlay art
 * renders, in application order, with <b>no cap</b>; a modifier without art draws nothing. Every
 * overlay is untinted <b>except</b> fortification (T70, issue #501), which upstream bakes through
 * {@code MaterialModel}, tinted to the fortifying material's color ({@code
 * ForgeweaveItemColors#fortificationTint}).
 *
 * <p><b>Mechanism (26.1):</b> the tools' {@code items/<tool>.json} pick a layer model per state
 * through vanilla {@code condition}/{@code range_dispatch} nodes, and every leaf is a {@code
 * forgeweave:tool} item model ({@code client.model.ToolItemModel}) that calls in here per stack. It
 * replaces the 1.21.1 {@code ModelEvent.ModifyBakingResult} wrapper whose {@code ItemOverrides} did the
 * same work; the quads are the same ones. Overlay sprites are stitched onto the block atlas by
 * {@code assets/minecraft/atlases/blocks.json}'s {@code derived/tools} directory source.
 *
 * <p>Every non-armor tool selects a baked finish sprite for each material layer before modifiers
 * are added. Loose part items use the same finishes. Unknown materials, broken tools and the Legacy
 * resource pack keep their normal tint and model art.
 *
 * <p><b>Z-fighting:</b> upstream scales every modifier layer up slightly in depth so it always sits
 * above the tool's own layers ({@code ModifierModel#bakeModels}, {@code s = 0.025}); {@link
 * #OVERLAY_DEPTH_STATE} reproduces that (vanilla applies model-state transforms about the block
 * center). All overlays share the one transform: two overlapping overlay pixels resolve by draw
 * order, which is application order.
 *
 * <p><b>The nocked ammo</b> is the ammo item's <em>own model</em>, moved to the spot {@link
 * ToolArt#ammoPosition} names -- upstream's Mantle {@code TRSRBakedModel}, rotation about the item's
 * centre then the offset. It draws last, so an arrow lies over both the tool and its overlays.
 */
public final class ModifierOverlayModels {

    /** Upstream {@code ModifierModel#bakeModels}'s {@code s}: how much deeper the overlay box is. */
    private static final float OVERLAY_DEPTH_GROWTH = 0.025f;

    /** See the class javadoc. */
    private static final ModelState OVERLAY_DEPTH_STATE = new ModelState() {
        private final Transformation transformation =
                new Transformation(null, null, new Vector3f(1.0f, 1.0f, 1.0f + OVERLAY_DEPTH_GROWTH), null);

        @Override
        public Transformation transformation() {
            return transformation;
        }
    };

    /**
     * One overlay sprite and whether it is fortification's, the one overlay tinted by material (T70,
     * issue #501). The tint itself is resolved live per stack, so two fortifications sharing an
     * overlay sprite share one cached quad list and still render each other's color correctly.
     */
    public record TintedOverlay(Identifier sprite, boolean tinted) {}

    private ModifierOverlayModels() {}

    /**
     * The materials whose finish sprites {@code tool}'s layers should draw for {@code stack}, or an
     * empty list where no finish applies: armor, broken tools, the Legacy pack and stacks without
     * materials all keep their tinted model art.
     */
    public static List<Identifier> finishMaterials(String tool, ItemStack stack) {
        if (tool.equals("helmet") || tool.equals("chestplate") || tool.equals("leggings") || tool.equals("boots")
                || tool.startsWith("heavy_") || ToolItem.isBroken(stack) || legacySelected()) {
            return List.of();
        }
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        return materials == null ? List.of() : List.copyOf(materials.parts());
    }

    /** Whether the built-in Legacy art pack is on, which keeps the pre-Forged tinted look. */
    public static boolean legacySelected() {
        return Minecraft.getInstance().getResourcePackRepository().getSelectedIds().stream()
                .anyMatch(id -> id.contains("forgeweave") && id.contains("legacy"));
    }

    /**
     * {@code quads} with each material layer swapped for its {@code material_finishes/<tool>/<material>/<layer>}
     * sprite, untinted, where one was stitched; a layer with no finish art keeps its tinted quad.
     */
    public static List<BakedQuad> finish(String tool, List<BakedQuad> quads, List<Identifier> materials, int stage) {
        ToolAssemblyRecipes.Entry entry = ToolAssemblyRecipes.ENTRIES.stream()
                .filter(candidate -> candidate.constants().id().equals(tool)).findFirst().orElse(null);
        if (entry == null) {
            return quads;
        }
        List<Integer> slots = ToolArt.layerSlots(entry.constants().parts());
        List<String> layers = ToolArt.layers(entry.constants().parts());
        List<BakedQuad> finished = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            int index = quad.materialInfo().tintIndex();
            if (index < 0 || index >= slots.size() || slots.get(index) >= materials.size()) {
                finished.add(quad);
                continue;
            }
            Identifier material = materials.get(slots.get(index));
            if (!material.getNamespace().equals(Forgeweave.MODID)) {
                finished.add(quad);
                continue;
            }
            TextureAtlasSprite target = ForgeweaveQuads.blockSprite(Identifier.fromNamespaceAndPath(Forgeweave.MODID,
                    "material_finishes/" + tool + "/" + material.getPath() + "/" + layers.get(index)
                            + (stage > 0 ? "_draw" + stage : "")));
            finished.add(target == null ? quad : ForgeweaveQuads.retexture(quad, target, -1));
        }
        return finished;
    }

    /** A loose part's {@code material_finishes/parts/<material>/<part>} finish, or {@code quads} unchanged without one. */
    public static List<BakedQuad> finishPart(List<BakedQuad> quads, String part, Identifier material) {
        TextureAtlasSprite target = ForgeweaveQuads.blockSprite(Identifier.fromNamespaceAndPath(Forgeweave.MODID,
                "material_finishes/parts/" + material.getPath() + "/" + part));
        if (target == null) {
            return quads;
        }
        List<BakedQuad> finished = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            finished.add(quad.materialInfo().tintIndex() == 0 ? ForgeweaveQuads.retexture(quad, target, -1) : quad);
        }
        return finished;
    }

    /**
     * The overlay sprites {@code stack}'s modifiers draw, in application order (upstream's own
     * order: the base modifiers tag list is append-only). A sprite missing from the atlas is
     * skipped defensively, as upstream skips ids its {@code modifierParts} map lacks --
     * {@code ModifierArtTest} is what makes that unreachable for shipped modifiers.
     */
    public static List<TintedOverlay> overlaySprites(String tool, ItemStack stack, int stage) {
        if (stack.getOrDefault(ForgeweaveDataComponents.HIDE_MODIFIER_VISUALS.get(), false)) {
            return List.of();
        }
        List<TintedOverlay> overlays = List.of();
        for (ModifierEntry entry : ForgeweaveModifiers.of(stack)) {
            String texture = ModifierArt.overlay(tool, entry.id(), stage);
            if (texture == null) {
                continue;
            }
            Identifier sprite = Identifier.fromNamespaceAndPath(Forgeweave.MODID, texture);
            if (ForgeweaveQuads.blockSprite(sprite) == null) {
                continue;
            }
            if (overlays.isEmpty()) {
                overlays = new ArrayList<>(2);
            }
            overlays.add(new TintedOverlay(sprite, Fortification.isFortification(entry.id())));
        }
        return overlays;
    }

    /**
     * One baked layer per overlay, untinted except fortification's, which bakes at {@code
     * fortificationTint} -- the tool model's first tint slot past its own material layers.
     */
    public static List<BakedQuad> overlayQuads(List<TintedOverlay> overlays, int fortificationTint) {
        List<BakedQuad> quads = new ArrayList<>();
        for (TintedOverlay overlay : overlays) {
            TextureAtlasSprite sprite = ForgeweaveQuads.blockSprite(overlay.sprite());
            if (sprite != null) {
                quads.addAll(ForgeweaveQuads.extrude(sprite, overlay.tinted() ? fortificationTint : -1, OVERLAY_DEPTH_STATE));
            }
        }
        return quads;
    }

    /**
     * Which pull stage's art {@code stack} draws (M3.5 issue #400), 0 for "not drawn" -- upstream's
     * {@code modifier_suffix}. It has to agree with the draw-stage <em>layers</em> the client item's
     * {@code forgeweave:pulling}/{@code forgeweave:pull} nodes pick, so it reads the same two
     * properties rather than the stack's state directly.
     */
    public static int drawStage(String tool, ItemStack stack, @Nullable LivingEntity entity) {
        if (ToolArt.hasLoadedState(tool) && ForgeweaveItemProperties.loaded(stack) > 0.0f) {
            return ToolArt.LOADED_STAGE;
        }
        return ForgeweaveItemProperties.pulling(stack, entity) > 0.0f
                ? ToolArt.drawStage(tool, ForgeweaveItemProperties.pull(stack, entity))
                : 0;
    }

    /**
     * The ammo {@code stack} draws nocked (T52, issue #483) as quads already in place, or an empty
     * list where nothing is nocked: upstream's {@code IAmmoUser#getAmmoToRender}, guarded by whether
     * this tool has an {@code ammoPosition} in this state at all -- an undrawn bow has none (#600), nor
     * does a crossbow being cranked, nor any melee tool ever. The ammo's model is resolved into a
     * scratch render state each frame, which is what hands back its quads and its own tints.
     */
    public static List<BakedQuad> nockedAmmo(String tool, ItemStack stack, @Nullable LivingEntity entity, int stage,
            ItemModelResolver resolver, @Nullable ClientLevel level, @Nullable ItemOwner owner) {
        float[] position = ToolArt.ammoPosition(tool, stage);
        if (position == null || !(stack.getItem() instanceof BowItem bow)) {
            return List.of();
        }
        ItemStack ammo = bow.ammoToRender(stack, entity);
        if (ammo.isEmpty()) {
            return List.of();
        }
        CapturingRenderState capture = new CapturingRenderState();
        resolver.appendItemLayers(capture, ammo, ItemDisplayContext.NONE, level, owner, 0);
        List<BakedQuad> nocked = new ArrayList<>();
        for (ItemStackRenderState.LayerRenderState layer : capture.layers) {
            int[] tints = layer.tintLayers().toIntArray();
            nocked.addAll(nockedQuads(layer.quads.all(), position,
                    index -> index < tints.length ? ARGB.opaque(tints[index]) : -1));
        }
        return nocked;
    }

    /**
     * The ammo's quads moved into place: upstream's {@code TRSRBakedModel(ammoModel, pos, rot, 1f)},
     * whose transform Mantle applies about the item's centre rather than its corner -- without that
     * the ammo's {@code rot [0, 180, 0]} would swing it clean out of the model.
     *
     * <p>The tint <em>index</em> has to be dropped, which upstream had no need to do: an arrow's own
     * quads carry tint index 0, which on the composed bow means "the first limb". Dropping it without
     * resolving it first is what made every tipped arrow render as the plain white one (#600), so
     * {@code ammoColors} is asked what the index meant and the answer is baked into the quad's
     * vertex colours, which the renderer multiplies by an untinted quad's implicit white.
     */
    static List<BakedQuad> nockedQuads(List<BakedQuad> ammoQuads, float[] position, IntUnaryOperator ammoColors) {
        Transformation transformation = new Transformation(new Matrix4f()
                .translation(0.5f, 0.5f, 0.5f)
                .translate(position[0], position[1], position[2])
                .rotateXYZ((float) Math.toRadians(position[3]), (float) Math.toRadians(position[4]),
                        (float) Math.toRadians(position[5]))
                .translate(-0.5f, -0.5f, -0.5f));
        List<BakedQuad> baked = new ArrayList<>(ammoQuads.size());
        for (BakedQuad quad : ammoQuads) {
            BakedQuad placed = QuadTransforms.applyTransformation(quad, transformation);
            BakedQuad.MaterialInfo info = placed.materialInfo();
            BakedColors colors = info.isTinted() ? BakedColors.of(ammoColors.applyAsInt(info.tintIndex()))
                    : placed.bakedColors();
            baked.add(new BakedQuad(placed.position0(), placed.position1(), placed.position2(), placed.position3(),
                    placed.packedUV0(), placed.packedUV1(), placed.packedUV2(), placed.packedUV3(), placed.direction(),
                    new BakedQuad.MaterialInfo(info.sprite(), info.layer(), info.itemRenderType(), info.itemGlintRenderType(),
                            info.itemGlintSpecialRenderType(), -1, info.shadeDirectionOverride(),
                            info.lightEmission(), info.ambientOcclusion()),
                    placed.bakedNormals(), colors));
        }
        return baked;
    }

    /** A render state that remembers its layers, so the nocked ammo's resolved quads and tints can be read back. */
    private static final class CapturingRenderState extends ItemStackRenderState {
        private final List<LayerRenderState> layers = new ArrayList<>(1);

        @Override
        public LayerRenderState newLayer() {
            LayerRenderState layer = super.newLayer();
            layers.add(layer);
            return layer;
        }
    }
}
