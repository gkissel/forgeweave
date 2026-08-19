package dev.gkissel.forgeweave.item;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The guide book's bookmark on close (issue #623): the {@code section.page} name of the page the
 * reader left open, sent up so the server can persist it on the held book item -- upstream 1.12
 * Mantle's {@code PacketUpdateSavedPage}, whose {@code handleServer} writes the client's page
 * string onto the player's held stack (branch {@code 1.12} @ {@code 340a386}, NOTICE.md).
 *
 * <p>Everything the payload carries is untrusted: the string is length-capped by the codec and
 * only ever lands on a {@link GuideBookItem} the sender is actually holding -- upstream writes to
 * whatever occupies the main hand, but a forged packet should not get to tag arbitrary items with
 * data components. It also carries the hand, because Forgeweave's book opens from either hand
 * where upstream hardcodes {@code EnumHand.MAIN_HAND}. An empty page string clears the bookmark
 * (upstream saves {@code ""} for the cover).
 */
public record SavedBookPagePayload(boolean mainHand, String page) implements CustomPacketPayload {

    /**
     * Generous for {@code section.modifier_or_material_path} names (the longest real bookmark
     * today is under 40 chars); a longer string is a forged packet and fails to decode.
     */
    private static final int MAX_ENCODED_LENGTH = 120;

    public static final CustomPacketPayload.Type<SavedBookPagePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "saved_book_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SavedBookPagePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SavedBookPagePayload::mainHand,
                    ByteBufCodecs.stringUtf8(MAX_ENCODED_LENGTH), SavedBookPagePayload::page,
                    SavedBookPagePayload::new);

    public SavedBookPagePayload(InteractionHand hand, String page) {
        this(hand == InteractionHand.MAIN_HAND, page);
    }

    public InteractionHand hand() {
        return this.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Registered from {@code Forgeweave}'s {@code RegisterPayloadHandlersEvent} listener. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(TYPE, STREAM_CODEC,
                (payload, context) -> apply(context.player(), payload.hand(), payload.page()));
    }

    /**
     * The server-side write ({@code PacketUpdateSavedPage#handleServer}): bookmark the guide book
     * in the given hand, or do nothing if that hand no longer holds one. Separated from the
     * network plumbing so {@code GuideBookGameTests} can drive it directly.
     */
    public static void apply(Player player, InteractionHand hand, String page) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof GuideBookItem) {
            if (page.isEmpty()) {
                held.remove(ForgeweaveDataComponents.BOOK_PAGE.get());
            } else {
                held.set(ForgeweaveDataComponents.BOOK_PAGE.get(), page);
            }
        }
    }
}
