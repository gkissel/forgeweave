package dev.gkissel.forgeweave.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * The three {@code Material} ids (ADR-0002) an assembled tool was built from, stored as a single
 * data component ({@code ForgeweaveDataComponents.TOOL_MATERIALS}) rather than three separate ones
 * so a tool's material set reads and writes atomically. Field order matches the Tool Station's
 * input slots and upstream 1.12's part order for these tools (head, binding, handle) -- see
 * {@code ToolAssemblyRecipes}.
 */
public record ToolMaterials(ResourceLocation head, ResourceLocation binding, ResourceLocation handle) {
    public static final Codec<ToolMaterials> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("head").forGetter(ToolMaterials::head),
            ResourceLocation.CODEC.fieldOf("binding").forGetter(ToolMaterials::binding),
            ResourceLocation.CODEC.fieldOf("handle").forGetter(ToolMaterials::handle))
            .apply(instance, ToolMaterials::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToolMaterials> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ToolMaterials::head,
            ResourceLocation.STREAM_CODEC, ToolMaterials::binding,
            ResourceLocation.STREAM_CODEC, ToolMaterials::handle,
            ToolMaterials::new);
}
