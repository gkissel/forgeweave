package dev.gkissel.forgeweave.block;

/**
 * A multiblock controller whose formed structure holds a {@link SmelteryTank} that its I/O blocks
 * re-expose -- the smeltery cores, and since parity audit T44 (issue #475) the seared reservoir.
 * Upstream 1.12's {@code ISmelteryTankHandler}, which {@code TileSmeltery} and {@code TileTinkerTank}
 * both implement and {@code TileDrain} reads through (NOTICE.md).
 *
 * <p>Two callers need the seam: {@link SmelteryIoBlockEntity}, so a drain pours whichever kind of
 * structure claimed it, and that class's unlinked-block sweep, so a drain dropped into a standing
 * structure asks every nearby controller to look again rather than only the smeltery cores.
 */
public interface SmelteryTankHost {
    /** Whether this controller's structure is currently formed. */
    boolean isFormed();

    /** The structure's fluid store. Meaningful only while {@link #isFormed()}. */
    SmelteryTank tank();

    /** Rescans the structure now; a no-op on the client. */
    void updateStructure();
}
