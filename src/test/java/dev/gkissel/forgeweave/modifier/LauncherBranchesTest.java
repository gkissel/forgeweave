package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * M3.5 issue #396: the pure halves of the launcher branches upstream 1.12 gives its modifiers and
 * traits -- {@code ModHaste}'s draw-speed bonus, {@code ModLuck}'s category refusal and
 * {@code TraitLightweight}'s draw-speed bonus. The station-level refusal, the real bow's draw and the
 * arrow-hit pipeline are {@code gametest.LauncherBranchGameTests}.
 */
class LauncherBranchesTest {

    private static final float DELTA = 1.0e-6f;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * {@code ModHaste#applyEffect}'s launcher branch: {@code drawSpeed += drawSpeed *
     * getDrawspeedBonus(modData)}, {@code getDrawspeedBonus = 0.1f * current / max} with
     * {@code max = 50} -- +0.2% per redstone, +10% per full level, +50% at level V.
     */
    @Test
    void hasteDrawSpeedMultiplierIsTenPercentPerLevel() {
        assertEquals(1.0f, ForgeweaveModifiers.HASTE.drawSpeedMultiplier(0), DELTA);
        assertEquals(1.002f, ForgeweaveModifiers.HASTE.drawSpeedMultiplier(1), DELTA);
        assertEquals(1.05f, ForgeweaveModifiers.HASTE.drawSpeedMultiplier(25), DELTA);
        assertEquals(1.1f, ForgeweaveModifiers.HASTE.drawSpeedMultiplier(50), DELTA);
        assertEquals(1.5f, ForgeweaveModifiers.HASTE.drawSpeedMultiplier(250), DELTA);
    }

    /** Every other modifier leaves the draw alone -- upstream only {@code ModHaste} touches it. */
    @Test
    void otherModifiersLeaveTheDrawAlone() {
        assertEquals(1.0f, ForgeweaveModifiers.SHARPNESS.drawSpeedMultiplier(72), DELTA);
        assertEquals(1.0f, ForgeweaveModifiers.FIERY.drawSpeedMultiplier(15), DELTA);
        assertEquals(1.0f, ForgeweaveModifiers.LUCK.drawSpeedMultiplier(60), DELTA);
    }

    /**
     * {@code ModLuck}'s aspects: {@code CategoryAnyAspect(HARVEST, WEAPON, PROJECTILE)} -- a bow is
     * {@code TOOL + LAUNCHER} only, so luck refuses it. {@code ModHaste#canApplyCustom} refuses only
     * {@code NO_MELEE} (the projectiles themselves), never a launcher.
     */
    @Test
    void luckRefusesLaunchersAndHasteDoesNot() {
        assertFalse(ForgeweaveModifiers.LUCK.appliesToLaunchers());
        assertTrue(ForgeweaveModifiers.HASTE.appliesToLaunchers());
        assertTrue(ForgeweaveModifiers.FIERY.appliesToLaunchers());
        assertTrue(ForgeweaveModifiers.REINFORCED.appliesToLaunchers());
    }

    /**
     * {@code TraitLightweight#applyEffect}: {@code if(hasCategory(LAUNCHER)) drawSpeed += drawSpeed *
     * bonus} with {@code bonus = 0.1f} -- the same 10% it gives attack speed.
     */
    @Test
    void lightweightSpeedsTheDrawByTenPercent() {
        assertEquals(0.1f, ForgeweaveTraits.LIGHTWEIGHT.drawSpeedBonus(), DELTA);
        assertEquals(0.0f, ForgeweaveTraits.MOMENTUM.drawSpeedBonus(), DELTA);
        assertEquals(0.0f, ForgeweaveTraits.SQUEAKY.drawSpeedBonus(), DELTA);
    }
}
