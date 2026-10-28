package dev.gkissel.forgeweave.client.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.ForgeweaveItemColors;
import dev.gkissel.forgeweave.client.ModifierOverlayModels;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * {@code forgeweave:part}: a loose part's single-layer model, tinted with its material's color
 * (ADR-0002's "greyscale texture + tint"), or drawn with the material's baked finish sprite
 * ({@code material_finishes/parts/<material>/<part>}) where one exists and the Legacy pack is off.
 * The part name is the item's own registry path, as it was for the 1.21.1 finish wrapper.
 */
public final class PartItemModel implements ItemModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Forgeweave.MODID, "part");

    private final List<BakedQuad> quads;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    private final Supplier<Vector3fc[]> extents;
    private final Map<FinishKey, List<BakedQuad>> finished = new ConcurrentHashMap<>();

    private record FinishKey(String part, Identifier material) {}

    private PartItemModel(List<BakedQuad> quads, ModelRenderProperties properties, Matrix4fc transformation) {
        this.quads = quads;
        this.properties = properties;
        this.transformation = transformation;
        this.extents = Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(quads));
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        Identifier material = stack.get(ForgeweaveDataComponents.MATERIAL.get());
        Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        List<BakedQuad> layer = quads;
        Object identity = quads;
        if (material != null && material.getNamespace().equals(Forgeweave.MODID)
                && item.getNamespace().equals(Forgeweave.MODID) && !ModifierOverlayModels.legacySelected()) {
            FinishKey key = new FinishKey(item.getPath(), material);
            layer = finished.computeIfAbsent(key, k -> List.copyOf(ModifierOverlayModels.finishPart(quads, k.part(), k.material())));
            identity = key;
        }
        output.appendModelIdentityElement(this);
        ForgeweaveQuads.fillLayer(output, stack, displayContext, identity, layer,
                new int[] {ForgeweaveItemColors.materialTint(stack, 0)}, properties, transformation, extents);
    }

    /** {@code {"type": "forgeweave:part", "model": <part model>}}. */
    public record Unbaked(Identifier model) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model)).apply(instance, Unbaked::new));

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
            ToolItemModel.Baked baked = ToolItemModel.Baked.of(context.blockModelBaker(), model);
            return new PartItemModel(baked.quads(), baked.properties(), new Matrix4f(transformation));
        }
    }
}
