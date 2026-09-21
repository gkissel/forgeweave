package dev.gkissel.forgeweave.trait;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

import dev.gkissel.forgeweave.particle.ForgeweaveParticles;

/**
 * The one call a trait makes when it actually does something: a particle and a quiet sound over
 * whoever it happened to, rate-limited, broadcast to everyone nearby (issue #1112).
 *
 * <p>One helper rather than one edit per trait id: every proc goes through {@link #fire}, called
 * from the shared behaviour and seam classes the whole roster is built out of, so a trait id that
 * never appears in this package still announces itself. Issue #1112 measured 14 of 303 ids giving
 * the player any signal, which is why the call sites are the seams and not the traits.
 *
 * <h2>What gets feedback and what does not</h2>
 *
 * <p>Only <em>discrete</em> events: something rolled a chance, spent stored state, or changed the
 * world. Per-blow arithmetic -- {@code protection}, {@code bonus_damage_vs},
 * {@code crit_multiplier_bonus}, {@code damage_scales_with}, {@code stat_scales_with_wear} -- is
 * deliberately silent, because a particle on every swing is noise rather than information; those
 * read from the tooltip instead, through their description (issue #1102) or a live
 * {@link dev.gkissel.forgeweave.api.trait.Trait#stateLines} line. Constant attribute bonuses
 * ({@code movement_bonus}, {@code knockback_resistance}, {@code extra_modifier_slots}) never fire at
 * all and so have nothing to announce.
 *
 * <h2>Overrides from a datapack</h2>
 *
 * <p>A {@code trait_definition} may name its own {@code particle} and {@code sound}, or set
 * {@code "silent": true} to say nothing -- the three flat fields {@link Feedback#CODEC} reads, added
 * once in {@code TraitBehaviors#wrap} so every behaviour and every addon behaviour gets them. A
 * field left out falls back to the {@link Kind} the call site named, which is what makes the Java
 * roster work with no per-trait data at all.
 */
public final class TraitFeedback {

    /**
     * What a proc looks and sounds like by default, one constant per flavour of thing a trait does.
     * The particles are the five heart effects {@link ForgeweaveParticles} already ships plus four
     * vanilla ones; every sound is vanilla, played quietly. Nothing new is authored (issue #1112).
     */
    public enum Kind {
        /** An extra bite the weapon took out of the target: armor breaking, a banked strike. */
        STRIKE(ForgeweaveParticles.HEART_BLOOD::get, () -> SoundEvents.PLAYER_ATTACK_CRIT),
        /** Health moving from the target to the attacker: lifesteal. */
        DRAIN(ForgeweaveParticles.HEART_BLOOD::get, () -> SoundEvents.SOUL_ESCAPE.value()),
        /** Fire the trait set, on either side of the blow. */
        BURN(ForgeweaveParticles.HEART_FIRE::get, () -> SoundEvents.FIRECHARGE_USE),
        /** A discharge: chain arcs, lightning, a spent kinetic charge. */
        SHOCK(ForgeweaveParticles.HEART_ELECTRO::get, () -> SoundEvents.TRIDENT_THUNDER.value()),
        /** A status effect applied, stripped or blunted. */
        AFFLICT(() -> ParticleTypes.EFFECT, () -> SoundEvents.BREWING_STAND_BREW),
        /** Armor doing its job: an immunity, an invulnerability window, a vented blast. */
        WARD(ForgeweaveParticles.HEART_ARMOR::get, () -> SoundEvents.AMETHYST_BLOCK_CHIME),
        /** A blow that missed entirely: evasion. */
        DODGE(() -> ParticleTypes.POOF, () -> SoundEvents.SHIELD_BLOCK),
        /** Durability or health coming back: self-repair, damage converted to healing. */
        MEND(() -> ParticleTypes.HAPPY_VILLAGER, () -> SoundEvents.ANVIL_USE),
        /** A death spent on the gear instead of the wearer. */
        SAVE(() -> ParticleTypes.TOTEM_OF_UNDYING, () -> SoundEvents.TOTEM_USE),
        /** Something grew, spread or dropped: fertilizing, a cascading break, a bonus drop. */
        HARVEST(() -> ParticleTypes.HAPPY_VILLAGER, () -> SoundEvents.BONE_MEAL_USE);

