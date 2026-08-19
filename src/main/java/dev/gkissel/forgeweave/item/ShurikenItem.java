package dev.gkissel.forgeweave.item;

import java.util.List;
import java.util.Objects;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.gkissel.forgeweave.entity.ForgeweaveEntities;
import dev.gkissel.forgeweave.entity.ShurikenEntity;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The shuriken (issue #448, parity audit T17): upstream 1.12's {@code tools/ranged/item/Shuriken}
 * on {@code library/tools/ranged/ProjectileCore} -- the first Forgeweave tool whose "durability" is
 * an ammo counter. Four knife blades at the Tool Forge; right-click throws one.
 *
 * <h2>Ammo as durability ({@code ProjectileCore})</h2>
 *
 * <p>Upstream abstracts ammo on top of durability: {@code durabilityPerAmmo = 10}, ammo =
 * durability / 10, a throw is {@code ToolHelper#damageTool(stack, 10)} and reaching zero breaks the
 * tool ({@code ProjectileCore#useAmmo}). Everything Forgeweave already does to durability therefore
 * works on ammo unchanged: the reinforced negation roll and duritos/dense repricing ride {@link
 * ToolItem#damageItem}, station repair is the reload ({@code getRepairParts() = all four blades}),
 * the durability bar shows the ammo fraction because it <em>is</em> the ammo fraction, and the
 * Broken state is "Empty". A worn shuriken that cannot pay a full throw any more (durability left
 * but ammo 0 -- possible because {@link ToolItem#damageItem} floors at {@code maxDamage - 1}) is
 * broken outright, upstream's own {@code useAmmo} endgame.
 *
 * <h2>The throw ({@code Shuriken#onItemRightClick})</h2>
 *
 * <p>Refused while Broken; 4-tick item cooldown; speed {@code 2.1}, inaccuracy {@code 0} (its
 * {@code buildTagData} pins accuracy to perfect); the entity carries a one-ammo snapshot of the tool
 * ({@code ProjectileCore#getProjectileStack}) which is also the weapon whose traits and modifiers the
 * combat seams resolve at impact. Pickup follows the same rule the bows ship: creative throws are
 * {@code CREATIVE_ONLY}, a throw that cost no ammo is {@code DISALLOWED}.
 *
 * <h2>No melee</h2>
 *
 * <p>Upstream adds {@code Category.NO_MELEE} and returns no melee attributes
 * ({@code ProjectileCore#getAttributeModifiers}); {@link #getDefaultAttributeModifiers} is
 * accordingly empty, so a swung shuriken hits like a bare hand and {@code weapon = false} keeps it
 * out of every WEAPON-gated path.
 */
public class ShurikenItem extends ToolItem {

    /** {@code ProjectileCore}'s default; the shuriken does not override it. */
    public static final int DURABILITY_PER_AMMO = 10;

    /** {@code Shuriken#onItemRightClick}'s {@code setCooldown(..., 4)}. */
    public static final int THROW_COOLDOWN_TICKS = 4;

    /** {@code Shuriken#getProjectile}'s launch speed, {@code 2.1f}. */
    public static final float THROW_SPEED = 2.1F;

    public ShurikenItem(Properties properties, ToolConstants.Entry constants) {
        // Mines nothing (Category.NO_MELEE, no HARVEST), no innate seam, weapon = false: upstream
        // never gives it Category.WEAPON.
        super(properties, constants, List.of(), false, null);
    }

    /** Ammo left: {@code ProjectileCore#getCurrentAmmo} -- durability over the ratio, hard zero while Broken. */
    public static int currentAmmo(ItemStack stack) {
        if (isBroken(stack)) {
            return 0;
        }
        return (stack.getMaxDamage() - stack.getDamageValue()) / DURABILITY_PER_AMMO;
    }

    /** {@code ProjectileCore#getMaxAmmo}. */
    public static int maxAmmo(ItemStack stack) {
        return stack.getMaxDamage() / DURABILITY_PER_AMMO;
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

    /**
     * {@code ProjectileCore#useAmmo}: one throw costs {@link #DURABILITY_PER_AMMO} durability, and a
     * tool that cannot pay another full throw breaks outright. Returns whether ammo was actually
     * spent -- creative mode never pays ({@code ItemStack#hurtAndBreak} skips players with infinite
     * materials), and the reinforced roll can negate the cost ({@link ToolItem#damageItem}).
     */
    private boolean useAmmo(ItemStack stack, Player player, InteractionHand hand) {
        int before = stack.getDamageValue();
        EquipmentSlot slot = hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        stack.hurtAndBreak(DURABILITY_PER_AMMO, player, slot);
        boolean used = stack.getDamageValue() > before;
        if (used && currentAmmo(stack) <= 0 && !isBroken(stack)) {
            // Not enough durability left for another throw: upstream's useAmmo breakTool endgame.
            stack.hurtAndBreak(stack.getMaxDamage(), player, slot);
        }
        return used;
    }

    /**
     * {@code ProjectileCore#getProjectileStack}: the entity carries a copy holding exactly one ammo
     * and never Broken, so what sticks in the wall (and what a pickup hands back) is one throw's
     * worth of this tool.
     */
    private ItemStack projectileStack(ItemStack stack) {
        ItemStack projectile = stack.copy();
        projectile.remove(ForgeweaveDataComponents.BROKEN.get());
        projectile.setDamageValue(Math.max(0, projectile.getMaxDamage() - DURABILITY_PER_AMMO));
        return projectile;
    }

    /**
     * Upstream {@code TinkerProjectileHandler#pickup} via {@code AmmoHelper}: a picked-up projectile
     * first tops up a matching shuriken -- same item, same materials -- anywhere in the inventory by
     * one ammo. Healing across the Broken line is upstream's own {@code ToolHelper#healTool}, which
     * un-breaks a tool the moment it has durability again; {@code ToolAssemblyRecipes} clears the
     * flag the same way on repair.
     *
     * @return true when a stack absorbed the ammo; false sends the caller down vanilla's own
     *     add-the-item branch
     */
    public static boolean restoreAmmo(Player player, ItemStack projectile) {
        if (!(projectile.getItem() instanceof ShurikenItem)) {
            return false;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack candidate = player.getInventory().getItem(i);
            if (candidate.getItem() != projectile.getItem()
                    || !Objects.equals(candidate.get(ForgeweaveDataComponents.TOOL_MATERIALS.get()),
                            projectile.get(ForgeweaveDataComponents.TOOL_MATERIALS.get()))) {
                continue;
            }
            if (candidate.getDamageValue() <= 0 && !isBroken(candidate)) {
                continue; // already full
            }
            candidate.setDamageValue(Math.max(0, candidate.getDamageValue() - DURABILITY_PER_AMMO));
            candidate.remove(ForgeweaveDataComponents.BROKEN.get());
            return true;
        }
        return false;
    }

    /**
     * No melee attributes at all -- upstream {@code ProjectileCore#getAttributeModifiers} returns
     * the empty per-slot map ("no special attributes for ranged weapons"); its stats reach the world
     * only through the thrown entity.
     */
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return ItemAttributeModifiers.EMPTY;
    }

    /**
     * Upstream {@code ProjectileCore#getInformation} leads with {@code Ammo: current/max}
     * ({@code TooltipBuilder#addAmmo}); the standard tool block follows. The durability line in that
     * block stays -- it is the same number at 10x scale and every other tool shows it.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (stack.has(ForgeweaveDataComponents.TOOL_MATERIALS.get())) {
            // Broken reads "Ammo: Empty" -- upstream TooltipBuilder#addAmmo's textIfEmpty branch,
            // dark red bold like the durability line's own Broken swap.
            tooltip.add(isBroken(stack)
                    ? Component.translatable("tooltip.forgeweave.ammo.empty")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    : Component.translatable("tooltip.forgeweave.ammo",
                            currentAmmo(stack), maxAmmo(stack)).withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
