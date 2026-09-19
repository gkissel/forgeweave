package dev.gkissel.forgeweave.api.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.world.item.ItemStack;

/**
 * Who attaches combat behavior to a blow, and the seam a partner mod registers through (issue
 * #1065). One {@link Provider} per source of combat behavior: materials' traits, a tool's innates,
 * combat modifiers, and whatever an addon brings. {@code CombatSeams} reads this list on every hit
 * and drives the hooks off NeoForge's damage and death events.
 *
 * <p>Registration order is hook order, and there are no priorities and no cancellation: a seam that
 * wants to stop a hit sets its damage to zero like any other adjustment. Forgeweave registers its
 * own providers during mod construction, so an addon registering from its own constructor lands
 * after them.
 *
 * <p>This class is the public half of what used to be {@code CombatSeams.register}. The event
 * pipeline stayed behind in {@code dev.gkissel.forgeweave.combat}, which names tool and armor item
 * types the api package may not.
 */
public final class CombatProviders {

    /**
     * Supplies the seams that apply to one weapon stack. Called on every hit, so an implementation
     * should read the stack's components and hand back seams rather than do real work itself.
     */
    @FunctionalInterface
    public interface Provider {
        void collect(ItemStack weapon, Consumer<CombatSeam> out);
    }

    private static final List<Provider> PROVIDERS = new ArrayList<>();

    /** Registers a source of combat behavior. Call order is hook order; see the class javadoc. */
    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    /**
     * The seams that apply to {@code weapon}, in registration order, or an empty list if none do.
     * Public so a GameTest can assert what a given tool resolves to without staging a real blow.
     */
    public static List<CombatSeam> seams(ItemStack weapon) {
        List<CombatSeam> seams = new ArrayList<>();
        for (Provider provider : PROVIDERS) {
            provider.collect(weapon, seams::add);
        }
        return seams;
    }

    private CombatProviders() {}
}