        private final Supplier<SimpleParticleType> particle;
        private final Supplier<SoundEvent> sound;

        Kind(Supplier<SimpleParticleType> particle, Supplier<SoundEvent> sound) {
            this.particle = particle;
            this.sound = sound;
        }

        /**
         * This kind's default pair. Resolved per call rather than cached, because the heart particles
         * are a {@code DeferredRegister} entry and are not resolvable while this enum loads.
         */
        public Feedback defaults() {
            return new Feedback(Optional.of(particle.get()), Optional.of(sound.get()), false);
        }
    }

    /**
     * The particle and sound one proc announces itself with: a datapack's own choice, a
     * {@link Kind}'s default, or nothing.
     *
     * @param particle the particle to draw, empty to fall back to the kind's (or to draw none, once
     *     merged)
     * @param sound the sound to play, empty to fall back to the kind's
     * @param silent whether this trait says nothing at all, whatever its kind would have done
     */
    public record Feedback(Optional<SimpleParticleType> particle, Optional<SoundEvent> sound, boolean silent) {

        /** No fields declared: whatever the call site's {@link Kind} says. */
        public static final Feedback DEFAULTS = new Feedback(Optional.empty(), Optional.empty(), false);

        /** Nothing drawn and nothing played, which is what {@code "silent": true} resolves to. */
        public static final Feedback NONE = new Feedback(Optional.empty(), Optional.empty(), true);

        /**
         * A bare particle-type id ({@code "minecraft:flame"}) rather than vanilla's
         * {@code {"type": ...}} wrapper, since every particle a trait would use is a plain one.
         */
        private static final Codec<SimpleParticleType> PARTICLE_CODEC = BuiltInRegistries.PARTICLE_TYPE.byNameCodec()
                .comapFlatMap(type -> type instanceof SimpleParticleType simple
                                ? DataResult.success(simple)
                                : DataResult.error(() -> "Particle type '"
                                        + BuiltInRegistries.PARTICLE_TYPE.getKey(type)
                                        + "' carries options of its own; trait feedback takes a plain particle."),
                        (ParticleType<?> simple) -> simple);

