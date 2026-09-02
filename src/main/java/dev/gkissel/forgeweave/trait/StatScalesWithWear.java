package dev.gkissel.forgeweave.trait;

import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.DefendedBlow;
import dev.gkissel.forgeweave.combat.Protection;

/**
 * A stat that rises as the piece wears down -- the M6 armor library's
 * {@code stat_scales_with_wear(stat, coefficient)} (issue #831).
 *
 * <p>The issue asked whether {@code stonebound} generalizes rather than adding a second wear curve.
 * It does: upstream 1.12's {@code TraitStonebound} is exactly {@code wearCurve * 1} on mining speed,
 * so {@code ForgeweaveTraits#STONEBOUND} <em>is</em> {@code stat_scales_with_wear(mining_speed, 1)}
 * now and this class owns the only copy of the curve's application. The curve itself stays where it
 * was, {@code ForgeweaveTraits#wearCurve} ({@code log(lost/72 + 1) * 2}), which {@code jagged} also
 * reads.
 *
 * @param stat which stat the curve is spent on
 * @param coefficient multiplied into the curve; {@code 1} is upstream stonebound's own strength
 */
public record StatScalesWithWear(Stat stat, float coefficient) implements Trait {

    /** The stats worth scaling; one constant per shipped consumer, {@link Trait}'s own rule. */
    public enum Stat {
        /** Mining speed, added only when the tool is effective for the block -- stonebound's shape. */
        MINING_SPEED,
        /** Worn protection, the armor-side mirror ({@code 1} blocks 1/25 of the post-armor blow). */
        PROTECTION
    }

    @Override
    public float miningSpeed(ItemStack stack, boolean effective, float originalSpeed, float speed) {
        if (stat != Stat.MINING_SPEED || !effective) {
            return speed;
        }
        return speed + ForgeweaveTraits.wearCurve(stack) * coefficient;
    }

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        if (stat != Stat.PROTECTION || !Protection.CAN_PROTECT.test(defense.source())) {
            return;
        }
        blow.addProtection(ForgeweaveTraits.wearCurve(defense.tool()) * coefficient);
    }
}
