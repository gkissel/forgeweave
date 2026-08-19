package dev.gkissel.forgeweave.combat;

import java.util.function.Consumer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.particle.ForgeweaveParticles;

/**
 * The full-charge attack slash a weapon draws when its blow lands (issue #584, parity audit T51):
 * upstream 1.12's eight {@code TinkerTools.proxy.spawnAttackParticle} call sites, seven of which are
 * this seam and one of which is the frying pan's charged launch ({@code ForgeweaveInnates.ChargedLaunch}).
 *
 * <p>Upstream puts each of the seven inside its weapon class's {@code dealDamage} override, behind
 * the same two-part gate every time -- {@code if(hit && readyForSpecialAttack(player))}: the blow
 * actually landed, and the swinger is a {@code EntityPlayer} whose {@code getCooledAttackStrength}
 * was past 0.9. {@link CombatSeam#onHit} is the first half by construction (it fires off
 * {@code LivingDamageEvent.Post}, i.e. once the target has lost health) and {@link
 * CombatHit#isFullCharge} is the second, so the gate below is upstream's, field for field.
 *
 * <p>A provider of its own rather than an entry in {@link ForgeweaveInnates}: a slash is not an
 * innate. It carries no tooltip line, no magnitude a player can build for, and no relationship to
 * the tool's actual behaviour -- four of these seven weapons already carry an unrelated innate seam,
 * and hanging a cosmetic on those would make the innate table read as if it owned them. One
 * {@link CombatSeams.Provider}, registered in {@code Forgeweave} alongside the others, keeps the two
 * concerns apart at the cost of one registration line.
 *
 * <p>ponytail: an if/else chain over the seven items, exactly like {@code ForgeweaveInnates#collect}
 * next door, rather than a map keyed on {@code Item}. Seven comparisons on a hit that already walks
 * every trait and modifier is not the thing to optimise, and a map would have to be built lazily to
 * stay clear of registry init order.
 *
 * @param slash which arc to draw
 * @param heightFactor upstream's per-call-site fraction of the swinger's height -- see
 *     {@link ForgeweaveParticles#spawnSlash}
 */
public record AttackSlash(ForgeweaveParticles.Slash slash, double heightFactor) implements CombatSeam {

    /** {@code Cleaver.java:77}. */
    public static final AttackSlash CLEAVER = new AttackSlash(ForgeweaveParticles.SLASH_CLEAVER, 0.85);
    /** {@code LongSword.java:80}. */
    public static final AttackSlash LONGSWORD = new AttackSlash(ForgeweaveParticles.SLASH_LONGSWORD, 0.7);
    /** {@code Rapier.java:69}. */
    public static final AttackSlash RAPIER = new AttackSlash(ForgeweaveParticles.SLASH_RAPIER, 0.8);
    /** {@code FryPan.java:129} -- the landed blow. Its {@code :115} launch is {@code ChargedLaunch}'s. */
    public static final AttackSlash FRYING_PAN = new AttackSlash(ForgeweaveParticles.SLASH_FRYING_PAN, 0.8);
    /** {@code Hammer.java:74}. */
    public static final AttackSlash HAMMER = new AttackSlash(ForgeweaveParticles.SLASH_HAMMER, 0.8);
    /** {@code Hatchet.java:89}. */
    public static final AttackSlash HATCHET = new AttackSlash(ForgeweaveParticles.SLASH_HATCHET, 0.8);
    /** {@code LumberAxe.java:102}. */
    public static final AttackSlash LUMBERAXE = new AttackSlash(ForgeweaveParticles.SLASH_LUMBERAXE, 0.8);

    /** Upstream's {@code height} on the pan's other site, {@code FryPan#onPlayerStoppedUsing}. */
    public static final double LAUNCH_HEIGHT_FACTOR = 0.6;

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        if (hit.attacker() instanceof Player player && hit.isFullCharge()) {
            ForgeweaveParticles.spawnSlash(slash, hit.level(), player, heightFactor);
        }
    }

    /** Registered as a {@link CombatSeams.Provider} in {@code Forgeweave}. */
    public static void collect(ItemStack weapon, Consumer<CombatSeam> out) {
        if (weapon.is(ForgeweaveItems.TOOL_CLEAVER.get())) {
            out.accept(CLEAVER);
        } else if (weapon.is(ForgeweaveItems.TOOL_LONGSWORD.get())) {
            out.accept(LONGSWORD);
        } else if (weapon.is(ForgeweaveItems.TOOL_RAPIER.get())) {
            out.accept(RAPIER);
        } else if (weapon.is(ForgeweaveItems.TOOL_FRYING_PAN.get())) {
            out.accept(FRYING_PAN);
        } else if (weapon.is(ForgeweaveItems.TOOL_HAMMER.get())) {
            out.accept(HAMMER);
        } else if (weapon.is(ForgeweaveItems.TOOL_HATCHET.get())) {
            out.accept(HATCHET);
        } else if (weapon.is(ForgeweaveItems.TOOL_LUMBERAXE.get())) {
            out.accept(LUMBERAXE);
        }
    }
}