        /** Flat fields on a {@code trait_definition}, all three optional; absent means {@link #DEFAULTS}. */
        public static final MapCodec<Feedback> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                PARTICLE_CODEC.optionalFieldOf("particle").forGetter(Feedback::particle),
                BuiltInRegistries.SOUND_EVENT.byNameCodec().optionalFieldOf("sound").forGetter(Feedback::sound),
                Codec.BOOL.optionalFieldOf("silent", false).forGetter(Feedback::silent))
                .apply(instance, Feedback::new));

        /** Whether this resolves to no packet at all. */
        public boolean isEmpty() {
            return silent || (particle.isEmpty() && sound.isEmpty());
        }

        /** This feedback with {@code kind}'s defaults filling in whichever field it left out. */
        Feedback over(Kind kind) {
            if (silent) {
                return NONE;
            }
            Feedback fallback = kind.defaults();
            return new Feedback(particle.or(fallback::particle), sound.or(fallback::sound), false);
        }
    }

    /**
     * How long one behaviour must wait before announcing itself on the same entity again. Half a
     * second, so a fast weapon's every-hit proc reads as a steady beat rather than a wall
     * (issue #1112: "a fast weapon must not drown the screen").
     */
    static final int COOLDOWN_TICKS = 10;

    /**
     * How many (entity, behaviour) pairs the rate limiter remembers. An LRU rather than a growing
     * map: entities die, and the oldest entry losing its cooldown early only means one extra
     * particle. ponytail: a fixed cap, raise it if a busy server shows repeated procs slipping
     * through.
     */
    private static final int TRACKED_PAIRS = 512;

    /**
     * When each (entity, behaviour) pair last fired. Read and written from the server thread only --
     * every {@link #fire} caller is a trait hook, and those are all server side
     * ({@code Trait}'s class javadoc).
     */
    private static final Map<Long, Long> LAST_FIRED = new LinkedHashMap<>(TRACKED_PAIRS, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > TRACKED_PAIRS;
        }
    };

    /**
     * The {@code trait_definition} overrides, keyed by the behaviour object itself so a call site
     * needs nothing but {@code this}. An {@link IdentityHashMap} on purpose: two definitions with
     * identical parameters are {@code equals} records but are separate traits, and only one of them
     * may have named a sound.
     */
    private static volatile Map<Object, Feedback> OVERRIDES = Map.of();

    /**
     * Called from {@link ForgeweaveTraits#onTagsUpdated} with the feedback the loaded definitions
     * declared. A {@link TraitBehaviors.SeamTrait} is registered under its inner seam as well as
     * itself, because a seam class fires the proc and only ever sees itself.
     */
    static void datapack(Map<net.minecraft.resources.ResourceLocation, TraitDefinition> definitions) {
        Map<Object, Feedback> overrides = new IdentityHashMap<>();
        for (TraitDefinition definition : definitions.values()) {
            Feedback feedback = definition.feedback();
            if (feedback.equals(Feedback.DEFAULTS)) {
                continue;
            }
            overrides.put(definition.trait(), feedback);
            if (definition.trait() instanceof TraitBehaviors.SeamTrait seam) {
                overrides.put(seam.seam(), feedback);
                overrides.put(seam.gated(), feedback);
            }
        }
        OVERRIDES = overrides;
    }

    /**
     * Announces one proc: {@code kind}'s particle and sound (or this behaviour's own overrides) over
     * {@code at}, sent to every player nearby.
     *
     * @param source the behaviour that fired, always {@code this} at the call site -- a
     *     {@code Trait} or a {@code CombatSeam}. Used to find its datapack overrides and to key the
     *     rate limit, both by identity, so nothing about the type matters
     * @param kind which default pair fits what just happened
     * @param level the server level the proc happened in
     * @param at whoever it happened to: the target for an offensive proc, the wearer for a
     *     defensive one, the holder for a tool that repaired itself
     * @return whether anything was broadcast -- {@code false} for a silenced trait and for one the
     *     rate limit just swallowed. Call sites ignore this; the GameTests assert on it
     */
    public static boolean fire(Object source, Kind kind, ServerLevel level, Entity at) {
        Feedback feedback = resolve(source, kind);
        if (feedback.isEmpty() || !allow(at.getId(), source, level.getGameTime())) {
            return false;
        }
        TraitFeedbackPayload.of(at, feedback).broadcast(level);
        return true;
    }

    /** What {@code source} announces a {@code kind} proc with: its own overrides over the kind's defaults. */
    public static Feedback resolve(Object source, Kind kind) {
        Feedback override = OVERRIDES.get(source);
        return override == null ? kind.defaults() : override.over(kind);
    }

    /**
     * Whether this (entity, behaviour) pair is off cooldown, marking it fired when it is. A clock
     * that moved backwards (a world reload mid-session) counts as off cooldown rather than locking
     * the pair out until it catches up.
     */
    static boolean allow(int entityId, Object source, long gameTime) {
        long key = ((long) entityId << 32) | (System.identityHashCode(source) & 0xFFFFFFFFL);
        Long last = LAST_FIRED.get(key);
        if (last != null && gameTime >= last && gameTime - last < COOLDOWN_TICKS) {
            return false;
        }
        LAST_FIRED.put(key, gameTime);
        return true;
    }

    /** Clears the rate limiter, for tests that stage several procs in one tick. */
    static void resetRateLimit() {
        LAST_FIRED.clear();
    }

    private TraitFeedback() {}
}
