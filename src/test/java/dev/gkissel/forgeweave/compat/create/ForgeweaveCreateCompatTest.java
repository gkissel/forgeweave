package dev.gkissel.forgeweave.compat.create;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Issue #1007: {@link ForgeweaveCreateCompat#isWearingGoggles}, the {@code ItemStack}-only half of
 * the seam {@link ForgeweaveCreateCompat#register} wraps as a {@code Predicate<Player>} for Create's
 * {@code GogglesItem.addIsWearingPredicate}. Deliberately exercised without Create on the classpath
 * at all (build.gradle: compileOnly, main source only) -- this class names no {@code
 * com.simibubi.create} type, per {@code CreateSourceIsolationTest} -- which is the point: the actual
 * predicate is a one-line lambda around this method, and this is what proves the method itself is
 * right.
 */
class ForgeweaveCreateCompatTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void trueOnlyWhenTheHelmetCarriesTheGogglesModifier() {
        ItemStack bareHelmet = new ItemStack(ForgeweaveItems.ARMOR_HELMET.get());
        assertFalse(ForgeweaveCreateCompat.isWearingGoggles(bareHelmet));

        ItemStack withGoggles = bareHelmet.copy();
        withGoggles.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(ForgeweaveModifiers.GOGGLES_ID, 1)));
        assertTrue(ForgeweaveCreateCompat.isWearingGoggles(withGoggles));

        ItemStack withOtherModifier = bareHelmet.copy();
        withOtherModifier.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", "haste"), 1)));
        assertFalse(ForgeweaveCreateCompat.isWearingGoggles(withOtherModifier), "a different modifier isn't goggles");

        assertFalse(ForgeweaveCreateCompat.isWearingGoggles(ItemStack.EMPTY), "an empty hand wears nothing");
    }
}
