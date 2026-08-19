package dev.gkissel.forgeweave.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.HumanoidArm;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import dev.gkissel.forgeweave.particle.ForgeweaveParticles;

/**
 * The arc a fully-charged swing draws in front of the player (issue #584, parity audit T51), ported
 * from upstream 1.12's {@code library/client/particle/ParticleAttack} -- one class for all seven
 * weapons, since upstream's own subclasses override nothing but the three numbers
 * {@link ForgeweaveParticles.Slash} carries (size, height, lifetime) and which sheet to bind.
 *
 * <p>What survives the port verbatim:
 *
 * <ul>
 *   <li><b>Eight phases across the lifetime.</b> Upstream picks its cell every <em>frame</em> from
 *       {@code (int) ((life + partialTicks) / lifeTime * 8)}, not every tick, which is what lets a
 *       two-tick rapier flick still play all eight. So the phase is chosen in {@link
 *       #renderRotatedQuad} with the partial tick in hand rather than through
 *       {@code TextureSheetParticle#setSpriteFromAge}, whose per-tick stepping would drop half of a
 *       four-tick arc and three quarters of a two-tick one.
 *   <li><b>It stands still.</b> {@code ParticleAttack#onUpdate} advances the animation and nothing
 *       else -- no {@code move}, no gravity, no drag -- so the arc hangs where the swing put it. The
 *       velocity upstream passes at spawn is dead weight; see
 *       {@code ForgeweaveParticles#spawnSlash}.
 *   <li><b>Full brightness.</b> {@code getBrightnessForRender} returns a hard-coded max, so a slash
 *       reads the same in a cave as at noon.
 *   <li><b>The vertical stretch.</b> {@code height} scales only the quad's world-vertical extent
 *       ({@code rotationZ * f4 * height} on the Y line and nowhere else), which is why this needs a
 *       {@code SingleQuadParticle#renderRotatedQuad} override at all -- the base class renders a
 *       square.
 *   <li><b>The left-handed flip.</b> Upstream swaps the cell's two U coordinates when the client's
 *       main hand is left, so the arc sweeps the way the player's arm did.
 * </ul>
 *
 * <p>Deviation: the render type is 1.21's alpha-blended particle sheet rather than upstream's
 * {@code getFXLayer() == 3} custom layer, which existed only so a 1.12 particle could bind its own
 * texture mid-frame. 1.21 stitches every phase into the shared particle atlas, so there is nothing
 * left to bind and the blend mode is all that carried over.
 */
@OnlyIn(Dist.CLIENT)
public class SlashParticle extends TextureSheetParticle {

    /** Upstream {@code ParticleAttack#init}: {@code animPhases = 8}. */
    private static final int PHASES = 8;

    private final SpriteSet sprites;
    /** Upstream {@code ParticleAttack#height}: a world-vertical stretch of the quad. */
    private final float height;

    SlashParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites,
            ForgeweaveParticles.Slash slash) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.height = slash.height();
        // quadSize is the quad's half-extent (renderVertex scales offsets of +-1 by it); upstream's
        // size is the whole edge, its own f4 being 0.5 * size.
        this.quadSize = slash.size() * 0.5F;
        this.lifetime = slash.lifetime();
        this.setSprite(sprites.get(0, PHASES - 1));
    }

    /** Upstream {@code ParticleAttack#onUpdate}: age the arc, move nothing. */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (++this.age >= this.lifetime) {
            this.remove();
        }
    }

    /** Upstream {@code getBrightnessForRender}: a hard-coded maximum, ignoring the block's light. */
    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * Upstream {@code ParticleAttack#renderParticle}, in 1.21's quaternion form: pick the phase from
     * the exact (fractional) age, stretch the quad vertically by {@link #height}, and mirror it for a
     * left-handed client. Reimplemented rather than delegated because the base class emits a square
     * quad from the sprite's own UVs and offers no hook for either the stretch or the mirror.
     */
    @Override
    protected void renderRotatedQuad(VertexConsumer buffer, Quaternionf quaternion, float x, float y, float z,
            float partialTicks) {
        int phase = (int) ((this.age + partialTicks) / this.lifetime * PHASES);
        if (phase >= PHASES) {
            return; // upstream's own `if(i < animPhases)`: the last partial tick draws nothing
        }
        this.setSprite(this.sprites.get(phase, PHASES - 1));

        boolean leftHanded = Minecraft.getInstance().options.mainHand().get() == HumanoidArm.LEFT;
        float u0 = leftHanded ? this.getU1() : this.getU0();
        float u1 = leftHanded ? this.getU0() : this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        float half = this.getQuadSize(partialTicks);
        int light = this.getLightColor(partialTicks);

        vertex(buffer, quaternion, x, y, z, 1.0F, -1.0F, half, u1, v1, light);
        vertex(buffer, quaternion, x, y, z, 1.0F, 1.0F, half, u1, v0, light);
        vertex(buffer, quaternion, x, y, z, -1.0F, 1.0F, half, u0, v0, light);
        vertex(buffer, quaternion, x, y, z, -1.0F, -1.0F, half, u0, v1, light);
    }

    private void vertex(VertexConsumer buffer, Quaternionf quaternion, float x, float y, float z,
            float xOffset, float yOffset, float half, float u, float v, int light) {
        // The stretch is applied after the billboard rotation, on the world Y axis, because that is
        // where upstream applies it -- its horizontal camera axis contributes nothing to world Y, so
        // scaling the rotated vector's Y is exactly its `rotationZ * f4 * height`.
        Vector3f corner = new Vector3f(xOffset, yOffset, 0.0F).rotate(quaternion).mul(half);
        corner.y *= this.height;
        corner.add(x, y, z);
        buffer.addVertex(corner.x(), corner.y(), corner.z())
                .setUv(u, v)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light);
    }

    /** One factory per {@link ForgeweaveParticles.Slash}; see {@link ForgeweaveParticleProviders}. */
    @OnlyIn(Dist.CLIENT)
    public record Provider(SpriteSet sprites, ForgeweaveParticles.Slash slash)
            implements ParticleProvider<SimpleParticleType> {

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            return new SlashParticle(level, x, y, z, this.sprites, this.slash);
        }
    }
}
