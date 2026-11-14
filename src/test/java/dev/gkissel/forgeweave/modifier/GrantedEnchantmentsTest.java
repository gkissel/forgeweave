package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Issue #1213: Scorched Guns looks up its enchantments through a {@code DeferredHolder} on the
 * datapack enchantment registry, which never binds. A level query on a Forgeweave tool must not
 * call {@code value()} on it, or the exception cancels the melee hit.
 */
class GrantedEnchantmentsTest {

    private static final DeferredHolder<Enchantment, Enchantment> UNBOUND =
            DeferredHolder.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath("testaddon", "corroded"));

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void anUnboundHolderReallyThrowsOnValue() {
        assertThrows(IllegalStateException.class, UNBOUND::value);
    }

    @Test
    void combinedSkipsTheEnchantmentWhenNothingIsGranted() {
        assertEquals(0, GrantedEnchantments.combined(UNBOUND, 0, 0));
        assertEquals(3, GrantedEnchantments.combined(UNBOUND, 3, 0));
        assertEquals(2, GrantedEnchantments.combined(UNBOUND, 0, 2));
    }
}
