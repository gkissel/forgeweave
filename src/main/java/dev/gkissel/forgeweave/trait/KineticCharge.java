package dev.gkissel.forgeweave.trait;

import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;

/**
 * {@code kinetic_charge(fractionOfDamage)}: converts damage dealt into stored energy (issue #830
 * deliverable 4) -- the buffer's non-machine top-up, the design pool's "Piezoelectric" idea (design
 * pool docs/research/m6-material-expansion-references.md §6.5). Hangs off the existing {@link
 * CombatSeam#onHit} seam rather than a new one, per the issue's own instruction, and is a no-op on
 * a tool whose traits carry no {@link Trait#energyCapacity} -- there is nowhere to put the energy
 * without an {@code energized} trait alongside it. Wired onto a {@link Trait} through {@code
 * ForgeweaveTraits#seamTrait}, the same idiom every other combat-seam trait uses.
 */
public record KineticCharge(float fractionOfDamage) implements CombatSeam {

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        if (fractionOfDamage <= 0.0F || damageDealt <= 0.0F) {
            return;
        }
        int capacity = ForgeweaveTraits.energyCapacity(hit.weapon());
        int gained = Math.round(damageDealt * fractionOfDamage);
        EnergyBuffer.receive(hit.weapon(), capacity, gained, false);
    }
}
