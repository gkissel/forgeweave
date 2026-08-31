package dev.gkissel.forgeweave.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;

/**
 * A landed hit calls down a real lightning bolt on the target -- ADR-0004's M6 on-hit effect library
 * batch (issue #828) {@code lightning_on_hit(condition)}, the reference instance "Thundergod's
 * Wrath": fires only while the wielder is at full health.
 *
 * <p>No fields beyond the strike itself: {@code condition} is {@link HitCondition}/{@link
 * ConditionalSeam}, the same "vs what" lift {@link FlatBonusDamage}'s javadoc explains, so the
 * trait-definition call site is {@code new ConditionalSeam(WIELDER_FULL_HEALTH, 1.0F, new
 * LightningOnHit())}. {@link HitCondition#WIELDER_FULL_HEALTH} is this batch's one new condition
 * constant -- the attacker-side mirror of the existing target-side {@link HitCondition#FULL_HEALTH},
 * needed because the reference instance gates on the <em>wielder's</em> health, not the target's.
 *
 * <p>A real {@link LightningBolt} entity, not a cosmetic-only one: it deals vanilla's own lightning
 * damage and sets the target on fire, same as a natural strike or a trident's channeling enchant.
 * Credited to the attacker when they are a {@link ServerPlayer} ({@link LightningBolt#setCause}),
 * the same crediting vanilla's own channeling does, so kill/mob-griefing bookkeeping attributes the
 * strike correctly; a non-player attacker (or none, a projectile) leaves the bolt uncredited.
 */
public final class LightningOnHit implements CombatSeam {

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        LivingEntity target = hit.target();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(hit.level());
        if (bolt == null) {
            return;
        }
        bolt.moveTo(target.getX(), target.getY(), target.getZ());
        if (hit.attacker() instanceof ServerPlayer player) {
            bolt.setCause(player);
        }
        hit.level().addFreshEntity(bolt);
    }
}
