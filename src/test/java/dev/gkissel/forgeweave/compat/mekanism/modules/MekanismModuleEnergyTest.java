package dev.gkissel.forgeweave.compat.mekanism.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import net.neoforged.neoforge.energy.IEnergyStorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.trait.EnergyBuffer;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #993's energy gate: the view a Mekanism module gets over issue #830's {@code EnergyBuffer}
 * reports exactly the numbers the buffer holds, and an empty buffer leaves every powered effect inert
 * rather than negative.
 *
 * <p>Mekanism-free on purpose. It drives {@link MekanismGearModules} through a fake bridge, so it
 * exercises the seam every Forgeweave hook actually calls rather than Mekanism's own module objects,
 * which need a live install ({@code IModuleHelper.INSTANCE} resolves through Mekanism's own service and
 * the container is a data component only its registries can decode). Issue #993 lists the live half --
 * a module installing at the Modification Station, vein mining actually finding positions, absorption
 * actually firing -- as manual release-checklist lines on #975 for exactly that reason.
 */
class MekanismModuleEnergyTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearBridge() {
        MekanismGearModules.install(null);
    }

    private static ItemStack plainStack() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }

    /** A bridge that reports one capacity and nothing else, so only the energy fold is under test. */
    private static MekanismGearModules.Bridge capacityBridge(int capacity) {
        return new MekanismGearModules.Bridge() {
            @Override
            public int installedModules(ItemStack stack) {
                return capacity > 0 ? 1 : 0;
            }

            @Override
            public int moduleEnergyCapacity(ItemStack stack) {
                return capacity;
            }
        };
    }

    @Test
    void withNoBridgeEveryQueryAnswersNeutrally() {
        ItemStack stack = plainStack();

        assertEquals(0, MekanismGearModules.installedModules(stack));
        assertEquals(0, MekanismGearModules.moduleEnergyCapacity(stack));
        assertEquals(1.0F, MekanismGearModules.digSpeedMultiplier(stack));
        assertEquals(0, MekanismGearModules.miningAoe(stack));
        assertEquals(0, MekanismGearModules.miningEnergyCost(stack));
        assertEquals(0.0D, MekanismGearModules.radiationShielding(stack));
        assertSame(ItemEnchantments.EMPTY, MekanismGearModules.moduleEnchantments(stack));
        assertTrue(MekanismGearModules.veinPositions(stack, null, null).isEmpty());
        assertSame(MekanismGearModules.Absorption.NONE, MekanismGearModules.damageAbsorbed(stack, null, 4.0F));
    }

    @Test
    void anInstalledEnergyUnitAddsToTheToolsOwnBuffer() {
        MekanismGearModules.install(capacityBridge(50_000));
        ItemStack stack = plainStack();

        assertEquals(50_000, ForgeweaveTraits.energyCapacity(stack),
                "a Mekanism energy unit's capacity folds into the one buffer the tool already has");
    }

    @Test
    void theBufferReportsTheSameNumbersTheModuleViewSees() {
        MekanismGearModules.install(capacityBridge(20_000));
        ItemStack stack = plainStack();

        IEnergyStorage buffer = EnergyBuffer.capability(stack);
        assertEquals(20_000, buffer.getMaxEnergyStored());
        assertEquals(0, buffer.getEnergyStored());
        assertEquals(0, EnergyBuffer.stored(stack));

        EnergyBuffer.receive(stack, 20_000, 7_500, false);
        assertEquals(7_500, EnergyBuffer.stored(stack));
        assertEquals(7_500, EnergyBuffer.capability(stack).getEnergyStored());
        assertEquals(20_000, EnergyBuffer.capability(stack).getMaxEnergyStored());
    }

    @Test
    void anEmptyBufferGivesUpNothingRatherThanGoingNegative() {
        MekanismGearModules.install(capacityBridge(20_000));
        ItemStack stack = plainStack();

        assertEquals(0, EnergyBuffer.extract(stack, MekanismGearModules.energyPerBlock(), true),
                "an empty buffer pays nothing towards a module");
        assertEquals(0, EnergyBuffer.extract(stack, MekanismGearModules.energyPerBlock(), false));
        assertEquals(0, EnergyBuffer.stored(stack), "and is still empty afterwards, not negative");
    }

    @Test
    void aPartialBufferPaysWhatItHasAndStopsThere() {
        MekanismGearModules.install(capacityBridge(20_000));
        ItemStack stack = plainStack();
        EnergyBuffer.receive(stack, 20_000, 100, false);

        assertEquals(100, EnergyBuffer.extract(stack, 5_000, false));
        assertEquals(0, EnergyBuffer.stored(stack));
    }

    @Test
    void noEnergyModuleAndNoEnergyTraitMeansNoBufferAtAll() {
        ItemStack stack = plainStack();

        assertEquals(0, ForgeweaveTraits.energyCapacity(stack));
        assertNull(EnergyBuffer.capability(stack),
                "a tool with no buffer exposes no capability, rather than a zero-capacity one");
    }

    @Test
    void absorptionOfNothingIsNotWorthApplying() {
        assertEquals(0.0F, MekanismGearModules.Absorption.NONE.ratio());
        assertEquals(0, MekanismGearModules.Absorption.NONE.energyCost());
        assertTrue(!MekanismGearModules.Absorption.NONE.any());
        assertTrue(new MekanismGearModules.Absorption(0.25F, 1_000).any());
    }
}
