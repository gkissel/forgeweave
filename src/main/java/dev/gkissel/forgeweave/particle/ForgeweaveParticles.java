package dev.gkissel.forgeweave.particle;

import java.util.List;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

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

    private ForgeweaveParticles() {}

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
}
