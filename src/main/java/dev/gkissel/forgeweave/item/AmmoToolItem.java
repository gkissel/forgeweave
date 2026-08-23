package dev.gkissel.forgeweave.item;

import java.util.List;
import java.util.Objects;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * A tool whose "durability" is an ammo counter -- upstream 1.12's {@code ProjectileCore} (issues
 * #448 and #653, parity audit T17), the shared layer under the shuriken and the material arrow.
 *
 * <p>Upstream abstracts ammo on top of durability: {@code durabilityPerAmmo = 10}
 * ({@link ToolConstants#DURABILITY_PER_AMMO}), ammo = durability / 10, spending one is
 * {@code ToolHelper#damageTool(stack, 10)} and reaching zero breaks the tool
 * ({@code ProjectileCore#useAmmo}). Everything Forgeweave already does to durability therefore
 * works on ammo unchanged: the reinforced negation roll and duritos/dense repricing ride
 * {@link ToolItem#damageItem}, station repair is the reload, the durability bar shows the ammo
 * fraction because it <em>is</em> the ammo fraction, and the Broken state is "Empty". A worn tool
 * that cannot pay a full shot any more (durability left but ammo 0 -- possible because
 * {@link ToolItem#damageItem} floors at {@code maxDamage - 1}) is broken outright, upstream's own
 * {@code useAmmo} endgame.
 *
 * <p>No melee: upstream adds {@code Category.NO_MELEE} and returns no melee attributes
 * ({@code ProjectileCore#getAttributeModifiers}); {@link #getDefaultAttributeModifiers} is
 * accordingly empty, so a swung projectile tool hits like a bare hand and {@code weapon = false}
 * keeps it out of every WEAPON-gated path.
 */
public abstract class AmmoToolItem extends ToolItem {

    /** {@code ProjectileCore}'s ratio; see {@link ToolConstants#DURABILITY_PER_AMMO}. */
    public static final int DURABILITY_PER_AMMO = ToolConstants.DURABILITY_PER_AMMO;

    protected AmmoToolItem(Properties properties, ToolConstants.Entry constants) {
        // Mines nothing, no innate seam, weapon = false: upstream never gives a projectile
        // Category.WEAPON.
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

    /**
     * {@code ProjectileCore#useAmmo}: one shot costs {@link #DURABILITY_PER_AMMO} durability, and a
     * tool that cannot pay another full shot breaks outright. Returns whether ammo was actually
     * spent -- creative mode never pays ({@code ItemStack#hurtAndBreak} skips players with infinite
     * materials), and the reinforced roll can negate the cost ({@link ToolItem#damageItem}).
     */
    protected static boolean useAmmo(ItemStack stack, Player player, InteractionHand hand) {
        int before = stack.getDamageValue();
        EquipmentSlot slot = hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        stack.hurtAndBreak(DURABILITY_PER_AMMO, player, slot);
        boolean used = stack.getDamageValue() > before;
        if (used && currentAmmo(stack) <= 0 && !isBroken(stack)) {
            // Not enough durability left for another shot: upstream's useAmmo breakTool endgame.
            stack.hurtAndBreak(stack.getMaxDamage(), player, slot);
        }
        return used;
    }

    /**
     * {@code ProjectileCore#getProjectileStack}: the entity carries a copy holding exactly one ammo
     * and never Broken, so what sticks in the wall (and what a pickup hands back) is one shot's
     * worth of this tool.
     */
    protected static ItemStack projectileStack(ItemStack stack) {
        ItemStack projectile = stack.copy();
        projectile.remove(ForgeweaveDataComponents.BROKEN.get());
        projectile.setDamageValue(Math.max(0, projectile.getMaxDamage() - DURABILITY_PER_AMMO));
        return projectile;
    }

    /**
     * Upstream {@code TinkerProjectileHandler#pickup} via {@code AmmoHelper}: a picked-up projectile
     * first tops up a matching tool -- same item, same materials -- anywhere in the inventory by
     * one ammo. Healing across the Broken line is upstream's own {@code ToolHelper#healTool}, which
     * un-breaks a tool the moment it has durability again; {@code ToolAssemblyRecipes} clears the
     * flag the same way on repair.
     *
     * @return true when a stack absorbed the ammo; false sends the caller down vanilla's own
     *     add-the-item branch
     */
    public static boolean restoreAmmo(Player player, ItemStack projectile) {
        if (!(projectile.getItem() instanceof AmmoToolItem)) {
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
     * only through the launched entity.
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
