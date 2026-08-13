package dev.gkissel.forgeweave.trait;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * The alien trait's progressive-stat state (upstream 1.12 {@code TraitProgressiveStats}, which
 * {@code TraitAlien} extends -- see {@code ForgeweaveTraits#ALIEN}): a {@code pool} of stat bonuses
 * designated once when the trait first ticks, and the {@code distributed} share of that pool the
 * tool has been awarded so far. Upstream persists the same two blocks as {@code alienStatPool} /
 * {@code alienStatBonus} NBT on the tool; this is that pair as a 1.21 data component
 * ({@code ForgeweaveDataComponents#ALIEN_PROGRESS}).
 *
 * <p>Save-compat promised from the first M3.2 beta
 * ({@code fixtures/save_compat/m3_2_tool_alien_progress.snbt}) -- see that fixture before changing a
 * field name or type. The {@code distributed} block is the single source of truth for the bonus:
 * mining speed and attack damage are derived from it at read time and durability is re-applied
 * wherever {@code max_damage} is recomputed ({@code Trait#maxDurabilityBonus}), which is what lets
 * the growth survive any later stat recomputation, as upstream's
 * {@code TraitProgressiveStats#applyEffect} re-adds its bonus on every tool rebuild.
 */
public record AlienProgress(Portion pool, Portion distributed) {

    /** One stat block, in the same three axes as {@code ToolStats.Stats} but allowed to be zero. */
    public record Portion(int durability, float miningSpeed, float attackDamage) {
        public static final Portion ZERO = new Portion(0, 0.0F, 0.0F);

        public static final Codec<Portion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("durability").forGetter(Portion::durability),
                Codec.FLOAT.fieldOf("mining_speed").forGetter(Portion::miningSpeed),
                Codec.FLOAT.fieldOf("attack_damage").forGetter(Portion::attackDamage))
                .apply(instance, Portion::new));

        public static final StreamCodec<ByteBuf, Portion> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Portion::durability,
                ByteBufCodecs.FLOAT, Portion::miningSpeed,
                ByteBufCodecs.FLOAT, Portion::attackDamage,
                Portion::new);
    }

    public static final Codec<AlienProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Portion.CODEC.fieldOf("pool").forGetter(AlienProgress::pool),
            Portion.CODEC.fieldOf("distributed").forGetter(AlienProgress::distributed))
            .apply(instance, AlienProgress::new));

    public static final StreamCodec<ByteBuf, AlienProgress> STREAM_CODEC = StreamCodec.composite(
            Portion.STREAM_CODEC, AlienProgress::pool,
            Portion.STREAM_CODEC, AlienProgress::distributed,
            AlienProgress::new);
}
