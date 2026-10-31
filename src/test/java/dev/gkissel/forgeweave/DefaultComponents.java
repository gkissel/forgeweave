package dev.gkissel.forgeweave;

import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;

/**
 * Binds every item's default data components for plain JUnit.
 *
 * <p>26.x binds them only when a server loads its datapacks
 * ({@code ReloadableServerResources#updateComponentsAndStaticRegistryTags}), and a unit test has no
 * server, so every {@code new ItemStack(...)} failed with "Components not bound yet". This does what
 * that method does, over {@link TestRegistries#vanilla()} with a missing tag bound empty (what every
 * tag is in a unit test), then posts the same event, so NeoForge resets each item's cached default
 * stack. {@code dev.gkissel.forgeweave.junit.BindDefaultComponents} (src/testLauncher) calls it once,
 * before the first test.
 */
public final class DefaultComponents {

    private static boolean bound;

    private DefaultComponents() {}

    public static synchronized void bind() {
        if (bound) {
            return;
        }
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(TestRegistries.lenient(TestRegistries.vanilla()))
                .forEach(DataComponentInitializers.PendingComponents::apply);
        NeoForge.EVENT_BUS.post(new DefaultDataComponentsBoundEvent(false, false));
        bound = true;
    }
}
