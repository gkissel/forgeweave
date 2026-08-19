package dev.gkissel.forgeweave.client.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.particle.ForgeweaveParticles;

/**
 * Gives every heart-effect particle type (issue #482) and every attack slash (issue #584) its
 * client-side factory. Upstream 1.12 does the same in {@code ClientProxy#createParticle}'s switch; on
 * 1.21 a type without a provider registered here simply never appears, so this walks
 * {@link ForgeweaveParticles#HEARTS} and {@link ForgeweaveParticles#SLASHES} rather than naming the
 * twelve by hand -- {@code ForgeweaveParticlesTest} asserts the coverage from the other side.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveParticleProviders {

    private ForgeweaveParticleProviders() {}

    @SubscribeEvent
    static void registerProviders(RegisterParticleProvidersEvent event) {
        for (DeferredHolder<ParticleType<?>, SimpleParticleType> heart : ForgeweaveParticles.HEARTS) {
            event.registerSpriteSet(heart.get(), HeartEffectParticle.Provider::new);
        }
        for (ForgeweaveParticles.Slash slash : ForgeweaveParticles.SLASHES) {
            event.registerSpriteSet(slash.type().get(), sprites -> new SlashParticle.Provider(sprites, slash));
        }
    }
}
