package dev.gkissel.forgeweave.trait;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Transient per-tool combo state for traits that build up and decay like upstream 1.12's hidden
 * potion effects ({@code TraitMomentum}, {@code TraitInsatiable}) but have no player-scoped
 * potion-effect plumbing to port that through -- see {@code ForgeweaveTraits#MOMENTUM} and
 * {@code #INSATIABLE}. {@code ticksRemaining} counts down by one on every
 * {@link Trait#inventoryTick}; once it reaches zero the component is removed, which reads the same
 * as {@code level == 0}.
 */
public record TraitStacks(int level, int ticksRemaining) {
    public static final Codec<TraitStacks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(TraitStacks::level),
            Codec.INT.fieldOf("ticks_remaining").forGetter(TraitStacks::ticksRemaining))
            .apply(instance, TraitStacks::new));

    public static final StreamCodec<ByteBuf, TraitStacks> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TraitStacks::level,
            ByteBufCodecs.VAR_INT, TraitStacks::ticksRemaining,
            TraitStacks::new);
}
