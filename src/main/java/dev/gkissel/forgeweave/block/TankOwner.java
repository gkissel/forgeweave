package dev.gkissel.forgeweave.block;

/**
 * A multiblock controller that draws fuel from {@link SearedTankBlockEntity}s in its structure and
 * therefore claims them (#288) -- the smeltery cores and, since issue #442, the seared furnace.
 * Upstream 1.12 has the same seam as {@code TileHeatingStructureFuelTank}, the shared base of
 * {@code TileSmeltery} and {@code TileSearedFurnace} (NOTICE.md).
 *
 * <p>Two callers need it: a scan asking whether a tank's current owner is still standing before it
 * takes the tank ({@link SmelteryScan}, {@link SearedFurnaceScan}), and a tank that was just filled
 * waking an owner that stopped ticking for want of heat.
 */
public interface TankOwner {
    /** Whether this owner's structure is currently formed; a claim from an unformed owner is not a claim. */
    boolean isFormed();

    /** A tank in this owner's structure was just filled; resume heating if that is what it was waiting on. */
    void armMeltTick();
}
