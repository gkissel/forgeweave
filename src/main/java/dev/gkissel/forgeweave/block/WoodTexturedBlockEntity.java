package dev.gkissel.forgeweave.block;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * A block entity for a table station that retains the appearance of the wood block it was crafted
 * from (issue #43 -- upstream 1.12's spruce log -> spruce table, ported here since upstream's real
 * mechanism, Mantle's {@code IRetexturedBlockEntity}/{@code RetexturedHelper}, lives outside the
 * 1.20.1 reference clone; see {@code PartBuilderBlock} javadoc). Implementations store the texture
 * {@link Block} in a field and delegate NBT/model-data plumbing to this interface's statics.
 */
public interface WoodTexturedBlockEntity {
    /** Shared model-data key read by {@code RetexturedTableBakedModel} at render time. */
    ModelProperty<Block> TEXTURE_PROPERTY = new ModelProperty<>();

    String TAG_TEXTURE = "texture";

    Block getTexture();

    /** Sets the texture, marks the block entity changed, and pushes a client model-data update. */
    void setTexture(Block texture);

    static Block readTexture(CompoundTag tag, Block fallback) {
        if (tag.contains(TAG_TEXTURE, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_TEXTURE));
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                return BuiltInRegistries.BLOCK.get(id);
            }
        }
        return fallback;
    }

    static void writeTexture(CompoundTag tag, Block texture) {
        tag.putString(TAG_TEXTURE, BuiltInRegistries.BLOCK.getKey(texture).toString());
    }

    /**
     * Common {@code setTexture} tail: call after assigning the field, when the value actually
     * changed, to persist it and push a client model-data/render update.
     */
    static void notifyTextureChanged(BlockEntity blockEntity) {
        blockEntity.setChanged();
        Level level = blockEntity.getLevel();
        if (level != null) {
            blockEntity.requestModelDataUpdate();
            if (!level.isClientSide) {
                level.sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            }
        }
    }

    static ModelData modelData(Block texture) {
        return ModelData.of(TEXTURE_PROPERTY, texture);
    }

    /**
     * Exposes the stored wood as a {@code TEXTURE} data component so vanilla's
     * {@code minecraft:copy_components} loot function (source: block_entity) can carry it onto the
     * dropped item when the block is broken -- see {@code ForgeweaveBlockLootSubProvider}. Call from
     * an override of {@code BlockEntity#collectImplicitComponents}.
     */
    static void collectTextureComponent(WoodTexturedBlockEntity blockEntity, DataComponentMap.Builder builder) {
        builder.set(ForgeweaveDataComponents.TEXTURE.get(), BuiltInRegistries.BLOCK.getKey(blockEntity.getTexture()));
    }
}
