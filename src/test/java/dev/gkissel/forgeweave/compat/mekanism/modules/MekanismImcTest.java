package dev.gkissel.forgeweave.compat.mekanism.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mekanism.api.MekanismIMC;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;

/**
 * Playtest 1.0.0 line 8, with Mekanism loaded: 34 "imcMethod already registered" errors, one per
 * Forgeweave gear item. Mekanism reads each IMC method's module messages once, for the first
 * container registered under it, and its own MekaTool and MekaSuit pieces own the five shared
 * names. So every Forgeweave item needs a method of its own, or it gets no modules at all.
 */
class MekanismImcTest {

    private static final Set<String> MEKANISMS_OWN_METHODS = Set.of(
            MekanismIMC.ADD_MEKA_TOOL_MODULES, MekanismIMC.ADD_MEKA_SUIT_HELMET_MODULES,
            MekanismIMC.ADD_MEKA_SUIT_BODYARMOR_MODULES, MekanismIMC.ADD_MEKA_SUIT_PANTS_MODULES,
            MekanismIMC.ADD_MEKA_SUIT_BOOTS_MODULES);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyGearItemRegistersUnderAMethodNoOtherContainerUses() {
        List<Item> gear = MekanismModuleContainer.gearItems();
        assertEquals(34, gear.size(), "non-vacuity: the gear roster the 1.0.0 log counted");

        Set<String> seen = new HashSet<>();
        for (Item item : gear) {
            String method = MekanismModuleContainer.imcMethod(item);
            assertFalse(MEKANISMS_OWN_METHODS.contains(method),
                    item + " registers under " + method + ", which Mekanism's own gear already owns");
            assertTrue(seen.add(method), item + " shares its IMC method " + method + " with another item");
        }
    }
}
