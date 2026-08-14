package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/**
 * Guards issue #335's IMC caller strings: {@link ForgeweaveDarkModeCompat} sends DarkModeEverywhere
 * two {@code fully.qualified.Class:method} strings pinned as plain text, which the compiler can't
 * check. Without this test, renaming or removing either anchor method (a refactor {@code
 * SmelteryScreen#renderFluid} or {@code ToolStationScreen#renderToolLayers} would otherwise sail
 * through unnoticed) would silently break the blacklist entry -- DME would stop excluding that
 * renderer from its darkening shader swap and nobody would find out short of a playtest.
 */
class ForgeweaveDarkModeCompatTest {

    @Test
    void smelteryFluidCallerResolvesToRealClassAndMethod() {
        assertResolves(ForgeweaveDarkModeCompat.SMELTERY_FLUID_CALLER);
    }

    @Test
    void toolStationLayersCallerResolvesToRealClassAndMethod() {
        assertResolves(ForgeweaveDarkModeCompat.TOOL_STATION_LAYERS_CALLER);
    }

    /**
     * Splits a {@code Class:method} caller string exactly the way DME's own blacklist matching
     * treats it, then confirms the class exists and declares a method by that name (any signature --
     * DME's own matching is a caller-string substring match, not a reflective signature lookup, so
     * this only needs to prove the name didn't rot out from under the string).
     */
    private void assertResolves(String classMethodCaller) {
        String[] parts = classMethodCaller.split(":", 2);
        if (parts.length != 2) {
            fail("Expected a 'fully.qualified.Class:method' caller string, got: " + classMethodCaller);
        }
        String className = parts[0];
        String methodName = parts[1];

        Class<?> clazz = assertDoesNotThrow(
                () -> Class.forName(className),
                () -> "Class not found for DME blacklist entry: " + className);

        boolean methodExists = java.util.Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(methodName));
        if (!methodExists) {
            fail("No declared method named '" + methodName + "' on " + className
                    + " -- update ForgeweaveDarkModeCompat's caller string after this rename.");
        }
    }
}
