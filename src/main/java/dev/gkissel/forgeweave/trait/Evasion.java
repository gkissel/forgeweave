package dev.gkissel.forgeweave.trait;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.DefendedBlow;

/**
 * A chance that the blow misses entirely -- the M6 armor library's {@code evasion(chance)} (issue
 * #831).
 *
 * <p>Maintainer decision (2026-09-02, JC8): the reference pool's tool-side "ignores evasion" idea is
 * <em>not</em> shipped, so evasion stands alone and there is no attacker-side interaction to settle.
 * A weapon cannot bypass it and nothing needs to read it back.
 *
 * @param chance 0..1 probability the blow is cancelled, rolled per blow
 */
public record Evasion(float chance) implements Trait {

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        if (blow.damage() > 0.0F && defense.level().getRandom().nextFloat() < chance) {
            blow.setDamage(0.0F);
        }
    }
}
