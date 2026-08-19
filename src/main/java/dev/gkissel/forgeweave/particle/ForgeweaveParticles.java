package dev.gkissel.forgeweave.particle;

import java.util.List;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The five heart-effect particles a hit puts over its target (issue #482, parity audit T51), ported
 * from upstream 1.12's {@code ParticleEffect.Type}: {@code HEART_FIRE}, {@code HEART_CACTUS},
 * {@code HEART_ELECTRO}, {@code HEART_BLOOD} and {@code HEART_ARMOR}, one little coloured heart each,
 * telling the player which of a blow's several damage instances just landed on top of the plain one.
 *
 * <p>Upstream is one {@code Particles.EFFECT} type carrying the {@code Type} ordinal as packet data,
 * because a 1.12 particle indexes its own cell out of one 128x128 sheet. 1.21 stitches every particle
 * sprite into a shared atlas from {@code assets/<ns>/particles/<id>.json}, so the five are five
 * registered {@link SimpleParticleType}s instead, one per sprite -- the same five sprites, chipped out
 * of upstream's sheet by {@code scripts/derive_particle_art.py} (NOTICE.md). Nothing else about them
 * moves: {@link HeartEffectParticle} carries upstream's motion, lifetime and fade verbatim.
 *
 * <p>The spawn helpers below are upstream's {@code CommonProxy#spawnEffectParticle}: {@code count}
 * particles at the entity's mid-height, each launched straight up, which on 1.21 is
 * {@code sendParticles} with a zero count (the form that treats {@code dx/dy/dz} as one particle's
 * velocity rather than a spread) called {@code count} times -- exactly what upstream's {@code
 * ClientProxy#spawnParticle} does when it loops {@code data[0] - 1} extra copies of the same particle.
 *
 * <p>T51's other half lives here too (issue #584): the seven {@link Slash} arcs a fully-charged swing
 * draws in front of the player, upstream's {@code Particles.CLEAVER_ATTACK} and friends. Same porting
 * story -- upstream stepped one sheet's UVs by hand, 1.21 animates a sprite list -- but each of those
 * is its own registered type either way, because upstream gives each its own {@code ParticleAttack}
 * subclass rather than a shared one with a payload.
 */
public final class ForgeweaveParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Forgeweave.MODID);

    /** Fiery's burn ({@code ModFiery#dealFireDamage}). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEART_FIRE = heart("heart_fire");

    /** Prickly's and spiky's cactus thorn ({@code TraitPrickly}/{@code TraitSpiky}). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEART_CACTUS = heart("heart_cactus");

    /** Shocking's discharge ({@code TraitShocking#onHit}). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEART_ELECTRO = heart("heart_electro");

    /** Sharp's bleed tick ({@code TraitSharp#dealDamage}). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEART_BLOOD = heart("heart_blood");

    /** The rapier's armour-skipping half ({@code Rapier#dealHybridDamage}). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEART_ARMOR = heart("heart_armor");

    /** Every heart type, for the client's provider registration and its coverage test. */
    public static final List<DeferredHolder<ParticleType<?>, SimpleParticleType>> HEARTS =
            List.of(HEART_FIRE, HEART_CACTUS, HEART_ELECTRO, HEART_BLOOD, HEART_ARMOR);

    // ------------------------------------------------------------------ attack slashes (#584)

    /**
     * One weapon's full-charge attack slash: the registered particle type plus the three numbers
     * upstream's {@code ParticleAttack} subclass overrides on it. Everything else about the arc --
     * the eight animation phases, the camera-facing quad, the full brightness, the standing-still
     * life -- is shared and lives on {@code client.particle.SlashParticle}.
     *
     * @param type the particle type, whose {@code assets/forgeweave/particles/<id>.json} names the
     *     eight phase sprites in upstream's own order
     * @param size upstream {@code ParticleAttack#size}: the quad's edge in blocks (its half-extent,
     *     which is what 1.21's {@code SingleQuadParticle#quadSize} holds, is half of this)
     * @param height upstream {@code ParticleAttack#height}: a vertical-only stretch of that quad, so
     *     a slash can be taller or flatter than it is wide without the sprite being resized
     * @param lifetime upstream {@code ParticleAttack#lifeTime}, in ticks -- the eight phases are
     *     spread across it, so a shorter life is a faster arc rather than a truncated one
     */
    public record Slash(DeferredHolder<ParticleType<?>, SimpleParticleType> type, float size, float height,
            int lifetime) {}

    /** {@code ParticleAttackCleaver}: {@code height 1.3}. */
    public static final Slash SLASH_CLEAVER = slash("slash_cleaver", 1.0F, 1.3F, 4);

    /**
     * {@code ParticleAttackLongsword}: {@code height 0.5, size 1.8}. The one slash that derives no
     * art -- upstream points it at vanilla's own {@code textures/entity/sweep.png}, which on 1.21 is
     * the {@code minecraft:sweep_0..7} particle sprites its definition names directly.
     */
    public static final Slash SLASH_LONGSWORD = slash("slash_longsword", 1.8F, 0.5F, 4);

    /** {@code ParticleAttackRapier}: {@code size 0.2, lifeTime 2}. */
    public static final Slash SLASH_RAPIER = slash("slash_rapier", 0.2F, 1.0F, 2);

    /** {@code ParticleAttackFrypan}: {@code size 0.9, lifeTime 6}. */
    public static final Slash SLASH_FRYING_PAN = slash("slash_frying_pan", 0.9F, 1.0F, 6);

    /** {@code ParticleAttackHammer}: {@code size 1.2}. */
    public static final Slash SLASH_HAMMER = slash("slash_hammer", 1.2F, 1.0F, 4);

    /** {@code ParticleAttackHatchet}: {@code size 0.8}, on the shared axe sheet. */
    public static final Slash SLASH_HATCHET = slash("slash_hatchet", 0.8F, 1.0F, 4);

    /** {@code ParticleAttackLumberAxe}: {@code size 1.2, lifeTime 6}, on that same axe sheet. */
    public static final Slash SLASH_LUMBERAXE = slash("slash_lumberaxe", 1.2F, 1.0F, 6);

    /** Every slash, for the client's provider registration and its coverage test. */
    public static final List<Slash> SLASHES = List.of(SLASH_CLEAVER, SLASH_LONGSWORD, SLASH_RAPIER,
            SLASH_FRYING_PAN, SLASH_HAMMER, SLASH_HATCHET, SLASH_LUMBERAXE);

    private ForgeweaveParticles() {}

    private static Slash slash(String name, float size, float height, int lifetime) {
        return new Slash(PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false)), size, height,
                lifetime);
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> heart(String name) {
        // false: not "always show" -- upstream's particles obey the client's particle setting too.
        return PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false));
    }

    /**
     * Upstream {@code CommonProxy#spawnEffectParticle(Type, Entity, int)}: {@code count} hearts at the
     * entity's horizontal centre and half its height, each with an upward launch velocity.
     */
    public static void spawnHearts(SimpleParticleType type, ServerLevel level, Entity target, int count) {
        spawnHearts(type, level, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), count);
    }

    /**
     * Upstream {@code CommonProxy#spawnEffectParticle(Type, World, double, double, double, int)} with
     * its launch velocity flipped back up: upstream passes {@code -1} on the world overload only so a
     * particle spawned at a block <em>falls</em>, and no heart-effect caller uses that overload.
     */
    public static void spawnHearts(SimpleParticleType type, ServerLevel level, double x, double y, double z,
            int count) {
        for (int i = 0; i < count; i++) {
            // Count 0 is the "this is one particle's velocity, not a spread" form of sendParticles,
            // which is the only way to reproduce upstream's straight-up launch on every copy.
            level.sendParticles(type, x, y, z, 0, 0.0, 1.0, 0.0, 1.0);
        }
    }

    /**
     * Upstream {@code CommonProxy#spawnAttackParticle(Particles, Entity, double)} (issue #584): one
     * slash a block along the swinger's look vector, at {@code posY + height * heightFactor} -- so the
     * arc hangs in front of where the blow went, at whatever fraction of the swinger's own height
     * that weapon's stroke passes through.
     *
     * <p>Upstream also passes that same look vector as the particle's velocity and then never uses
     * it: {@code ParticleAttack#onUpdate} advances the animation and nothing else, so the arc stands
     * still for its whole life. This spawns with no velocity for exactly that reason -- {@code
     * client.particle.SlashParticle} does not move either, and a zero spread is the honest way to
     * say so.
     *
     * @param heightFactor the fraction of the swinger's height the arc is drawn at, per call site --
     *     upstream varies it by weapon (0.85 cleaver, 0.7 longsword, 0.8 everything else) and by
     *     moment (the frying pan's charged launch draws at 0.6, its landed blow at 0.8)
     */
    public static void spawnSlash(Slash slash, ServerLevel level, Entity swinger, double heightFactor) {
        Vec3 look = swinger.getLookAngle();
        level.sendParticles(slash.type().get(),
                swinger.getX() + look.x,
                swinger.getY() + swinger.getBbHeight() * heightFactor,
                swinger.getZ() + look.z,
                1, 0.0, 0.0, 0.0, 0.0);
    }
}
