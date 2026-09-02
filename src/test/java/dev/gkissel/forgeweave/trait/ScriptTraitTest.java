package dev.gkissel.forgeweave.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

/**
 * Issue #832's script-trait half, without KubeJS: {@link ScriptTrait} routes each hook to its
 * callback (or keeps {@link Trait}'s default when unset), and {@link ForgeweaveTraits#registerScripted}
 * feeds {@link ForgeweaveTraits#lookup} without ever shadowing a built-in id.
 */
class ScriptTraitTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void setHooksRunTheirCallbacksAndUnsetOnesKeepTheDefaults() {
        ScriptTrait trait = new ScriptTrait()
                .onHeadDurability(durability -> durability * 2)
                .onMiningSpeed((stack, effective, original, speed) -> speed + 1.0F)
                .bonusSlots(1)
                .silkTouch();

        assertEquals(20, trait.headDurability(10));
        assertEquals(4.0F, trait.miningSpeed(ItemStack.EMPTY, true, 3.0F, 3.0F));
        assertEquals(1, trait.bonusSlots());
        assertTrue(trait.grantsSilkTouch());

        assertEquals(0, trait.repairBonus(5), "unset: Trait's default");
        assertEquals(7, trait.durabilityDamage(ItemStack.EMPTY, null, 7, 7), "unset: passthrough");
        assertFalse(trait.autoSmelt());
        assertEquals(0.0F, trait.attackSpeedBonus());
    }

    @Test
    void scriptedIdsResolveThroughLookupButNeverShadowABuiltIn() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("somepack", "frosty");
        assertNull(ForgeweaveTraits.lookup(id));

        ScriptTrait trait = new ScriptTrait();
        ForgeweaveTraits.registerScripted(id, trait);
        assertSame(trait, ForgeweaveTraits.lookup(id));

        ScriptTrait replacement = new ScriptTrait();
        ForgeweaveTraits.registerScripted(id, replacement);
        assertSame(replacement, ForgeweaveTraits.lookup(id), "a script reload re-registers the same id");

        ResourceLocation builtIn = ResourceLocation.fromNamespaceAndPath("forgeweave", "poisonous");
        assertThrows(IllegalArgumentException.class, () -> ForgeweaveTraits.registerScripted(builtIn, new ScriptTrait()));
        assertSame(ForgeweaveTraits.POISONOUS, ForgeweaveTraits.lookup(builtIn));
    }
}
