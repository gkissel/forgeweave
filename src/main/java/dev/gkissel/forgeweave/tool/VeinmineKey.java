package dev.gkissel.forgeweave.tool;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ToolItem;

/**
 * The veinmine hold-key's server half (issue #719, maintainer decision from the beta.1 playtest):
 * which players currently hold it, and which blocks each tool family may vein while they do.
 *
 * <p>Synced the way vanilla syncs sneak -- the client sends {@link Payload} only when the key's
 * state changes ({@code client/VeinmineKeyMapping}), and the server keeps a flag per player. The
 * flag is a plain {@link Set} of UUIDs cleared on logout rather than a data attachment: nothing
 * about "is the key down right now" should survive a relog. Every path that touches it runs on the
 * server thread.
 *
 * <p>The scope is a block tag per tool family under {@code data/forgeweave/tags/block/veinmine/}
 * ({@code axe}: logs, {@code pickaxe}: ores, {@code shovel}: loose soil -- datagen'd in {@code
 * ForgeweaveBlockTagsProvider}), named after the tool's own vanilla {@code mineable/<family>} tag
 * so a pack can edit it and a family with no tag simply never veins.
 */
public final class VeinmineKey {

    private static final Set<UUID> HELD = new HashSet<>();

    /** Whether {@code player} is holding the veinmine key right now, as last reported by its client. */
    public static boolean held(Player player) {
        return HELD.contains(player.getUUID());
    }

    /** Records the key state; the payload handler and the GameTests are the two callers. */
    public static void set(Player player, boolean held) {
        if (held) {
            HELD.add(player.getUUID());
        } else {
            HELD.remove(player.getUUID());
        }
    }

    /** Registered on the game bus in {@code Forgeweave}: a leaving player's flag goes with them. */
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HELD.remove(event.getEntity().getUUID());
    }

    /** {@code forgeweave:veinmine/<family>} -- the per-family whitelist tag. */
    public static TagKey<Block> family(String family) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "veinmine/" + family));
    }

    /**
     * Whether {@code state} is in the veinmine whitelist of any family {@code tool} mines -- a
     * mattock is both an axe and a shovel, so it veins what either may.
     */
    public static boolean whitelisted(ItemStack tool, BlockState state) {
        if (!(tool.getItem() instanceof ToolItem item)) {
            return false;
        }
        for (TagKey<Block> mineable : item.mineableBlocks()) {
            String path = mineable.location().getPath();
            if (path.startsWith("mineable/") && state.is(family(path.substring("mineable/".length())))) {
                return true;
            }
        }
        return false;
    }

    /** The one-boolean serverbound payload; untrusted, but all it can do is flip the sender's own flag. */
    public record Payload(boolean held) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Payload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "veinmine_key"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, Payload::held, Payload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        /** Registered from {@code Forgeweave}'s {@code RegisterPayloadHandlersEvent} listener. */
        public static void register(PayloadRegistrar registrar) {
            registrar.playToServer(TYPE, STREAM_CODEC, Payload::handle);
        }

        private static void handle(Payload payload, IPayloadContext context) {
            set(context.player(), payload.held());
        }
    }

    private VeinmineKey() {}
}
