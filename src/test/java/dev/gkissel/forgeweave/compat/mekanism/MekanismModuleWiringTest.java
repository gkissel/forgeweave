package dev.gkissel.forgeweave.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import mekanism.common.registration.impl.ModuleRegistryObject;
import mekanism.common.registries.MekanismModules;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.compat.mekanism.modules.MekanismGearModules;
import dev.gkissel.forgeweave.compat.mekanism.modules.MekanismGearModules.ModuleWiring;
import dev.gkissel.forgeweave.compat.mekanism.modules.MekanismGearModules.Wiring;

/**
 * Issue #993's "the module-to-hook mapping table is total" gate:
 * {@link MekanismGearModules#MODULE_WIRING} must account for every module Mekanism registers, as
 * wired or as deliberately unwired with a reason. Nothing falls through silently, and a Mekanism
 * update that adds a module fails this test rather than quietly dropping an effect. Issue #994 closed
 * the third case: no row is deferred any more.
 *
 * <p>The roster is read off {@code MekanismModules}' own declared fields rather than off a registry,
 * because {@code Class#getDeclaredFields} does not run a static initialiser: no {@code DeferredRegister}
 * is built, no Minecraft bootstrap is needed, and the test costs nothing. Mekanism is on the test
 * classpath only (build.gradle's {@code testCompileOnly}/{@code testRuntimeOnly} rows); no dev or
 * gametest run ever loads it.
 */
class MekanismModuleWiringTest {

    /** Mekanism names its holders after the registry path, upper-cased. {@code ENERGY_UNIT} is {@code energy_unit}. */
    private static Set<String> mekanismModuleIds() {
        Set<String> ids = new TreeSet<>();
        for (Field field : MekanismModules.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())) {
                continue;
            }
            if (ModuleRegistryObject.class.isAssignableFrom(field.getType())) {
                ids.add(field.getName().toLowerCase(Locale.ROOT));
            }
        }
        return ids;
    }

    @Test
    void theWiringTableAccountsForEveryMekanismModule() {
        Set<String> registered = mekanismModuleIds();
        assertFalse(registered.isEmpty(),
                "read no module holders off MekanismModules -- the field shape this walks has changed");

        List<String> unaccounted = new ArrayList<>();
        for (String id : registered) {
            if (!MekanismGearModules.WIRING_BY_ID.containsKey(id)) {
                unaccounted.add(id);
            }
        }
        assertTrue(unaccounted.isEmpty(),
                "MekanismGearModules.MODULE_WIRING must place every Mekanism module. Missing:\n"
                        + String.join("\n", unaccounted));
    }

    @Test
    void theWiringTableNamesNoModuleMekanismDoesNotHave() {
        Set<String> registered = mekanismModuleIds();
        List<String> stale = MekanismGearModules.MODULE_WIRING.stream()
                .map(ModuleWiring::id)
                .filter(id -> !registered.contains(id))
                .toList();
        assertTrue(stale.isEmpty(),
                "MekanismGearModules.MODULE_WIRING names modules Mekanism no longer registers:\n"
                        + String.join("\n", stale));
    }

    @Test
    void everyRowSaysWhereItLandsAndWhy() {
        for (ModuleWiring row : MekanismGearModules.MODULE_WIRING) {
            assertFalse(row.note().isBlank(), row.id() + " has no note saying which hook or why not");
            assertTrue(Set.of(Wiring.values()).contains(row.wiring()), row.id() + " has no wiring");
        }
    }

    @Test
    void theTableHasOneRowPerIdAndNoDuplicates() {
        assertEquals(MekanismGearModules.MODULE_WIRING.size(), MekanismGearModules.WIRING_BY_ID.size(),
                "MODULE_WIRING has a duplicate id");
    }

    @Test
    void phaseOneWiresTheEffectsIssue993Names() {
        // The seven rows issue #993's own table lists, plus the energy unit every powered one spends.
        for (String id : List.of("energy_unit", "radiation_shielding_unit", "excavation_escalation_unit",
                "silk_touch_unit", "fortune_unit", "frost_walker_unit", "blasting_unit", "vein_mining_unit")) {
            ModuleWiring row = MekanismGearModules.WIRING_BY_ID.get(id);
            assertEquals(Wiring.WIRED, row == null ? null : row.wiring(), id + " is a phase 1 effect");
        }
    }

    @Test
    void phaseTwoWiresTheEffectsIssue994Names() {
        // Issue #994's own roster, plus the attack path phase 1's table left open. Every one of these
        // was DEFERRED before M8-10 and is wired now.
        for (String id : List.of("attack_amplification_unit", "elytra_unit", "farming_unit",
                "gravitational_modulating_unit", "jetpack_unit", "shearing_unit", "teleportation_unit")) {
            ModuleWiring row = MekanismGearModules.WIRING_BY_ID.get(id);
            assertEquals(Wiring.WIRED, row == null ? null : row.wiring(), id + " is a phase 2 effect");
        }
    }

    @Test
    void nothingIsDeferredAnyMore() {
        // Issue #994's "no module is left marked deferred" gate. The enum no longer has a DEFERRED
        // constant at all, so this reads the note text instead: a row that still says "issue #994" or
        // "phase 2" is a row somebody forgot to move.
        List<String> pending = MekanismGearModules.MODULE_WIRING.stream()
                .filter(row -> row.note().contains("#994") || row.note().toLowerCase(Locale.ROOT)
                        .contains("phase 2"))
                .map(ModuleWiring::id)
                .toList();
        assertTrue(pending.isEmpty(), "these rows still defer to phase 2:\n" + String.join("\n", pending));
    }
}
