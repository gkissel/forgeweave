package dev.gkissel.forgeweave.trait;

import java.util.Optional;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveClientConfig;

/**
 * One trait proc announcing itself: the particle and sound {@link TraitFeedback} picked, plus where
 * to draw them (issue #1112).
 *
 * <h2>Why a payload and not {@code sendParticles}/{@code playSound}</h2>
 *
 * <p>The issue asks for a client switch that turns trait feedback off. A server-side
 * {@code ServerLevel#sendParticles} or {@code playSound} broadcast cannot honour one: by the time
 * the packet reaches the client it is an ordinary vanilla particle or sound with nothing on it to
 * say which mod sent it, so the client has no way to drop just this traffic.
 * {@link ForgeweaveClientConfig} is a {@code CLIENT} spec that a dedicated server never loads, so
 * the server cannot consult it either. A payload of our own settles both: the server decides
 * <em>what</em> a proc looks like (rate-limited, override-resolved -- all in {@link TraitFeedback}),
 * and each client decides <em>whether</em> to draw it. A player with the switch off costs the server
 * the same packet and renders nothing, and a player with it on still sees every other player's procs
 * because the broadcast goes to everyone in {@link #RADIUS}.
 *
 * <p>The alternative the issue leaves open -- a per-player preference stored server side -- would
 * need a command, persistence and a sync of its own to reach the same result, and would move a
 * display preference out of the file every other Forgeweave display preference already lives in.
 *
 * @param particle the particle type's id, or empty for a proc that only makes a sound
 * @param sound the sound event's id, or empty for a proc that is only seen
 * @param x where the proc happened
 * @param y the entity's mid-height, matching {@code ForgeweaveParticles#spawnHearts}' own placement
 * @param z where the proc happened
 */
public record TraitFeedbackPayload(Optional<ResourceLocation> particle, Optional<ResourceLocation> sound,
        double x, double y, double z) implements CustomPacketPayload {

    /** How far a proc carries, in blocks. Roughly vanilla's own sound range for a quiet cue. */
    private static final double RADIUS = 32.0;

    /**
     * Quiet on purpose (issue #1112: "keep volumes low"): a trait fires far more often than a
     * crafting cue, and a proc is meant to be noticed rather than announced.
     */
    private static final float VOLUME = 0.35F;

    /** Upstream's own heart-effect count for a one-off proc ({@code ForgeweaveParticles#spawnHearts}). */
    private static final int PARTICLE_COUNT = 3;

    public static final CustomPacketPayload.Type<TraitFeedbackPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "trait_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TraitFeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), TraitFeedbackPayload::particle,
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), TraitFeedbackPayload::sound,
                    ByteBufCodecs.DOUBLE, TraitFeedbackPayload::x,
                    ByteBufCodecs.DOUBLE, TraitFeedbackPayload::y,
                    ByteBufCodecs.DOUBLE, TraitFeedbackPayload::z,
                    TraitFeedbackPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Registered from {@code Forgeweave}'s {@code RegisterPayloadHandlersEvent} listener. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, STREAM_CODEC, (payload, context) -> payload.show(context.player()));
    }

    /** The payload {@link TraitFeedback#fire} sends for a proc on {@code at}. */
    static TraitFeedbackPayload of(Entity at, TraitFeedback.Feedback feedback) {
        return new TraitFeedbackPayload(
                feedback.particle().map(BuiltInRegistries.PARTICLE_TYPE::getKey),
                feedback.sound().map(SoundEvent::getLocation),
                at.getX(), at.getY() + at.getBbHeight() * 0.5, at.getZ());
    }

    /**
     * Sends this to every player close enough to see or hear it, the sender included.
     *
     * <p>Skips a listener that never negotiated this channel rather than using
     * {@code PacketDistributor#sendToPlayersNear}, which throws for one. That is not a theoretical
     * case: a GameTest's mock player has a connection with no channels at all, and so does a vanilla
     * client connected to a server running Forgeweave.
     */
    void broadcast(ServerLevel level) {
        double rangeSqr = RADIUS * RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= rangeSqr && player.connection.hasChannel(TYPE)) {
                PacketDistributor.sendToPlayer(player, this);
            }
        }
    }

    /**
     * Draws the proc on this client, or does nothing when the player turned trait feedback off.
     *
     * <p>Only ever called from the client-bound handler above, which is why reading a {@code CLIENT}
     * config spec here is safe -- see {@link ForgeweaveClientConfig}'s own note on that rule. An id
     * this client does not know (a server-side-only particle from a mod it lacks) is skipped rather
     * than crashing the connection.
     */
    private void show(Player player) {
        if (!ForgeweaveClientConfig.TRAIT_FEEDBACK.get()) {
            return;
        }
        Level level = player.level();
        particle.map(BuiltInRegistries.PARTICLE_TYPE::get)
                .filter(SimpleParticleType.class::isInstance)
                .map(SimpleParticleType.class::cast)
                .ifPresent(type -> spawn(level, type));
        sound.map(BuiltInRegistries.SOUND_EVENT::get)
                .ifPresent(event -> level.playLocalSound(x, y, z, event, SoundSource.PLAYERS, VOLUME, 1.0F, false));
    }

    /** {@code ForgeweaveParticles#spawnHearts}' straight-up launch, one client's copy of it. */
    private void spawn(Level level, ParticleType<?> type) {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            level.addParticle((SimpleParticleType) type, x, y, z, 0.0, 1.0, 0.0);
        }
    }
}
