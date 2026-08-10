package dev.gkissel.forgeweave.block;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * A station block entity that can be asked to open its own GUI (issue #78).
 *
 * <p>Every station already had the same two lines in its block's {@code useWithoutItem} -- gather
 * whatever the client-side menu constructor needs, then {@code player.openMenu(be, writer)}. That
 * was fine while the only way to open a station was clicking it, but the station-group tab row opens
 * a <em>neighbour's</em> menu from inside {@link dev.gkissel.forgeweave.menu.StationGroup#open}, and
 * that code only has a {@code BlockPos}. Duplicating each station's packet payload there would be
 * five copies waiting to drift out of step with the five menu constructors that read them, so the
 * payload lives on the block entity instead and both callers go through {@link #open}.
 */
public interface StationMenuHost extends MenuProvider {

    /**
     * Writes the extra data this station's client-side menu constructor reads, in the same order it
     * reads it. Called on the server only.
     */
    void writeMenuData(RegistryFriendlyByteBuf buf);

    /**
     * Opens this station's GUI for {@code player}, carrying {@link #writeMenuData}'s payload.
     *
     * <p>Skipped for a player whose connection cannot carry it. {@code openMenu} with extra data
     * goes out as NeoForge's {@code advanced_open_screen} payload, and sending that down a
     * connection which never negotiated the channel throws rather than degrading -- which is what a
     * GameTest's mock {@code ServerPlayer} is (issue #101: opening the smeltery GUI from
     * {@code useWithoutItem} made #110's advancement tests fail on the packet, not on the
     * behaviour they assert). Guarded here rather than at one call site because all six stations
     * open through this method and any of them can be driven by {@code GameTestHelper#useBlock}.
     */
    default void open(Player player) {
        if (player instanceof ServerPlayer serverPlayer
                && !NetworkRegistry.hasChannel(serverPlayer.connection, AdvancedOpenScreenPayload.TYPE.id())) {
            return;
        }
        player.openMenu(this, this::writeMenuData);
    }
}
