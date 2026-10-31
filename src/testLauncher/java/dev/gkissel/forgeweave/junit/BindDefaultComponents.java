package dev.gkissel.forgeweave.junit;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * Binds every item's default data components before the first unit test runs, by calling
 * {@code dev.gkissel.forgeweave.DefaultComponents#bind} (src/test) inside the game layer.
 *
 * <p>JUnit loads launcher listeners on the application class loader, while the game and the tests
 * run in the class loader FML's own JUnit session listener sets up when the session opens. FML
 * refuses to see a class of the tested mod on the application loader, so this lives in its own
 * source set, touches no game class, and reaches the game layer through
 * {@code FMLLoader.getCurrent().getCurrentClassLoader()} by reflection. A test plan only starts
 * once the session is open, so the game is bootstrapped by then.
 */
public final class BindDefaultComponents implements TestExecutionListener {

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        try {
            Object loader = Class.forName("net.neoforged.fml.loading.FMLLoader").getMethod("getCurrent").invoke(null);
            ClassLoader game = (ClassLoader) loader.getClass().getMethod("getCurrentClassLoader").invoke(loader);
            Class.forName("dev.gkissel.forgeweave.DefaultComponents", true, game).getMethod("bind").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not bind the default data components for the unit tests", e);
        }
    }
}
