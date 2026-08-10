package dev.gkissel.forgeweave.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import net.neoforged.neoforge.fluids.FluidType;

/**
 * Pins the nine molten metal fluids' temperatures against upstream 1.12's {@code
 * TinkerFluids#setupFluids} constants (docs/SCOPE.md M2 issue #92; NOTICE.md), and rose
 * gold/netherite/netherite scrap's maintainer-picked deviation values (see {@link
 * ForgeweaveFluids}'s class javadoc). Exercises {@link ForgeweaveFluids#moltenFluidType} directly
 * rather than the {@code DeferredHolder}-backed {@code MoltenMetal} constants' {@code .get()} --
 * this needs no more than the same Minecraft bootstrap the rest of the test suite already uses.
 */
class ForgeweaveFluidsTest {

    /** Ported 1:1 from upstream's {@code TinkerFluids#setupFluids} (NOTICE.md); the last three have no 1.12 counterpart. */
    private static final Map<String, Integer> EXPECTED_TEMPERATURES = Map.of(
            "iron", 769,
            "copper", 542,
            "gold", 532,
            "cobalt", 950,
            "ardite", 860,
            "manyullyn", 1000,
            "rose_gold", 550,
            "netherite_scrap", 1100,
            "netherite", 1200);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @ParameterizedTest
    @MethodSource("catalog")
    void temperatureIsQueryableAndMatchesExpected(String metalId, ForgeweaveFluids.MoltenMetal metal) {
        int expected = EXPECTED_TEMPERATURES.get(metalId);

        assertEquals(expected, metal.temperature(), metalId + "'s MoltenMetal.temperature() should match the expected constant");

        FluidType type = ForgeweaveFluids.moltenFluidType(metal.temperature());
        assertEquals(expected, type.getTemperature(), metalId + "'s FluidType.getTemperature() should be queryable and match the expected constant");
    }

    @Test
    void alloysRunHotterThanTheirComponents() {
        // Same pattern upstream uses (manyullyn 1000 > cobalt 950 / ardite 860): an alloy's
        // temperature is higher than either of its inputs, both for the ported and the deviated
        // fluids (see ForgeweaveFluids's class javadoc).
        assertTrue(ForgeweaveFluids.MANYULLYN.temperature() > ForgeweaveFluids.COBALT.temperature());
        assertTrue(ForgeweaveFluids.MANYULLYN.temperature() > ForgeweaveFluids.ARDITE.temperature());
        assertTrue(ForgeweaveFluids.NETHERITE.temperature() > ForgeweaveFluids.NETHERITE_SCRAP.temperature());
        assertTrue(ForgeweaveFluids.NETHERITE.temperature() > ForgeweaveFluids.GOLD.temperature());
    }

    private static Stream<Arguments> catalog() {
        return Stream.of(
                Arguments.of("iron", ForgeweaveFluids.IRON),
                Arguments.of("copper", ForgeweaveFluids.COPPER),
                Arguments.of("gold", ForgeweaveFluids.GOLD),
                Arguments.of("cobalt", ForgeweaveFluids.COBALT),
                Arguments.of("ardite", ForgeweaveFluids.ARDITE),
                Arguments.of("manyullyn", ForgeweaveFluids.MANYULLYN),
                Arguments.of("rose_gold", ForgeweaveFluids.ROSE_GOLD),
                Arguments.of("netherite_scrap", ForgeweaveFluids.NETHERITE_SCRAP),
                Arguments.of("netherite", ForgeweaveFluids.NETHERITE));
    }
}
