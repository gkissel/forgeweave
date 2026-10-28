package dev.gkissel.forgeweave.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.ForgeweaveItemColors;
import dev.gkissel.forgeweave.client.ModifierOverlayModels;

/**
 * {@code forgeweave:tool}: one assembled tool's layer model, per-material tinted, with its material
 * finishes, modifier overlays and nocked ammo resolved per stack ({@link ModifierOverlayModels}). It is
 * the leaf of every {@code items/<tool>.json}; the broken, draw-stage and held-pose model swaps sit
 * above it as vanilla {@code condition}/{@code range_dispatch} nodes, the 26.1 form of the 1.21.1
 * {@code overrides} ladder.
 *
 * <p>Tint slot {@code n} is the material of layer {@code n} ({@code
 * ForgeweaveItemColors#toolMaterialTint}); the slot right after the last layer is the fortification
 * overlay's material color.
 */
public final class ToolItemModel implements ItemModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Forgeweave.MODID, "tool");

    private final String tool;
    private final List<BakedQuad> quads;
    private final int layerCount;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    private final Supplier<Vector3fc[]> extents;

    /** Finished and overlaid quad lists by what chose them; a rebake makes a fresh model, so a fresh cache. */
    private final Map<Key, List<BakedQuad>> composed = new ConcurrentHashMap<>();

    private record Key(List<Identifier> finish, int stage, List<ModifierOverlayModels.TintedOverlay> overlays) {}

    private ToolItemModel(String tool, List<BakedQuad> quads, ModelRenderProperties properties, Matrix4fc transformation) {
        this.tool = tool;
        this.quads = quads;
        this.layerCount = quads.stream().mapToInt(quad -> quad.materialInfo().tintIndex()).max().orElse(-1) + 1;
        this.properties = properties;
        this.transformation = transformation;
        this.extents = Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(quads));
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        LivingEntity entity = owner == null ? null : owner.asLivingEntity();
        int stage = ModifierOverlayModels.drawStage(tool, stack, entity);
        Key key = new Key(ModifierOverlayModels.finishMaterials(tool, stack), stage,
                ModifierOverlayModels.overlaySprites(tool, stack, stage));
        List<BakedQuad> layer = key.finish().isEmpty() && key.overlays().isEmpty() ? quads
                : composed.computeIfAbsent(key, this::compose);
        List<BakedQuad> ammo = ModifierOverlayModels.nockedAmmo(tool, stack, entity, stage, resolver, level, owner);
        if (!ammo.isEmpty()) {
            layer = new ArrayList<>(layer);
            layer.addAll(ammo);
        }

        boolean fortified = key.overlays().stream().anyMatch(ModifierOverlayModels.TintedOverlay::tinted);
        int[] tints = new int[fortified ? layerCount + 1 : layerCount];
        for (int index = 0; index < layerCount; index++) {
            tints[index] = ForgeweaveItemColors.toolMaterialTint(stack, index);
        }
        if (fortified) {
            tints[layerCount] = ForgeweaveItemColors.fortificationTint(stack);
        }
        output.appendModelIdentityElement(this);
        if (!ammo.isEmpty()) {
            output.appendModelIdentityElement(ammo);
        }
        ForgeweaveQuads.fillLayer(output, stack, displayContext, key, layer, tints, properties, transformation, extents);
    }

    private List<BakedQuad> compose(Key key) {
        List<BakedQuad> layer = new ArrayList<>(key.finish().isEmpty() ? quads
                : ModifierOverlayModels.finish(tool, quads, key.finish(), key.stage()));
        layer.addAll(ModifierOverlayModels.overlayQuads(key.overlays(), layerCount));
        return List.copyOf(layer);
    }

    /** {@code {"type": "forgeweave:tool", "model": <layer model>, "tool": <tool id>}}. */
    public record Unbaked(Identifier model, String tool) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
                Codec.STRING.fieldOf("tool").forGetter(Unbaked::tool)).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(model);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            Baked baked = Baked.of(context.blockModelBaker(), model);
            return new ToolItemModel(tool, baked.quads(), baked.properties(), new Matrix4f(transformation));
        }
    }

    /** One layer model baked the way {@code CuboidItemModelWrapper.Unbaked#bake} bakes it. */
    record Baked(List<BakedQuad> quads, ModelRenderProperties properties, ResolvedModel resolved, TextureSlots slots) {
        static Baked of(ModelBaker baker, Identifier model) {
            ResolvedModel resolved = baker.getModel(model);
            TextureSlots slots = resolved.getTopTextureSlots();
            List<BakedQuad> quads = List.copyOf(resolved.bakeTopGeometry(slots, baker, BlockModelRotation.IDENTITY).getAll());
            return new Baked(quads, ModelRenderProperties.fromResolvedModel(baker, resolved, slots), resolved, slots);
        }
    }
}
