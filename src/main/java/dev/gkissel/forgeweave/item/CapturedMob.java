package dev.gkissel.forgeweave.item;

import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.util.ProblemReporter;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import com.mojang.serialization.Dynamic;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
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
 *
 * <p>Vanilla's data fixer never sees the tag either, since it sits inside a Forgeweave component, so
 * {@link #of} stamps it with {@code DataVersion} the way vanilla stamps its own saves, and
 * {@link #release} fixes a tag from an older version first. A tag without one was captured on 1.21.1,
 * the only version Forgeweave 1.0.0 shipped for: loaded as is, a mob caged there would come out
 * without its gear (1.21.5 moved {@code HandItems}/{@code ArmorItems} to {@code equipment}) and with
 * its custom name as raw JSON.
 */
public record CapturedMob(Identifier entityType, CompoundTag data) {

    public static final Codec<CapturedMob> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("entity_type").forGetter(CapturedMob::entityType),
            CompoundTag.CODEC.fieldOf("data").forGetter(CapturedMob::data))
            .apply(instance, CapturedMob::new));

    public static final StreamCodec<ByteBuf, CapturedMob> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, CapturedMob::entityType,
            ByteBufCodecs.COMPOUND_TAG, CapturedMob::data,
            CapturedMob::new);

    /** The data version 1.21.1 stamps on everything it saves. */
    private static final int DATA_VERSION_1_21_1 = 3955;

    /** Snapshots {@code entity} as it stands right now; the caller removes it from the world. */
    public static CapturedMob of(LivingEntity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        output.putInt(SharedConstants.DATA_VERSION_TAG, currentVersion());
        return new CapturedMob(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), output.buildResult());
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
                .map(type -> (Entity) type.create(level, EntitySpawnReason.LOAD))
                .orElse(null);
        if (entity == null) {
            return Optional.empty();
        }
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), upgradedData()));
        entity.setUUID(UUID.randomUUID());
        entity.snapTo(pos.x, pos.y, pos.z, yRot, 0.0F);
        return level.addFreshEntity(entity) ? Optional.of(entity) : Optional.empty();
    }

    /** {@link #data}, run through vanilla's entity fixes when it was saved before this version. */
    CompoundTag upgradedData() {
        int from = data.getIntOr(SharedConstants.DATA_VERSION_TAG, DATA_VERSION_1_21_1);
        if (from >= currentVersion()) {
            return data;
        }
        // The entity fixes dispatch on the id, which saveWithoutId left out.
        CompoundTag tagged = data.copy();
        tagged.putString("id", entityType.toString());
        CompoundTag fixed = (CompoundTag) DataFixers.getDataFixer().update(References.ENTITY,
                new Dynamic<>(NbtOps.INSTANCE, tagged), from, currentVersion()).getValue();
        fixed.remove("id");
        return fixed;
    }

    private static int currentVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }
}
