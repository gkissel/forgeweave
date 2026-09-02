package dev.gkissel.forgeweave.trait;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.DefendedBlow;

/**
 * A worn piece refuses to let the blow it is settling fall below a floor -- the M6 armor library's
 * {@code damage_floor(minimumHearts)} (issue #831), the counterweight that keeps a stack of
 * defensive behaviours from adding up to invulnerability.
 *
 * <p>The floor never <em>raises</em> a blow: it is clamped to {@link DefendedBlow#originalDamage},
 * so a half-heart tickle stays a half-heart tickle and only reductions made by earlier pieces are
 * undone. Being an {@code onDefend} behaviour it sees the pre-mitigation amount, which is where
 * every cancelling behaviour in this batch acts (evasion, immunity, the vents); what armor,
 * vanilla Protection and {@link DefendedBlow#protection} take off afterwards is settled later in
 * {@code CombatSeams#onDamagePre} and is deliberately out of reach -- a floor that fought vanilla
 * armor would make plate armor worse than no armor at all.
 *
 * @param minimumHearts the floor in hearts; 1 heart is 2 damage points
 */
public record DamageFloor(float minimumHearts) implements Trait {

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        float floor = Math.min(minimumHearts * 2.0F, blow.originalDamage());
        if (blow.damage() < floor) {
            blow.setDamage(floor);
        }
    }
}
