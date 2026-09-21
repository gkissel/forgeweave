package dev.gkissel.forgeweave.trait;

import java.util.function.Supplier;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import net.neoforged.neoforge.registries.DeferredHolder;

import dev.gkissel.forgeweave.particle.ForgeweaveParticles;

/**
 * The particle and sound the three stateful #1114 signature behaviours announce a proc with, in one
 * place.
 *
 * <p>Temporary, and deliberately one method: issue #1112 is adding the shared proc-feedback helper
 * this belongs in, with the same shape and a rate limit
 * ({@code TraitFeedback.fire(this, TraitFeedback.Kind.HARVEST, level, at)}). When it lands, delete
 * this class and replace the three calls to {@link #fire} with that one. The {@link Kind} names
 * below are already #1112's, so the swap is the import and the class name.
 *
 * <p>ponytail: no rate limit here. All three callers are gated by a discrete event that cannot
 * repeat within a tick -- a block break, a spent charge bank, a stored blow reaching its threshold
 * -- so the limit #1112 adds for the per-hit procs buys nothing for these three.
 */
final class SignatureFeedback {

    /** Which proc happened. #1112's own preset names, narrowed to the three this issue needs. */
    enum Kind {
        /** Something grew, spread or dropped. */
        HARVEST(ForgeweaveParticles.HEART_CACTUS, SoundEvents.AMETHYST_BLOCK_CHIME),
        /** Extra bite out of the target: a spent charge, a released blow. */
        STRIKE(ForgeweaveParticles.HEART_BLOOD, SoundEvents.PLAYER_ATTACK_CRIT),
        /** The armour did its job. */
        WARD(ForgeweaveParticles.HEART_ARMOR, SoundEvents.SHIELD_BLOCK);

        private final Supplier<SimpleParticleType> particle;
        private final SoundEvent sound;

        Kind(DeferredHolder<ParticleType<?>, SimpleParticleType> particle, SoundEvent sound) {
            this.particle = particle;
            this.sound = sound;
        }
    }

    /** How many particles a proc throws, and how loud it is -- quiet, per 04-impact.md section 5d. */
    private static final int PARTICLES = 4;
    private static final float VOLUME = 0.4F;
    private static final float PITCH = 1.2F;

    /**
     * Announces a proc at {@code at}.
     *
     * @param at whoever the proc happened to: the target for an offensive one, the wearer for a
     *     defensive one, the holder for a tool that changed the world around it
     */
    static void fire(Kind kind, ServerLevel level, Entity at) {
        ForgeweaveParticles.spawnHearts(kind.particle.get(), level, at, PARTICLES);
        level.playSound(null, at.getX(), at.getY(), at.getZ(), kind.sound, SoundSource.PLAYERS, VOLUME, PITCH);
    }

    private SignatureFeedback() {}
}
