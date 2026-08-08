package dev.gkissel.forgeweave.block;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;

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

    /** Opens this station's GUI for {@code player}, carrying {@link #writeMenuData}'s payload. */
    default void open(Player player) {
        player.openMenu(this, this::writeMenuData);
    }
}
