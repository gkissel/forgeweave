package dev.gkissel.forgeweave.menu;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;

/**
 * Base class for all six station menus, carrying the one thing every station GUI now needs: the
 * {@link StationGroup} its tab row is drawn from (issue #78).
 *
 * <p>It exists for the same reason {@code StationScreen} does -- the alternative was the same field,
 * the same constructor plumbing and the same {@code clickMenuButton} branch copied six times, which
 * is exactly the shape of duplication that let issue #75's tooltip defect reappear in three screens.
 * Menus with buttons of their own ({@code ToolStationMenu}, {@code StencilTableMenu},
 * {@code PartBuilderMenu}) call {@code super.clickMenuButton} first and handle their own ids only if
 * it declines; {@link StationGroup#isTabButton} keeps the ranges apart.
 */
public abstract class StationMenu extends AbstractContainerMenu {

    private final StationGroup stationGroup;

    protected StationMenu(MenuType<?> type, int containerId, StationGroup stationGroup) {
        super(type, containerId);
        this.stationGroup = stationGroup;
    }

    /**
     * The server-side group for the station at {@code access}'s position, or {@link
     * StationGroup#EMPTY} on the client (where {@code access} is {@link ContainerLevelAccess#NULL}
     * and the real group arrives through the open-menu packet instead).
     */
    protected static StationGroup groupAt(ContainerLevelAccess access) {
        return access.evaluate(StationGroup::tabsFor).orElse(StationGroup.EMPTY);
    }

    /** The connected stations this GUI shows tabs for; empty when there is no tab row. */
    public StationGroup stationGroup() {
        return stationGroup;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return StationGroup.isTabButton(id) && stationGroup.open(player, id - StationGroup.TAB_BUTTON_BASE);
    }

    /**
     * Why the loaded slots produce nothing, and how loudly to say it -- upstream 1.12's
     * {@code GuiTinkerStation#error(String)}/{@code #warning(String)} pair (issue #378), which every
     * station GUI implements and which <em>takes the info panel over</em> rather than adding a line
     * to it: the caption becomes {@code gui.error}/{@code gui.warning} and the body becomes the
     * message ({@code GuiToolStation:562-575}, {@code GuiPartBuilder:179-189}).
     *
     * <p>The split is upstream's own usage, not a severity guess. Everything a craft attempt throws
     * as a {@code TinkerGuiException} -- caught in {@code ContainerToolStation#onCraftMatrixChanged}
     * and handed straight to {@code error} -- blocks the craft and is an <b>error</b>. The two
     * messages the GUI instead derives by looking at what is already in the slots
     * ({@code gui.error.wrong_material_part}, {@code gui.error.useless_tool_part}) call
     * {@code warning} instead: nothing failed, the loadout was never going to build anything.
     *
     * <p>Lives on the shared base because both stations that show one produce it and both screens
     * consume it identically; {@link #caption()} and {@link #body()} are that shared shape, so a
     * takeover cannot end up looking different on the two screens.
     */
    public record Rejection(Component message, boolean warning) {

        /** A craft that was refused: upstream's {@code error(...)}, captioned ERROR. */
        public static Rejection error(Component message) {
            return new Rejection(message, false);
        }

        /** A loadout that can never craft: upstream's {@code warning(...)}, captioned WARNING. */
        public static Rejection warning(Component message) {
            return new Rejection(message, true);
        }

        /** Upstream's {@code gui.error}/{@code gui.warning} -- "ERROR" / "WARNING". */
        public Component caption() {
            return Component.translatable(warning ? "gui.forgeweave.warning" : "gui.forgeweave.error");
        }

        /** Upstream's {@code setText(message)}: the message replaces the panel's body outright. */
        public List<Component> body() {
            return List.of(message);
        }
    }
}
