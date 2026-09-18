package dev.gkissel.forgeweave.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.neoforged.neoforge.common.conditions.ICondition;

import org.junit.jupiter.api.Test;

/**
 * Issue #995: {@link ForgeweaveConfigCondition}'s own logic, independent of a loaded {@code
 * ModConfigSpec} -- flipping {@link ForgeweaveConfig#CREATE_RECIPES} and its three siblings needs a
 * {@code Config} object {@code ModConfigSpec.ConfigValue#set} refuses to work without (a plain JUnit
 * run never loads one, per {@code ConfigValue.set}'s own "Cannot set config value without assigned
 * Config object present"), so the actual on/off round trip for all four toggles is a GameTest instead
 * -- see {@code ProcessingCompatToggleGameTests}, which runs inside a real loaded world the same way
 * {@code CompatToggleGameTests} exercises every other {@code compat} toggle. What stays here is what a
 * loaded spec adds nothing to: every default reads true, and an unrecognized toggle key fails loudly
 * instead of silently resolving either way.
 */
class ForgeweaveConfigConditionTest {

    private static boolean evaluate(String toggle) {
        return new ForgeweaveConfigCondition(toggle).test(ICondition.IContext.TAGS_INVALID);
    }

    @Test
    void everyToggleDefaultsToTrueWhenTheSpecIsNotLoaded() {
        assertTrue(evaluate("createRecipes"));
        assertTrue(evaluate("immersiveEngineeringRecipes"));
        assertTrue(evaluate("enderIoRecipes"));
        assertTrue(evaluate("powahHeatSources"));
    }

    @Test
    void anUnknownToggleKeyThrowsRatherThanSilentlyResolvingEitherWay() {
        assertThrows(IllegalArgumentException.class, () -> evaluate("notARealToggle"));
    }
}
