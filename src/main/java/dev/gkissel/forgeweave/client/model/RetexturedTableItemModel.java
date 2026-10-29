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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * {@code forgeweave:retextured_table}: a table station's item form (issue #77), the block model
 * drawn in whichever wood the stack's {@code TEXTURE} component names -- the same default-sprite
 * swap {@link RetexturedTableBakedModel} makes in the world, cached per wood.
 */
public final class RetexturedTableItemModel implements ItemModel {
    private final List<BakedQuad> quads;
    private final TextureAtlasSprite defaultSprite;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    private final Supplier<Vector3fc[]> extents;
    private final Map<Block, List<BakedQuad>> variants = new ConcurrentHashMap<>();

    private RetexturedTableItemModel(List<BakedQuad> quads, TextureAtlasSprite defaultSprite,
            ModelRenderProperties properties, Matrix4fc transformation) {
        this.quads = quads;
        this.defaultSprite = defaultSprite;
        this.properties = properties;
        this.transformation = transformation;
        this.extents = Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(quads));
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        Identifier textureId = stack.get(ForgeweaveDataComponents.TEXTURE.get());
        Block wood = textureId == null ? null : BuiltInRegistries.BLOCK.getOptional(textureId).orElse(null);
        List<BakedQuad> layer = quads;
        if (wood != null && wood != Blocks.AIR) {
            TextureAtlasSprite target = RetexturedTableBakedModel.woodParticle(wood).sprite();
            if (target != defaultSprite) {
                layer = variants.computeIfAbsent(wood, block -> RetexturedTableBakedModel.retexture(quads, defaultSprite, target));
            }
        }
        output.appendModelIdentityElement(this);
        ForgeweaveQuads.fillLayer(output, stack, displayContext, layer == quads ? Blocks.AIR : wood, layer, new int[0],
                properties, transformation, extents);
    }

    /** {@code {"type": "forgeweave:retextured_table", "model": <station block model>}}. */
    public record Unbaked(Identifier model) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model)).apply(instance, Unbaked::new));
        static final Identifier ID = Identifier.fromNamespaceAndPath(Forgeweave.MODID, "retextured_table");

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
            TextureAtlasSprite defaultSprite = context.blockModelBaker().materials()
                    .resolveSlot(baked.slots(), "texture", baked.resolved()).sprite();
            return new RetexturedTableItemModel(baked.quads(), defaultSprite, baked.properties(), new Matrix4f(transformation));
        }
    }
}
