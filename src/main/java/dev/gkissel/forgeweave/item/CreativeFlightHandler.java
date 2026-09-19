package dev.gkissel.forgeweave.item;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import dev.gkissel.forgeweave.compat.mekanism.modules.MekanismGearModules; // #994
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;

/**
 * Issue #737 (epic #730 slice 2): creative-style flight while a full set of armor is worn (any
 * weight since issue #1005) and the chestplate carries {@code forgeweave:creative_flight} --
 * revoked the instant that stops being true, whether a piece is removed, a piece breaks, or the
 * chestplate's modifier is gone. Unlike elytra flight (an {@code ArmorPieceItem} item hook, since it
 * only ever needs the one stack it is on), the grant here depends on all four equipment slots at
 * once, which no per-item hook sees -- so, like {@link SlimeBounceHandler}, this is one static
 * per-player tick listener over a weak map rather than a hook on {@code Modifier}.
 *
 * <p>Never touches a creative or spectator player's abilities: those already fly by the gamemode's
 * own right, and {@link Player#onUpdateAbilities} would otherwise fight the gamemode's own packet.
 * Weak keys for the same reason {@link SlimeBounceHandler}'s map is: a player who logs out mid-flight
 * never ticks again and would otherwise be pinned.
 */
public final class CreativeFlightHandler {

    private static final Set<Player> GRANTED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /** Whether {@code player} currently holds mod-granted creative flight -- the tests' window onto the map. */
    public static boolean isGranted(Player player) {
        return GRANTED.contains(player);
    }

    /**
     * All four slots worn with a plate armor piece, any weight (issue #1005 dropped the heavy-only
     * requirement), none Broken, and the chestplate specifically carrying {@code
     * forgeweave:creative_flight} (#737's proposed balance already refuses that modifier anywhere
     * else at the Tool Station, so checking the chestplate slot alone is enough).
     */
    private static boolean wearsFullCreativeFlightSet(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorPieceItem) || ToolItem.isBroken(stack)) {
                return false;
            }
        }
        return ForgeweaveModifiers.grantsCreativeFlight(player.getItemBySlot(EquipmentSlot.CHEST));
    }

    /**
     * Issue #994: a powered Mekanism gravitational modulating unit on an unbroken Forgeweave
     * chestplate, which is all Mekanism's own unit ever asks for -- it never looks at the other three
     * slots. A second, independent grant beside {@link #wearsFullCreativeFlightSet}: installing the
     * module never costs a player the set rule they already met, and meeting the set rule never makes
     * the module's condition stricter. False on every install without Mekanism.
     */
    private static boolean wearsGravityModule(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return chest.getItem() instanceof ArmorPieceItem && !ToolItem.isBroken(chest)
                && MekanismGearModules.grantsCreativeFlight(chest);
    }

    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.isCreative() || player.isSpectator()) {
            // The gamemode itself owns flight here; never let a removed piece pull it back off.
            GRANTED.remove(player);
            return;
        }
        boolean eligible = wearsFullCreativeFlightSet(player) || wearsGravityModule(player);
        boolean granted = GRANTED.contains(player);
        if (eligible && !granted) {
            GRANTED.add(player);
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        } else if (!eligible && granted) {
            GRANTED.remove(player);
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    private CreativeFlightHandler() {}
}
