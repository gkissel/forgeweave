package dev.gkissel.forgeweave.compat.draconic.modules;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

/**
 * The Draconic-free half of Forgeweave's module host compat (issue #956): the grid table every
 * caller reads, and the seam the rest of the mod calls into without naming a {@code com.brandon3055}
 * type.
 *
 * <p>Same split {@code ForgeweaveDraconicCompat} already uses. This class is classloaded on every
 * install, Draconic Evolution or not, so it names nothing from that mod; {@link DraconicModuleHost}
 * does, and installs itself here through {@link #install} from inside the {@code ModList} guard in
 * {@code Forgeweave}'s constructor. With no Draconic Evolution present nothing ever installs a
 * bridge and every query below answers zero.
 *
 * <h2>The grid table</h2>
 *
 * <p>The maintainer's allowance (2026-09-04) is 2 / 4 / 8 modules for {@code evolved} I / II / III.
 * Draconic Evolution spends modules on a grid rather than in slots: a module occupies a
 * width-by-height rectangle of cells, and most of the roster is 1x1. So the allowance becomes a grid
 * whose cell count is exactly that number, 2x1, 2x2 and 4x2. Two of the roster are bigger than one
 * cell (the shield controller is 2x2, the energy link 4x4), which is deliberate: an {@code evolved} I
 * tool cannot fit a shield controller at all, {@code evolved} II fits one and nothing else, and the
 * energy link never fits.
 *
 * <p>For scale, Draconic Evolution's own tool grids at the matching tech levels are 4x4 (wyvern),
 * 6x5 (draconic) and 8x6 (chaotic), and its chestpieces 6x5 / 8x6 / 10x8 ({@code ModuleCfg}'s static
 * defaults). Forgeweave's evolved gear is deliberately far tighter than DE's own equipment: modules
 * are a second perk of a fusion metal, not a replacement for building a wyvern pickaxe. Retuning is
 * one edit to {@link #GRIDS}.
 */
public final class DraconicModules {

    /**
     * Grid width and height per {@code evolved} level, index 0 being level I. The cell counts are the
     * maintainer's 2 / 4 / 8; read the class javadoc before changing either number.
     */
    private static final List<int[]> GRIDS = List.of(
            new int[] {2, 1},
            new int[] {2, 2},
            new int[] {4, 2});

    /** The highest {@code evolved} level with a grid, which is also the number of fusion metals. */
    public static final int MAX_EVOLVED = GRIDS.size();

    @Nullable
    private static int[] grid(int evolvedLevel) {
        if (evolvedLevel < 1 || evolvedLevel > MAX_EVOLVED) {
            return null;
        }
        return GRIDS.get(evolvedLevel - 1);
    }

    /** The module grid's width for a tool carrying {@code evolvedLevel}, or 0 for a tool with none. */
    public static int gridWidth(int evolvedLevel) {
        int[] grid = grid(evolvedLevel);
        return grid == null ? 0 : grid[0];
    }

    /** The module grid's height, same contract as {@link #gridWidth}. */
    public static int gridHeight(int evolvedLevel) {
        int[] grid = grid(evolvedLevel);
        return grid == null ? 0 : grid[1];
    }

    /**
     * How many 1x1 modules the grid holds, which is the maintainer's allowance and the {@code m} in
     * issue #955's {@code Draconic upgrades: n of m} tooltip line.
     */
    public static int moduleSlots(int evolvedLevel) {
        return gridWidth(evolvedLevel) * gridHeight(evolvedLevel);
    }

    /** What {@link DraconicModuleHost} answers once Draconic Evolution is installed. */
    public interface Bridge {

        /** How many modules sit on {@code stack} right now. */
        int installedModules(ItemStack stack);

        /** The FE capacity the stack's installed energy modules add, clamped to {@code int}. */
        int moduleEnergyCapacity(ItemStack stack);
    }

    @Nullable
    private static volatile Bridge bridge;

    /** Called once, from inside the {@code ModList} guard. */
    public static void install(Bridge installed) {
        bridge = installed;
    }

    /** {@code n} in issue #955's tooltip line; 0 with no Draconic Evolution. */
    public static int installedModules(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 0 : installed.installedModules(stack);
    }

    /**
     * The FE a stack's Draconic energy modules add to {@code ForgeweaveTraits#energyCapacity}, so one
     * buffer serves a Forgeweave charger and a Draconic energy module alike (issue #956). 0 with no
     * Draconic Evolution, and 0 for a tool carrying no {@code evolved} trait.
     */
    public static int moduleEnergyCapacity(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 0 : installed.moduleEnergyCapacity(stack);
    }

    private DraconicModules() {}
}
