package dev.gkissel.forgeweave.compat.mekanism.modules;

import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleContainer;
import mekanism.common.content.gear.mekasuit.ModuleJetpackUnit;
import mekanism.common.item.interfaces.IJetpackItem;
import mekanism.common.registries.MekanismModules;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.trait.EnergyBuffer;

/**
 * The three MekaSuit modules that act on a worn piece rather than on a swing (issue #994, M8-10): the
 * elytra unit, the gravitational modulating unit and the jetpack unit. Split out of
 * {@link MekanismModuleEffects}, which is the tool half, so neither file carries both halves of the
 * integration.
 *
 * <p>Like every other class in this package but {@link MekanismModuleContainer} and
 * {@link MekanismModuleEffects}'s own body, this one names {@code mekanism} types and is only ever
 * reached from inside {@code Forgeweave}'s {@code ModList} guard.
 *
 * <h2>What happens when a module and a Forgeweave armour behaviour want the same tick</h2>
 *
 * <p>They add, and neither can take anything away. Forgeweave's {@code elytra_flight} modifier and an
 * elytra unit are two independent grants of the same glide, so one is enough and two are no better:
 * {@code ArmorPieceItem#canElytraFly} ors them. Forgeweave's {@code creative_flight} modifier wants a
 * full set of four unbroken pieces; a gravitational modulating unit wants a powered chestplate, which
 * is all Mekanism's own unit ever asks for. {@code CreativeFlightHandler} grants flight when
 * <em>either</em> is satisfied, so installing the module never costs a player the set rule they
 * already met, and meeting the set rule never makes the module's own condition stricter. The jetpack
 * touches nothing Forgeweave's armour does: no Forgeweave trait or modifier writes the wearer's
 * vertical motion.
 *
 * <h2>Verified Mekanism coordinates (Mekanism 1.21.1-10.7.19.85, read off the published jar)</h2>
 *
 * <ul>
 *   <li>{@code mekanism.common.registries.MekanismModules#ELYTRA_UNIT},
 *       {@code #GRAVITATIONAL_MODULATING_UNIT}, {@code #JETPACK_UNIT}.
 *   <li>{@code ModuleElytraUnit} carries only mode-change hooks -- the glide itself is
 *       {@code ItemMekaSuitArmor#canElytraFly}, so it is replicated on Forgeweave's own item hook
 *       rather than delegated.
 *   <li>{@code ModuleGravitationalModulatingUnit#tickServer} spends energy and applies the sprint
 *       boost but never grants {@code mayfly}; Mekanism grants that from its own player tick. So
 *       Forgeweave grants it from {@code CreativeFlightHandler}, which already owns that decision, and
 *       leaves the module's own tick to the generic {@link MekanismModuleEffects#tickModules}
 *       dispatch.
 *   <li>{@code ModuleJetpackUnit#mode()} answers an {@code IJetpackItem.JetpackMode}
 *       (NORMAL/HOVER/VECTOR/DISABLED) and {@code #getThrustMultiplier()} a float;
 *       {@code IJetpackItem#handleJetpackMotion(PLAYER, JetpackMode, double, Predicate)} is
 *       Mekanism's own public static thrust maths, with the predicate answering "is this player
 *       holding jump".
 * </ul>
 */
public final class MekanismWornModules {

    /**
     * The thrust a jetpack unit at its {@code NORMAL} multiplier applies, before the module's own
     * multiplier. Mekanism's own base is a number in its config, which Forgeweave does not read: this
     * matches the value that config ships with, and the per-tick price is
     * {@link MekanismGearModules#energyPerFlightTick()} on Forgeweave's side instead.
     */
    private static final double JETPACK_BASE_THRUST = 0.15D;

    /** Whether a powered, switched-on elytra unit on this worn piece lets it glide. */
    public static boolean grantsElytraFlight(ItemStack stack) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        return container != null && powered(stack, container.getIfEnabled(MekanismModules.ELYTRA_UNIT));
    }

    /** Whether a powered, switched-on gravitational modulating unit on this piece grants flight. */
    public static boolean grantsCreativeFlight(ItemStack stack) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        return container != null
                && powered(stack, container.getIfEnabled(MekanismModules.GRAVITATIONAL_MODULATING_UNIT));
    }

    /** A module that is installed, switched on, and has a tick of flight left in the piece's buffer. */
    private static boolean powered(ItemStack stack, @Nullable IModule<?> module) {
        return module != null
                && EnergyBuffer.stored(stack) >= MekanismGearModules.energyPerFlightTick();
    }

    /**
     * One tick of an installed jetpack unit on a worn chestplate.
     *
     * <p><b>Why the motion is client side and the price is server side.</b> Mekanism's thrust maths
     * needs to know whether the player is holding jump. On a dedicated server nothing knows that:
     * vanilla only sends the jump key while the player is riding something, and Mekanism learns it
     * from a packet of its own that Forgeweave has no business sending. {@code LivingEntity#jumping}
     * is truthful on the client, and a player's own motion is client-authoritative anyway -- it is
     * where vanilla applies elytra and creative flight too -- so the thrust runs there. The server
     * charges the buffer on the same tick whenever the module is on and the wearer is off the ground,
     * which is the one place the stack can actually be written. A player who is airborne without
     * thrusting therefore pays anyway; that is the price of not adding a network packet, and it is
     * recorded as a deviation rather than hidden.
     */
    public static void jetpackTick(ItemStack stack, Player player, boolean serverSide) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        if (container == null || player.onGround() || player.isPassenger()) {
            return;
        }
        IModule<ModuleJetpackUnit> module = container.getIfEnabled(MekanismModules.JETPACK_UNIT);
        int cost = MekanismGearModules.energyPerFlightTick();
        if (module == null || EnergyBuffer.stored(stack) < cost) {
            return;
        }
        ModuleJetpackUnit jetpack = module.getCustomInstance();
        if (jetpack.mode() == IJetpackItem.JetpackMode.DISABLED) {
            return;
        }
        if (serverSide) {
            EnergyBuffer.extract(stack, cost, false);
            return;
        }
        IJetpackItem.handleJetpackMotion(player, jetpack.mode(),
                JETPACK_BASE_THRUST * jetpack.getThrustMultiplier(), ascending -> ascending.jumping);
    }

    private MekanismWornModules() {}
}
