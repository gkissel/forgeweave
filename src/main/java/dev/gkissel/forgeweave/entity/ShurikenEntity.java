package dev.gkissel.forgeweave.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import dev.gkissel.forgeweave.item.ShurikenItem;

/**
 * A thrown shuriken (issue #448, parity audit T17): upstream 1.12's {@code EntityShuriken} on
 * {@code EntityProjectileBase}, re-based on vanilla's {@link net.minecraft.world.entity.projectile.AbstractArrow}
 * exactly as upstream bases its projectile on {@code EntityArrow} ("otherwise minecraft does derp
 * things because everything is handled based on class").
 *
 * <h2>What is upstream's</h2>
 *
 * <ul>
 *   <li><b>Flat damage.</b> Upstream deals the tool's attack stat as-is on impact
 *       ({@code ProjectileCore#dealDamageRanged} via {@code ToolHelper#attackEntity}; speed is never a
 *       factor). Vanilla's {@code onHitEntity} instead deals {@code ceil(speed * baseDamage)}, so
 *       {@link #onHitEntity} sets {@code baseDamage = attack / speed} just before handing over --
 *       the multiplication cancels and the blow lands at the tool's flat number wherever it hits,
 *       while every other piece of vanilla plumbing (fire transfer to the target, the weapon-item
 *       damage source Forgeweave's combat seams key on, pickup, deflection) stays vanilla's.</li>
 *   <li><b>Gravity ramp</b> ({@code EntityShuriken#getGravity}): none for the first ten ticks, then
 *       {@code (ticks / 10) * 0.04} per tick, integer division included -- a shuriken flies flat and
 *       then drops.</li>
 *   <li><b>Pickup restores ammo</b> ({@code TinkerProjectileHandler#pickup} via {@code AmmoHelper}):
 *       walking over a stuck shuriken first tops up a matching shuriken in the inventory by one ammo
 *       and only hands over the item itself when there is none -- {@link #tryPickup}.</li>
 * </ul>
 *
 * <h2>Recorded deviations (issue #448 PR)</h2>
 *
 * <ul>
 *   <li>Air drag is vanilla's hardcoded 0.99/tick rather than upstream's 0.95
 *       ({@code EntityShuriken#getSlowdown} = 0.05): {@code AbstractArrow#tick} offers no hook for
 *       it, and re-implementing the whole move step for 4% drag is not worth the fork. Flat damage
 *       makes the difference cosmetic (range, not damage).</li>
 *   <li>A hit that deals no damage deflects and drops the shuriken where it stops (vanilla's failed-hurt
 *       branch) rather than upstream's immediate {@code setDead} ({@code bounceOnNoDamage = false});
 *       the shuriken is recoverable either way.</li>
 *   <li>A successful entity hit plays vanilla's arrow-hit ping where upstream's
 *       {@code EntityShuriken#playHitEntitySound} is empty: {@code AbstractArrow} plays its one
 *       {@code soundEvent} on entity and block hits alike, so silencing only the entity branch would
 *       mean forking the whole method for a sound effect.</li>
 * </ul>
 */
public class ShurikenEntity extends net.minecraft.world.entity.projectile.AbstractArrow {

    public ShurikenEntity(EntityType<? extends ShurikenEntity> type, Level level) {
        super(type, level);
    }

    /**
     * @param stack the single-ammo snapshot the entity carries and hands back on pickup
     *     ({@code ProjectileCore#getProjectileStack}); also the weapon whose traits and modifiers
     *     ride the hit ({@code AbstractArrow#firedFromWeapon} -- upstream's ammo-side trait branch,
     *     {@code EntityProjectileBase} reads its capability's item stack the same way)
     */
    public ShurikenEntity(EntityType<? extends ShurikenEntity> type, Level level, LivingEntity owner,
            ItemStack stack) {
        super(type, owner, level, stack, stack);
    }

    /** Upstream {@code EntityShuriken#getGravity}: integer division on purpose -- no drop at all for the first 10 ticks. */
    @Override
    protected double getDefaultGravity() {
        return (this.tickCount / 10) * 0.04;
    }

    /** See the class javadoc: flat tool damage through vanilla's speed-scaled formula. */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        float speed = (float) getDeltaMovement().length();
        ItemStack stack = getPickupItemStackOrigin();
        if (speed > 1.0E-5F && stack.getItem() instanceof ShurikenItem shuriken) {
            setBaseDamage(shuriken.attackDamage(stack) / speed);
        }
        super.onHitEntity(result);
    }

    /**
     * Upstream {@code TinkerProjectileHandler#pickup}: a matching shuriken already in the inventory
     * absorbs the pickup as one ammo; only when none exists (or all are full) does the item itself
     * transfer, which is vanilla's own branch.
     */
    @Override
    protected boolean tryPickup(Player player) {
        if (this.pickup == Pickup.ALLOWED && ShurikenItem.restoreAmmo(player, getPickupItemStackOrigin())) {
            return true;
        }
        return super.tryPickup(player);
    }

    /** Never reached with a real shuriken aboard; vanilla requires a non-null default. */
    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.AIR);
    }

    /** Ticks spent in flight -- the renderer's spin clock, frozen once stuck (upstream {@code spin}). */
    private int spinTicks;

    @Override
    public void tick() {
        if (!this.inGround) {
            this.spinTicks++;
        }
        super.tick();
    }

    /**
     * The renderer's spin angle in degrees: upstream {@code RenderShuriken} advances
     * {@code entity.spin += 20 * partialTicks} every frame while airborne and stops once stuck.
     */
    public float spin(float partialTicks) {
        return (this.spinTicks + (this.inGround ? 0.0F : partialTicks)) * 20.0F;
    }

    /**
     * Upstream {@code EntityShuriken#readSpawnData}'s client-only {@code rollAngle = 7 - rand(14)}
     * "diversity" roll, derived from the entity id so it needs no extra sync or state.
     */
    public float rollAngle() {
        return 7 - Math.floorMod(getId(), 14);
    }
}
