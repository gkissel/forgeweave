package dev.gkissel.forgeweave.api.trait;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Where a partner mod adds traits from Java (issue #1065). Two things can be registered here, and
 * they answer different questions:
 *
 * <ul>
 *   <li>{@link #register} adds one finished {@link Trait} under an id. A material JSON naming that
 *       id gets the behavior, the same way it gets a built-in one. Use this when the logic is
 *       Java and there is nothing to parameterize.
 *   <li>{@link #registerBehavior} adds a behavior <em>type</em>: an id plus the codec that reads
 *       its parameters. A datapack can then name that id in a {@code trait_definition} file and
 *       supply the parameters, so one registration serves every pack that installs the addon.
 * </ul>
 *
 * <h2>The window</h2>
 *
 * <p>Register during mod construction: from your mod's constructor, or from an
 * {@code FMLConstructModEvent} listener. Forgeweave closes the window during its own common setup,
 * which every mod constructor has already run by. A later call throws rather than registering
 * something nothing will ever read, and a second call for an id already registered here throws
 * too: two addons claiming one id is a packaging bug, not a precedence question.
 *
 * <p>A built-in Forgeweave id is the one collision that does not throw. The built-in wins and the
 * registration is ignored, which is the rule {@code trait_definition} already follows for a
 * datapack. Forgeweave logs the collision when it closes the window.
 *
 * <p>Nothing here is gated by {@code compat.kubejsTraits}. That toggle governs KubeJS script
 * traits, and an addon's content is governed by the addon's own config.
 */
public final class TraitRegistry {

    private static final Map<ResourceLocation, Trait> TRAITS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, MapCodec<? extends Trait>> BEHAVIORS = new ConcurrentHashMap<>();

    /** Written once from the mod-loading thread, read from every later thread. */
    private static volatile boolean closed;

    /**
     * Adds {@code trait} under {@code id}.
     *
     * @throws IllegalStateException if the registration window has closed
     * @throws IllegalArgumentException if another Java registration already owns {@code id}
     */
    public static void register(ResourceLocation id, Trait trait) {
        checkOpen(id);
        if (TRAITS.putIfAbsent(id, trait) != null) {
            throw new IllegalArgumentException("Trait id '" + id + "' is already registered from Java; "
                    + "pick an id in your own namespace.");
        }
    }

    /**
     * Adds a behavior type a {@code trait_definition} file may name in its {@code behavior} field.
     *
     * @param codec reads the behavior's parameters out of the rest of the file
     * @throws IllegalStateException if the registration window has closed
     * @throws IllegalArgumentException if another Java registration already owns {@code id}
     */
    public static void registerBehavior(ResourceLocation id, MapCodec<? extends Trait> codec) {
        checkOpen(id);
        if (BEHAVIORS.putIfAbsent(id, codec) != null) {
            throw new IllegalArgumentException("Trait behavior id '" + id + "' is already registered from Java; "
                    + "pick an id in your own namespace.");
        }
    }

    private static void checkOpen(ResourceLocation id) {
        if (closed) {
            throw new IllegalStateException("Forgeweave's trait registration window has closed and '" + id
                    + "' arrived too late; register during mod construction (your mod's constructor or an "
                    + "FMLConstructModEvent listener).");
        }
    }

    /** The trait registered under {@code id}, or {@code null} if nothing registered one. */
    @Nullable
    public static Trait trait(ResourceLocation id) {
        return TRAITS.get(id);
    }

    /** The behavior codec registered under {@code id}, or {@code null} if nothing registered one. */
    @Nullable
    public static MapCodec<? extends Trait> behavior(ResourceLocation id) {
        return BEHAVIORS.get(id);
    }

    /** Every Java-registered trait id. */
    public static Set<ResourceLocation> traitIds() {
        return Set.copyOf(TRAITS.keySet());
    }

    /** Every Java-registered behavior id, for the "known behaviors" list a bad file is told about. */
    public static Set<ResourceLocation> behaviorIds() {
        return Set.copyOf(BEHAVIORS.keySet());
    }

    /** Called once by Forgeweave. Registering after this throws. */
    public static void closeRegistration() {
        closed = true;
    }

    /** Puts the window back where mod construction has it. For tests. */
    static void resetForTests() {
        TRAITS.clear();
        BEHAVIORS.clear();
        closed = false;
    }

    private TraitRegistry() {}
}
