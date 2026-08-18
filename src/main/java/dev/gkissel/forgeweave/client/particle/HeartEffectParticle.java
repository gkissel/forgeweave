package dev.gkissel.forgeweave.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CritParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The little heart a landed secondary hit puts over its target (issue #482, parity audit T51),
 * ported from upstream 1.12's {@code shared/client/ParticleEffect} -- which is exactly vanilla's
 * crit particle with a different sprite, a longer life, and a colour fade of its own:
 *
 * <ul>
 *   <li>{@code particleMaxAge = 20} instead of the crit's randomised 4-10 ticks;
 *   <li>white to start ({@code particleRed = particleGreen = particleBlue = 1}) instead of the crit's
 *       random grey, so the sprite shows its own colours;
 *   <li>an extra launch of {@code motionY += 0.1} and {@code motionX/Z += -0.25 + random * 0.5}, on
 *       top of whatever the spawner asked for -- the little scatter that stops a five-heart burst
 *       from stacking into one heart;
 *   <li>a uniform {@code * 0.975} fade per tick applied <em>around</em> the crit's own asymmetric
 *       {@code green * 0.96, blue * 0.9} fade, cancelling it -- upstream saves all three channels
 *       before the super call and writes them back scaled, which is why this does the same rather
 *       than just scaling after.
 * </ul>
 *
 * <p>Upstream's sheet-indexing half does not survive the port: a 1.12 particle picked its 8x8 cell
 * out of one shared sheet, so all five hearts were a single particle type carrying a {@code Type}
 * ordinal, while 1.21 stitches every sprite into the particle atlas and keys it by particle type.
 * The five are therefore five registered types over one class -- see
 * {@code dev.gkissel.forgeweave.particle.ForgeweaveParticles}.
 */
@OnlyIn(Dist.CLIENT)
public class HeartEffectParticle extends CritParticle {

    /** Upstream {@code ParticleEffect}: {@code particleMaxAge = 20}. */
    private static final int LIFETIME = 20;
    /** Upstream {@code ParticleEffect#onUpdate}: every channel {@code * 0.975} per tick. */
    private static final float FADE_PER_TICK = 0.975F;
    /** Upstream {@code ParticleEffect}: {@code motionY += 0.1f}. */
    private static final double EXTRA_RISE = 0.1;
    /** Upstream {@code ParticleEffect}: {@code motionX/Z += -0.25f + rand.nextFloat() * 0.5f}. */
    private static final double SCATTER = 0.5;

    protected HeartEffectParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.lifetime = LIFETIME;
        this.yd += EXTRA_RISE;
        this.xd += -SCATTER / 2.0 + this.random.nextFloat() * SCATTER;
        this.zd += -SCATTER / 2.0 + this.random.nextFloat() * SCATTER;
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
    }

    @Override
    public void tick() {
        float r = this.rCol;
        float g = this.gCol;
        float b = this.bCol;
        super.tick();
        this.rCol = r * FADE_PER_TICK;
        this.gCol = g * FADE_PER_TICK;
        this.bCol = b * FADE_PER_TICK;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            HeartEffectParticle particle = new HeartEffectParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
