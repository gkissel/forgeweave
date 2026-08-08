package dev.gkissel.forgeweave.menu;

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
}
