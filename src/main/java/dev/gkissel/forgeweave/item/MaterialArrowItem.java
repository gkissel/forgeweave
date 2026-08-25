package dev.gkissel.forgeweave.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.gkissel.forgeweave.entity.ArrowEntity;
import dev.gkissel.forgeweave.entity.ForgeweaveEntities;
import dev.gkissel.forgeweave.tool.ProjectileStats;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * The material arrow (issue #653, parity audit T17): upstream 1.12's
 * {@code tools/ranged/item/Arrow.java} on {@code ProjectileCore} -- shaft + head + fletching at the
 * Tool Station, ammo abstracted over durability ({@link AmmoToolItem}), fired by Forgeweave's bows
 * ahead of vanilla arrows ({@code BowItem#findAmmo}).
 *
 * <p>Unlike the shuriken it has no {@code use()} of its own -- upstream's Arrow is pure ammo, thrown
 * by nothing; {@link #createProjectile} is its {@code IAmmo#getProjectile}, called from
 * {@code BowItem#shoot} with the launcher-side damage already folded in
 * ({@code BowCore#getProjectileEntity} passes itself in the same way).
 *
 * <p>Upstream {@code Arrow#getProjectile}'s inaccuracy adjustment is ported verbatim:
 * {@code inaccuracy -= (1 - 1/accuracy) * speed / 2} -- zero for a perfect (1.0) fletching and a
 * growing spread for a worse one, since {@code accuracy <= 1} makes the subtracted term negative.
 *
 * <p>The two flight traits' launch halves land here ({@code AbstractProjectileTrait#onLaunch}):
 * hovering halves the launch velocity ({@code TraitHovering}), endspeed cuts it to a tenth and
 * turns gravity off ({@code TraitEndspeed}); their per-tick halves are {@link ArrowEntity}'s.
 */
public class MaterialArrowItem extends AmmoToolItem {

    public MaterialArrowItem(Properties properties, ToolConstants.Entry constants) {
        super(properties, constants);
    }

    /**
     * {@code Arrow#getProjectile} + {@code EntityProjectileBase}'s launch: the entity, aimed the way
     * the player faces at {@code velocity} with the fletching-adjusted {@code inaccuracy}, carrying
     * a one-ammo snapshot ({@link #projectileStack}) and the launch-computed {@code flatDamage}
     * (see {@link ArrowEntity}'s class javadoc for the formula; {@code BowItem#shoot} computes it
     * because the launcher's base/modifier constants live there).
     *
     * <p>The caller decides pickup and spawns the entity, exactly as it does for vanilla arrows.
     */
    public ArrowEntity createProjectile(ItemStack ammo, Level level, Player player, float velocity,
            float inaccuracy, float flatDamage) {
        ProjectileStats stats = ammo.get(ForgeweaveDataComponents.PROJECTILE_STATS.get());
        if (stats != null && stats.accuracy() > 0.0F) {
            inaccuracy -= (1.0F - 1.0F / stats.accuracy()) * velocity / 2.0F;
        }
        ItemStack carried = projectileStack(ammo);
        ArrowEntity arrow = new ArrowEntity(ForgeweaveEntities.ARROW.get(), level, player, carried);
        arrow.setFlatDamage(flatDamage);
        Vec3 aim = BowItem.aimVector(player);
        arrow.shoot(aim.x, aim.y, aim.z, velocity, inaccuracy);
        // The launch halves of the flight traits (AbstractProjectileTrait#onLaunch).
        if (ForgeweaveTraits.has(carried, ForgeweaveTraits.HOVERING)) {
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(0.5)); // TraitHovering: /2
        }
        if (ForgeweaveTraits.has(carried, ForgeweaveTraits.ENDSPEED)) {
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(0.1)); // TraitEndspeed: /10
            arrow.setNoGravity(true);
        }
        return arrow;
    }

    /** {@link AmmoToolItem#useAmmo}, opened up for {@code BowItem}'s ammo consumption. */
    public boolean consumeShot(ItemStack stack, Player player) {
        return useAmmo(stack, player, player.getUsedItemHand());
    }
}
