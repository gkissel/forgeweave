package dev.gkissel.forgeweave.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The Tool Station rename field's text, addressed to a menu by container id (issue #47). Sent up on
 * every edit, and back down to the <em>other</em> players standing at that station so their field
 * reads the same (parity audit T11, issue #443 -- upstream's {@code ToolStationTextPacket} is
 * likewise bidirectional).
 *
 * <p>A custom payload rather than the {@code clickMenuButton} path the tab buttons use, because a
 * menu button carries a single {@code int} and a name is a string -- the same reason vanilla gives
 * the anvil its own {@code ServerboundRenameItemPacket} (which is hard-wired to {@code AnvilMenu}
 * and so unusable here). Everything the payload carries is untrusted:
 * {@link ToolStationMenu#setToolName} filters and length-caps the text, and the container id is
 * checked against the menu the player actually has open before the name reaches it, so a forged
 * packet can only ever rename the sender's own open station.
 */
public record RenameStationItemPayload(int containerId, String name) implements CustomPacketPayload {
    /** Vanilla's own cap for {@code ServerboundRenameItemPacket}; {@code setToolName} trims further. */
    private static final int MAX_ENCODED_LENGTH = 50;

    public static final CustomPacketPayload.Type<RenameStationItemPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "rename_station_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameStationItemPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RenameStationItemPayload::containerId,
                    ByteBufCodecs.stringUtf8(MAX_ENCODED_LENGTH), RenameStationItemPayload::name,
                    RenameStationItemPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Registered from {@code Forgeweave}'s {@code RegisterPayloadHandlersEvent} listener. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playBidirectional(TYPE, STREAM_CODEC, RenameStationItemPayload::handle);
    }

    /**
     * Server-bound this is the text the sender typed; client-bound it is the text <em>another</em>
     * player at the same station typed, echoed by {@code ToolStationMenu#setToolName} so both fields
     * read the same over one shared output slot (parity audit T11, upstream
     * {@code ToolStationTextPacket}). Either way it lands on the addressed menu's
     * {@code setToolName}, which is where the filtering and the length cap live.
     */
    private static void handle(RenameStationItemPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof ToolStationMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setToolName(payload.name());
        }
    }
}
