package dev.gkissel.forgeweave.item;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.gkissel.forgeweave.entity.ForgeweaveEntities;
import dev.gkissel.forgeweave.entity.ShurikenEntity;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The shuriken (issue #448, parity audit T17): upstream 1.12's {@code tools/ranged/item/Shuriken}
 * on {@code library/tools/ranged/ProjectileCore} -- the ammo-over-durability layer that is now
 * {@link AmmoToolItem} (issue #653 factored it out for the material arrow). Four knife blades at
 * the Tool Forge; right-click throws one.
 *
 * <h2>The throw ({@code Shuriken#onItemRightClick})</h2>
 *
 * <p>Refused while Broken; 4-tick item cooldown; speed {@code 2.1}, inaccuracy {@code 0} (its
 * {@code buildTagData} pins accuracy to perfect); the entity carries a one-ammo snapshot of the tool
 * ({@code ProjectileCore#getProjectileStack}) which is also the weapon whose traits and modifiers the
 * combat seams resolve at impact. Pickup follows the same rule the bows ship: creative throws are
 * {@code CREATIVE_ONLY}, a throw that cost no ammo is {@code DISALLOWED}.
 */
public class ShurikenItem extends AmmoToolItem {

    /** {@code Shuriken#onItemRightClick}'s {@code setCooldown(..., 4)}. */
    public static final int THROW_COOLDOWN_TICKS = 4;

    /** {@code Shuriken#getProjectile}'s launch speed, {@code 2.1f}. */
    public static final float THROW_SPEED = 2.1F;

    public ShurikenItem(Properties properties, ToolConstants.Entry constants) {
        super(properties, constants);
    }

    /** {@code Shuriken#onItemRightClick}, structure and constants. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isBroken(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, THROW_COOLDOWN_TICKS);
        if (!level.isClientSide) {
            boolean usedAmmo = useAmmo(stack, player, hand);
            ShurikenEntity thrown = new ShurikenEntity(ForgeweaveEntities.SHURIKEN.get(), level, player,
                    projectileStack(stack));
            // The bows' documented pickup rule (BowItem, M3.5): creative throws are CREATIVE_ONLY,
            // a throw that cost no ammo is DISALLOWED, everything else stays recoverable.
            if (player.hasInfiniteMaterials()) {
                thrown.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            } else if (!usedAmmo) {
                thrown.pickup = AbstractArrow.Pickup.DISALLOWED;
            } else {
                thrown.pickup = AbstractArrow.Pickup.ALLOWED;
            }
            Vec3 view = player.getViewVector(1.0F);
            thrown.shoot(view.x, view.y, view.z, THROW_SPEED, 0.0F);
            level.addFreshEntity(thrown);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
