package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * Issue #996 (D-M8-17): {@code surgebound}'s pure-function coverage -- the crystal-order refusal, the
 * energy-capacity/mining-speed curve computed from the config defaults (so a retune fails this test
 * into passing for the wrong reason rather than silently), and the nitro step (level V) doubling both
 * relative to a linear fifth step. Config is never loaded in a plain unit JVM ({@code
 * ForgeweaveConfig#enabled}'s permissive fallback and every {@code surgebound*()} accessor's own
 * default), so every expectation below is computed from the shipped {@code *_DEFAULT} constants --
 * the same discipline {@code ModifierBatch1Test}'s sharpness/luck tests already use. The
 * {@code powahModifiers} toggle-off "inert" path needs a loaded config spec and is covered by
 * {@code PowahGameTests} instead.
 */
class SurgeboundTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ------------------------------------------------------------------ crystal order

    @Test
    void firstLevelFromZeroIsInOrder() {
        assertTrue(ForgeweaveModifiers.SURGEBOUND.outOfOrderRefusal(0, 1).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"1,2", "2,3", "3,4", "4,5"})
    void eachLaterLevelIsInOrderOnlyRightAfterThePreviousOne(int currentLevel, int targetLevel) {
        assertTrue(ForgeweaveModifiers.SURGEBOUND.outOfOrderRefusal(currentLevel, targetLevel).isEmpty());
    }

    @Test
    void skippingAheadIsRefused() {
        // Level III (niotic) attempted on a tool that never took level II (blazing).
        var refusal = ForgeweaveModifiers.SURGEBOUND.outOfOrderRefusal(1, 3);
        assertTrue(refusal.isPresent());
        assertEquals(Component.translatable("gui.forgeweave.modifier.surgebound_out_of_order", 2, 3), refusal.get());
    }

    @Test
    void reapplyingTheSameOrAnEarlierLevelIsRefused() {
        assertTrue(ForgeweaveModifiers.SURGEBOUND.outOfOrderRefusal(3, 3).isPresent(), "same level again");
        assertTrue(ForgeweaveModifiers.SURGEBOUND.outOfOrderRefusal(3, 2).isPresent(), "an earlier level again");
    }

    // ------------------------------------------------------------------ slot cost (one a level)

    @Test
    void eachLevelCostsExactlyOneSlot() {
        for (int level = 1; level <= 5; level++) {
            assertEquals(level, ForgeweaveModifiers.SURGEBOUND.occupiedSlots(level),
                    "level " + level + " should occupy exactly that many slots, one per level");
        }
    }

    // ------------------------------------------------------------------ capacity/speed curve

    /** Levels I-IV are linear: {@code level * SURGEBOUND_*_PER_LEVEL_DEFAULT}. */
    @ParameterizedTest
    @CsvSource({"1", "2", "3", "4"})
    void linearLevelsScaleWithTheConfiguredPerLevelFraction(int level) {
        float expectedCapacity = level * (float) ForgeweaveConfig.SURGEBOUND_CAPACITY_PER_LEVEL_DEFAULT;
        float expectedSpeed = level * (float) ForgeweaveConfig.SURGEBOUND_MINING_SPEED_PER_LEVEL_DEFAULT;
        assertEquals(expectedCapacity, ForgeweaveModifiers.surgeboundCapacityFraction(level), 1.0e-6F);
        assertEquals(expectedSpeed, ForgeweaveModifiers.surgeboundSpeedFraction(level), 1.0e-6F);
    }

    /**
     * The nitro step (level V) multiplies its own per-level fraction by the nitro multiplier instead
     * of adding a fifth linear step -- worth more than the linear four before it.
     */
    @Test
    void nitroStepDoublesRelativeToALinearFifthStep() {
        float linearFifthStep = 5 * (float) ForgeweaveConfig.SURGEBOUND_CAPACITY_PER_LEVEL_DEFAULT;
        float actualLevel5 = ForgeweaveModifiers.surgeboundCapacityFraction(5);
        assertTrue(actualLevel5 > linearFifthStep, "nitro should be worth more than a plain linear fifth step");

        float expectedLevel5Capacity = 4 * (float) ForgeweaveConfig.SURGEBOUND_CAPACITY_PER_LEVEL_DEFAULT
                + (float) (ForgeweaveConfig.SURGEBOUND_CAPACITY_PER_LEVEL_DEFAULT
                        * ForgeweaveConfig.SURGEBOUND_NITRO_CAPACITY_MULTIPLIER_DEFAULT);
        assertEquals(expectedLevel5Capacity, actualLevel5, 1.0e-6F);
        // Default config: 4*0.25 + 0.25*2 = 1.5 (150%).
        assertEquals(1.5F, actualLevel5, 1.0e-6F);

        float expectedLevel5Speed = 4 * (float) ForgeweaveConfig.SURGEBOUND_MINING_SPEED_PER_LEVEL_DEFAULT
                + (float) (ForgeweaveConfig.SURGEBOUND_MINING_SPEED_PER_LEVEL_DEFAULT
                        * ForgeweaveConfig.SURGEBOUND_NITRO_MINING_SPEED_MULTIPLIER_DEFAULT);
        // Default config: 4*0.05 + 0.05*2 = 0.3 (30%).
        assertEquals(0.3F, expectedLevel5Speed, 1.0e-6F);
        assertEquals(expectedLevel5Speed, ForgeweaveModifiers.surgeboundSpeedFraction(5), 1.0e-6F);
    }

    @Test
    void hooksScaleTheBaseValueNotTheRunningTotal() {
        // 10,000 base capacity, an earlier modifier already folded the running total to 12,000: the
        // level-1 +25% should apply to the untouched 10,000 base, landing at 12,000 + 2,500.
        int result = ForgeweaveModifiers.SURGEBOUND.energyCapacity(1, 12000, 10000);
        assertEquals(14500, result);

        float speedResult = ForgeweaveModifiers.SURGEBOUND.miningSpeed(1, 7.0F, 6.0F);
        assertEquals(7.0F + 6.0F * 0.05F, speedResult, 1.0e-6F);
    }

    @Test
    void levelZeroOrBelowAddsNothing() {
        assertEquals(10000, ForgeweaveModifiers.SURGEBOUND.energyCapacity(0, 10000, 10000));
        assertEquals(6.0F, ForgeweaveModifiers.SURGEBOUND.miningSpeed(0, 6.0F, 6.0F), 1.0e-6F);
    }

    @Test
    void surgeboundHasNoCombatOrExtraSlotSideEffects() {
        assertFalse(ForgeweaveModifiers.SURGEBOUND.armorOnly());
        assertFalse(ForgeweaveModifiers.SURGEBOUND.harvestOnly());
        assertEquals(0, ForgeweaveModifiers.SURGEBOUND.bonusSlots(5));
    }
}
