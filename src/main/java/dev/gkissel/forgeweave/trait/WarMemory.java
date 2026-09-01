package dev.gkissel.forgeweave.trait;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * Warspar's adaptive-damage state (issue #884 (8a), the reference Valyrium): one counted-fights
 * entry per entity type the tool has landed a hit on, read by {@code ForgeweaveTraits#WARMEMORY} for
 * its per-type bonus damage. A {@link List} of entries rather than a {@code Map} -- the same shape
 * {@code ForgeweaveDataComponents#TRAITS}/{@code #MODIFIERS} already use for a persisted,
 * network-synced list, so this needs no new {@code StreamCodec} map combinator this codebase has no
 * existing precedent for.
 *
 * <p>Save-compat promised from issue #884's own PR ({@code fixtures/save_compat/
 * m884_tool_war_memory.snbt}) -- see that fixture before changing a field name or type.
 */
public record WarMemory(List<Fight> fights) {

    /** One entity type's counted fights, capped by {@code ForgeweaveTraits#WAR_MEMORY_CAP}. */
    public record Fight(ResourceLocation entityType, int count) {
        public static final Codec<Fight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("entity_type").forGetter(Fight::entityType),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("count").forGetter(Fight::count))
                .apply(instance, Fight::new));

        public static final StreamCodec<ByteBuf, Fight> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Fight::entityType,
                ByteBufCodecs.VAR_INT, Fight::count,
                Fight::new);
    }

    public static final WarMemory EMPTY = new WarMemory(List.of());

    public static final Codec<WarMemory> CODEC = Fight.CODEC.listOf().xmap(WarMemory::new, WarMemory::fights);

    public static final StreamCodec<ByteBuf, WarMemory> STREAM_CODEC =
            Fight.STREAM_CODEC.apply(ByteBufCodecs.list()).map(WarMemory::new, WarMemory::fights);

    /** The counted fights for one entity type, {@code 0} if it has never been struck. */
    public int count(ResourceLocation entityType) {
        for (Fight fight : fights) {
            if (fight.entityType().equals(entityType)) {
                return fight.count();
            }
        }
        return 0;
    }

    /** A new {@link WarMemory} with {@code entityType}'s count set to {@code count}. */
    public WarMemory with(ResourceLocation entityType, int count) {
        List<Fight> updated = new java.util.ArrayList<>(fights.size() + 1);
        boolean replaced = false;
        for (Fight fight : fights) {
            if (fight.entityType().equals(entityType)) {
                updated.add(new Fight(entityType, count));
                replaced = true;
            } else {
                updated.add(fight);
            }
        }
        if (!replaced) {
            updated.add(new Fight(entityType, count));
        }
        return new WarMemory(List.copyOf(updated));
    }
}
