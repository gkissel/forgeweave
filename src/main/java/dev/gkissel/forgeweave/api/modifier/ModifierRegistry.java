package dev.gkissel.forgeweave.api.modifier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Where a partner mod adds modifiers from Java (issue #1065), the modifier-side twin of
 * {@code TraitRegistry} and deliberately the same shape:
 *
 * <ul>
 *   <li>{@link #register} adds one finished {@link Modifier} under an id. A {@code modifier_recipe}
 *       then applies it and a tool stores it, exactly as for a built-in one.
 *   <li>{@link #registerBehavior} adds a behavior <em>type</em>: an id plus the codec that reads its
 *       parameters, which a datapack then names in a {@code modifier_definition} file.
 * </ul>
 *
 * <p>Registering a modifier does not make it reachable on its own. A modifier needs a
 * {@code modifier_recipe} naming its id before a station will apply it, and the addon supplies the
 * id's {@code modifier.<namespace>.<path>.name} and {@code .description} lang keys.
 *
 * <h2>The window</h2>
 *
 * <p>Register during mod construction: from your mod's constructor, or from an
 * {@code FMLConstructModEvent} listener. Forgeweave closes the window during its own common setup,
 * which every mod constructor has already run by. A later call throws, and so does a second call
 * for an id another Java registration already owns.
 *
 * <p>A built-in Forgeweave id is the one collision that does not throw. The built-in wins and the
 * registration is ignored, the rule {@code modifier_definition} already follows for a datapack
 * (issue #973). Forgeweave logs the collision when it closes the window.
 *
 * <p>What a tool stores does not change: a modifier is an id plus a level and nothing else
 * (ADR-0004 item 2). A tool carrying an addon's id after the addon is gone keeps the entry inertly
 * and works again the moment the addon returns.
 */
public final class ModifierRegistry {

    private static final Map<ResourceLocation, Modifier> MODIFIERS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, MapCodec<? extends Modifier>> BEHAVIORS = new ConcurrentHashMap<>();

    /** Written once from the mod-loading thread, read from every later thread. */
    private static volatile boolean closed;

    /**
     * Adds {@code modifier} under {@code id}.
     *
     * @throws IllegalStateException if the registration window has closed
     * @throws IllegalArgumentException if another Java registration already owns {@code id}
     */
    public static void register(ResourceLocation id, Modifier modifier) {
        checkOpen(id);
        if (MODIFIERS.putIfAbsent(id, modifier) != null) {
            throw new IllegalArgumentException("Modifier id '" + id + "' is already registered from Java; "
                    + "pick an id in your own namespace.");
        }
    }

    /**
     * Adds a behavior type a {@code modifier_definition} file may name in its {@code behavior} field.
     *
     * @param codec reads the behavior's parameters out of the rest of the file
     * @throws IllegalStateException if the registration window has closed
     * @throws IllegalArgumentException if another Java registration already owns {@code id}
     */
    public static void registerBehavior(ResourceLocation id, MapCodec<? extends Modifier> codec) {
        checkOpen(id);
        if (BEHAVIORS.putIfAbsent(id, codec) != null) {
            throw new IllegalArgumentException("Modifier behavior id '" + id + "' is already registered from Java; "
                    + "pick an id in your own namespace.");
        }
    }

    private static void checkOpen(ResourceLocation id) {
        if (closed) {
            throw new IllegalStateException("Forgeweave's modifier registration window has closed and '" + id
                    + "' arrived too late; register during mod construction (your mod's constructor or an "
                    + "FMLConstructModEvent listener).");
        }
    }

    /** The modifier registered under {@code id}, or {@code null} if nothing registered one. */
    @Nullable
    public static Modifier modifier(ResourceLocation id) {
        return MODIFIERS.get(id);
    }

    /** The behavior codec registered under {@code id}, or {@code null} if nothing registered one. */
    @Nullable
    public static MapCodec<? extends Modifier> behavior(ResourceLocation id) {
        return BEHAVIORS.get(id);
    }

    /** Every Java-registered modifier id. */
    public static Set<ResourceLocation> modifierIds() {
        return Set.copyOf(MODIFIERS.keySet());
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
        MODIFIERS.clear();
        BEHAVIORS.clear();
        closed = false;
    }

    private ModifierRegistry() {}
}
