package dev.gkissel.forgeweave.tool;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

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
 *              then adjusted by the HEAD material's trait (Trait#headDurability)
 * miningSpeed = headMiningSpeed
 * attackDamage = headAttackDamage
 * </pre>
 *
 * <p>The trait step is upstream's {@code TinkerEvent.OnItemBuilding} listeners, which fire once on
 * the finished stat block at assembly -- for M1 that is stone's {@code cheapskate} taking 20% off
 * (see {@code ForgeweaveTraits#CHEAPSKATE}). It belongs here rather than in the Tool Station because
 * every caller that wants an assembled tool's stats goes through this method.
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

    /**
     * The three materials must carry the stat block their slot draws from; {@code
     * PartItem#hasUnusableMaterial} is what keeps a part whose material does not from ever reaching
     * an assembly (issue #392 made the blocks optional).
     */
    public static Stats compute(Material head, Material binding, Material handle) {
        Material.Head headStats = head.head().orElseThrow(() -> noStats("head"));
        Material.Handle handleStats = handle.handle().orElseThrow(() -> noStats("handle"));

        int durability = headStats.durability() + binding.extraDurability().orElseThrow(() -> noStats("extra"));
        durability = Math.round(durability * handleStats.durabilityModifier()) + handleStats.durability();
        durability = Math.max(1, durability);
        durability = ForgeweaveTraits.headDurability(head.traits().forPart(PartItem.Kind.HEAD), durability);

        return new Stats(durability, headStats.miningSpeed(), headStats.attackDamage());
    }

    /**
     * The enchantability an assembled tool made of these part materials gets (issue #593): the plain
     * mean of every part's {@link Material#enchantability}, rounded, over every slot the tool has --
     * head, binding, handle, limb, bowstring alike.
     *
     * <p>No upstream aggregation to port: neither 1.12 nor 1.20 gives a material an enchantability
     * to aggregate (see {@link Material#enchantability()}). The mean is the same shape as the
     * averaging upstream 1.12's {@code ToolNBT} does apply to the stats it <em>does</em> have across
     * a slot's materials, and it is the only rule that reads sensibly in both directions: a gold
     * handle on an iron head should make the tool enchant somewhat better, not iron-exactly (a max)
     * and not gold-exactly (a head-only read).
     *
     * <p>Averaging over <em>all</em> parts rather than just the heads is deliberate -- a paper
     * binding is the classic "make it enchantable" part, and a head-only rule would make every
     * non-head material's value dead data.
     */
    public static int averageEnchantability(List<Material> parts) {
        if (parts.isEmpty()) {
            return Material.DEFAULT_ENCHANTABILITY;
        }
        int total = 0;
        for (Material part : parts) {
            total += part.enchantability();
        }
        return Math.max(1, Math.round((float) total / parts.size()));
    }

    static IllegalArgumentException noStats(String block) {
        return new IllegalArgumentException("material in the " + block + " slot carries no " + block + " stats");
    }

    private ToolStats() {}
}
