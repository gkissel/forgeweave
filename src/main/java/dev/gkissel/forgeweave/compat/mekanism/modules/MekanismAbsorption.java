package dev.gkissel.forgeweave.compat.mekanism.modules;

import java.util.function.Consumer;

import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.api.combat.CombatDefense;
import dev.gkissel.forgeweave.api.combat.CombatProviders;
import dev.gkissel.forgeweave.api.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.api.combat.DefendedBlow;
import dev.gkissel.forgeweave.compat.mekanism.ForgeweaveMekanismCompat;
import dev.gkissel.forgeweave.trait.EnergyBuffer;

/**
 * MekaSuit damage absorption, reimplemented on M4's {@code onDefend} seam (issue #993). Mekanism's own
 * {@code ItemMekaSuitArmor#getDamageAbsorbed} walks each worn piece's absorption modules, multiplies
 * their ratios together and spends energy per point absorbed; this does the same arithmetic to a
 * {@link DefendedBlow} and pays out of issue #830's {@code EnergyBuffer}, which D-M8-15 makes the
 * container's energy.
 *
 * <p>Mekanism-free by construction: the ratio and the cost both come across the
 * {@link MekanismGearModules} seam, so this class is safe to classload on a Forgeweave-only install
 * and {@code MekanismSourceIsolationTest} has nothing to complain about.
 *
 * <h2>Deviations from Mekanism's own suit, both so a module only ever adds</h2>
 *
 * <ul>
 *   <li>Absorption lands on the pre-mitigation damage and never below zero, so it reduces the blow
 *       rather than replacing Forgeweave's own armour arithmetic. Mekanism's suit instead owns the
 *       whole reduction for the slots it covers.
 *   <li>An empty buffer means the module does nothing rather than absorbing for free. That is the same
 *       call issue #956 recorded for every powered Draconic module.
 * </ul>
 */
public final class MekanismAbsorption implements CombatSeam, CombatProviders.Provider {

    /** Stateless; one instance, registered from {@link MekanismModuleContainer#register}. */
    public static final MekanismAbsorption INSTANCE = new MekanismAbsorption();

    @Override
    public void collect(ItemStack piece, Consumer<CombatSeam> out) {
        if (ForgeweaveMekanismCompat.isContainerStack(piece)) {
            out.accept(this);
        }
    }

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        float damage = blow.damage();
        if (damage <= 0.0F) {
            return;
        }
        ItemStack piece = defense.tool();
        MekanismGearModules.Absorption absorption =
                MekanismGearModules.damageAbsorbed(piece, defense.defender(), damage);
        if (!absorption.any()) {
            return;
        }
        if (absorption.energyCost() > 0) {
            // simulate first: an absorption the buffer cannot pay for does not happen at all, rather
            // than happening at a discount.
            if (EnergyBuffer.extract(piece, absorption.energyCost(), true) < absorption.energyCost()) {
                return;
            }
            EnergyBuffer.extract(piece, absorption.energyCost(), false);
        }
        blow.setDamage(Math.max(0.0F, damage * (1.0F - Math.min(1.0F, absorption.ratio()))));
    }

    private MekanismAbsorption() {}
}
