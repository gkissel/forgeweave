package dev.gkissel.forgeweave.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * One modifier on a tool: <b>an id and a level, and nothing else</b>. ADR-0004's decision 2 makes
 * that a hard architectural rule -- no class reference, no cached behavior, no derived stat -- so
 * that every save and every fixture stays decodable when M6 replaces the Java behaviors with a
 * parameterized library. The tool's component is an ordered {@code List} of these
 * ({@code ForgeweaveDataComponents#MODIFIERS}), in the order the modifiers were applied.
 *
 * <h2>What {@code level} counts</h2>
 *
 * <p>Application units, in whatever unit the modifier itself is denominated in -- upstream 1.12's
 * {@code ModifierNBT.IntegerNBT#current}. For a modifier that is simply on or off (one gold block,
 * done) that is the same as the level a player sees. For a leveled one it is the raw count of
 * applications: haste's {@link Modifier#unitsPerLevel} is 50, so {@code level == 51} is the 51st
 * redstone and displays as level 2 ({@link ForgeweaveModifiers#displayLevel}).
 *
 * <p>Collapsing progress into the level is what keeps the serialized shape to the two fields ADR-0004
 * allows while still reproducing upstream's behavior of accepting redstone one at a time and showing
 * the partial fill, rather than demanding 50 at once.
 */
public record ModifierEntry(ResourceLocation id, int level) {

    public static final Codec<ModifierEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(ModifierEntry::id),
            ExtraCodecs.POSITIVE_INT.fieldOf("level").forGetter(ModifierEntry::level))
            .apply(instance, ModifierEntry::new));

    public static final StreamCodec<ByteBuf, ModifierEntry> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ModifierEntry::id,
            ByteBufCodecs.VAR_INT, ModifierEntry::level,
            ModifierEntry::new);

    public ModifierEntry withLevel(int newLevel) {
        return new ModifierEntry(id, newLevel);
    }
}
