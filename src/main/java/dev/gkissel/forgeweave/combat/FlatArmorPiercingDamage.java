package dev.gkissel.forgeweave.combat;

/**
 * A hit deals {@code amount} of extra damage that skips armor, resistance and absorption entirely --
 * ADR-0004's M6 parameterized-behavior-library candidate {@code flat_armor_piercing_damage}.
 * Forgeweave's M1 pickaxe retrofit ("pierce", docs/SCOPE.md M3 issue #164) is the first consumer;
 * magnitude decided on the issue (2026-08-12): 1.0.
 *
 * <h2>Why {@link #onHit}, and why a direct health subtraction</h2>
 *
 * <p>{@link CombatSeam#preHit} runs <em>before</em> vanilla's armor/potion/absorption mitigation
 * (ADR-0005), so anything added there is mitigated right along with the rest of the hit -- the
 * opposite of "ignores armor". {@link CombatSeam#onHit} is the only hook that runs after all of that
 * mitigation has already been resolved (it is driven by {@code LivingDamageEvent.Post}, fired once
 * {@code LivingEntity#actuallyHurt} has finished reducing armor, magic and absorption and has already
 * written the mitigated amount to the target's health), which makes it the natural "post-armor bonus"
 * point.
 *
 * <p>Two ways to add a bonus there were considered:
 *
 * <ul>
 *   <li>A second, separate {@code target.hurt(armorBypassingSource, amount)} call. Rejected: vanilla's
 *       hurt-resistance window ({@code LivingEntity#hurt}: a follow-up hit within the same
 *       {@code invulnerableTime} is silently dropped unless its amount exceeds the previous hit's,
 *       {@code if (amount <= this.lastHurt) return false;}) would eat a flat 1.0 follow-up against
 *       almost every real hit, since the original blow is nearly always larger than 1.0.
 *   <li><b>Chosen</b>: subtract {@code amount} from the target's health directly, the same primitive
 *       vanilla's own {@code actuallyHurt} uses ({@code this.setHealth(this.getHealth() - f1)}). This
 *       runs synchronously inside the same {@code actuallyHurt} call that is already resolving the
 *       original blow, strictly after its mitigation and strictly before {@code LivingEntity#hurt}'s
 *       own death check -- so a lethal pierce hit still kills normally (through the attack's own
 *       damage source, for death messages/loot) and no re-entrant hurt-resistance window is ever hit.
 * </ul>
 */
public final class FlatArmorPiercingDamage implements CombatSeam {
    private final float amount;

    public FlatArmorPiercingDamage(float amount) {
        this.amount = amount;
    }

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        hit.target().setHealth(hit.target().getHealth() - amount);
    }
}
