package dev.gkissel.forgeweave.item;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A mob {@code ForgeweaveTraits#DUSKSNARE} pulled out of the world (issue #886), carried by
 * {@link DuskCageItem} as {@code ForgeweaveDataComponents#CAPTURED_MOB}: the entity's registry id
 * plus its <b>full</b> saved NBT (maintainer decision on #886, 2026-09-02 -- not just the type), so
 * releasing it restores the mob exactly as it was snared, health included.
 *
 * <p>The id is stored beside the tag rather than read back out of it because
 * {@link Entity#saveWithoutId} deliberately omits the {@code id} key; keeping it as its own field
 * also lets the item name resolve the mob's name without parsing the tag.
 *
 * <p>Save-compat: {@code fixtures/save_compat/m886_dusk_cage.snbt} pins this shape -- see it before
 * renaming a field. The tag itself is vanilla's own entity format, so it is passed through
 * unvalidated: a mob type this version no longer has simply fails to resolve at release time
 * ({@link #release} returns {@link Optional#empty()}) instead of failing to decode the item.
 */
public record CapturedMob(ResourceLocation entityType, CompoundTag data) {

    public static final Codec<CapturedMob> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity_type").forGetter(CapturedMob::entityType),
            CompoundTag.CODEC.fieldOf("data").forGetter(CapturedMob::data))
            .apply(instance, CapturedMob::new));

    public static final StreamCodec<ByteBuf, CapturedMob> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, CapturedMob::entityType,
            ByteBufCodecs.COMPOUND_TAG, CapturedMob::data,
            CapturedMob::new);

    /** Snapshots {@code entity} as it stands right now; the caller removes it from the world. */
    public static CapturedMob of(LivingEntity entity) {
        return new CapturedMob(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                entity.saveWithoutId(new CompoundTag()));
    }

    /** The captured mob's own name, for the cage's item name and tooltip. */
    public Component name() {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(entityType)
                .map(EntityType::getDescription)
                .orElseGet(() -> Component.literal(entityType.toString()));
    }

    /**
     * Puts the mob back at {@code pos} with the NBT it was captured with, so its health is the
     * health it was snared at. The UUID is re-rolled rather than restored: the saved one may still
     * belong to a live entity (a creative-copied cage), and {@code ServerLevel} silently refuses a
     * duplicate.
     */
    public Optional<Entity> release(ServerLevel level, Vec3 pos, float yRot) {
        Entity entity = BuiltInRegistries.ENTITY_TYPE.getOptional(entityType)
                .map(type -> type.create(level))
                .orElse(null);
        if (entity == null) {
            return Optional.empty();
        }
        entity.load(data);
        entity.setUUID(UUID.randomUUID());
        entity.moveTo(pos.x, pos.y, pos.z, yRot, 0.0F);
        return level.addFreshEntity(entity) ? Optional.of(entity) : Optional.empty();
    }
}
