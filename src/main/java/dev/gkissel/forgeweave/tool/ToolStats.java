package dev.gkissel.forgeweave.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

import dev.gkissel.forgeweave.material.Material;

/**
 * Derives an assembled tool's base stats from its head/binding/handle materials. Ported from
 * upstream 1.12's {@code ToolNBT#head}/{@code #extra}/{@code #handle}
 * (tinkers-1.12 {@code library/tools/ToolNBT.java}, pinned commit in NOTICE.md): that class
 * averages across multiple materials per slot (a tool could have two heads, e.g. a hammer), but
 * Forgeweave's parts are always exactly one material each, so the averaging divides by 1 and drops
 * out, leaving:
 *
 * <pre>
 * durability = round((headDurability + bindingExtraDurability) * handleDurabilityModifier)
 *              + handleDurabilityBonus, minimum 1
 * miningSpeed = headMiningSpeed
 * attackDamage = headAttackDamage
 * </pre>
 *
 * <p>ponytail: kept intentionally small and pure (materials in, stats out; no item plumbing) so
 * it's easy to extend or replace piecemeal. {@link Stats} is codec'd because it is stored on the
 * assembled tool as {@code forgeweave:tool_stats} -- durability lands on the vanilla max-damage
 * component and mining speed inside the vanilla {@code tool} component, but attack damage has no
 * vanilla home once attack modifiers are computed per-stack (see {@code ToolItem}), and keeping all
 * three together makes the assembled stat block one stable serialized shape for the save-compat
 * fixture corpus (docs/SCOPE.md).
 */
public final class ToolStats {

    public record Stats(int durability, float miningSpeed, float attackDamage) {
        public static final Codec<Stats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("durability").forGetter(Stats::durability),
                Codec.FLOAT.fieldOf("mining_speed").forGetter(Stats::miningSpeed),
                Codec.FLOAT.fieldOf("attack_damage").forGetter(Stats::attackDamage))
                .apply(instance, Stats::new));

        public static final StreamCodec<ByteBuf, Stats> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Stats::durability,
                ByteBufCodecs.FLOAT, Stats::miningSpeed,
                ByteBufCodecs.FLOAT, Stats::attackDamage,
                Stats::new);
    }

    public static Stats compute(Material head, Material binding, Material handle) {
        int durability = head.head().durability() + binding.extraDurability();
        durability = Math.round(durability * handle.handle().durabilityModifier()) + handle.handle().durability();
        durability = Math.max(1, durability);

        return new Stats(durability, head.head().miningSpeed(), head.head().attackDamage());
    }

    private ToolStats() {}
}
