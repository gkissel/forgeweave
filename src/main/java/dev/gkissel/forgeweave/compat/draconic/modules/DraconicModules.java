package dev.gkissel.forgeweave.compat.draconic.modules;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
 * <p>Draconic Evolution spends modules on a grid rather than in slots: a module occupies a
 * width-by-height rectangle of cells, and most of the roster is 1x1. The maintainer's table (issue
 * #965, 2026-09-04) is 2x3 inert, 2x6 wyvern, 4x5 awakened and 6x6 chaotic, width by height, so the
 * 1x1 allowance those grids work out to is 6 / 12 / 20 / 36. It replaces the 2x1 / 2x2 / 4x2 table
 * issue #962 shipped, which the same maintainer call widened.
 *
 * <p>Two of Draconic Evolution's roster are bigger than one cell, and the shapes rather than the
 * cell counts decide where they land: the shield controller is 2x2 and fits every tier including the
 * inert one, and the energy link is 4x4, which needs the 4x5 awakened grid or the 6x6 chaotic one --
 * the 2-wide inert and wyvern grids cannot take it however many cells they have.
 *
 * <p>For scale, Draconic Evolution's own tool grids at the matching tech levels are 4x4 (wyvern),
 * 6x5 (draconic) and 8x6 (chaotic), and its chestpieces 6x5 / 8x6 / 10x8 ({@code ModuleCfg}'s static
 * defaults). Forgeweave's evolved gear is still under DE's own equipment at every tier, and the
 * inert tier has no DE counterpart at all. Retuning is one edit to {@link #GRIDS}, which is also
 * where {@code ForgeweaveTraits}'s tooltip allowance reads its numbers.
 */
public final class DraconicModules {

    /**
     * Grid width and height per {@code evolved} level, index 0 being level 1 (inert). The one table
     * behind both the {@code ModuleHostImpl} the capability hands out and issue #955's tooltip
     * allowance; read the class javadoc before changing a number.
     */
    private static final List<int[]> GRIDS = List.of(
            new int[] {2, 3},
            new int[] {2, 6},
            new int[] {4, 5},
            new int[] {6, 6});

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
     * issue #955's {@code Draconic upgrades: n of m} tooltip line. 0 for a level with no grid, which
     * is what a tool carrying no tier marker gets.
     */
    public static int moduleSlots(int evolvedLevel) {
        return gridWidth(evolvedLevel) * gridHeight(evolvedLevel);
    }

    /**
     * What a projectile module does to one shot from a Forgeweave bow (issue #956 phase 2), as
     * Draconic Evolution's own {@code ProjectileData} states it: each number is a fraction added to
     * one, so {@link #NONE}'s three zeroes leave the shot exactly as Forgeweave built it.
     *
     * @param velocity multiplies the shot's speed by {@code 1 + velocity}
     * @param accuracy multiplies the shot's inaccuracy by {@code 1 - accuracy}
     * @param damage multiplies the arrow's damage by {@code 1 + damage}
     */
    public record Projectile(float velocity, float accuracy, float damage) {

        /** No projectile module, no Draconic Evolution, or a buffer too empty to pay for the shot. */
        public static final Projectile NONE = new Projectile(0.0F, 0.0F, 0.0F);

        /** Whether this is worth applying at all. */
        public boolean any() {
            return velocity != 0.0F || accuracy != 0.0F || damage != 0.0F;
        }
    }

    /** What {@link DraconicModuleHost} answers once Draconic Evolution is installed. */
    public interface Bridge {

        /** How many modules sit on {@code stack} right now. */
        int installedModules(ItemStack stack);

        /** The FE capacity the stack's installed energy modules add, clamped to {@code int}. */
        int moduleEnergyCapacity(ItemStack stack);

        // The tool-active effects (issue #956 phase 2). Each one defaults to doing nothing, so a
        // test's fake bridge names only the effect it is about, and so a bridge written before an
        // effect existed keeps compiling.

        /** See {@link DraconicModules#digSpeedMultiplier}. */
        default float digSpeedMultiplier(ItemStack stack) {
            return 1.0F;
        }

        /** See {@link DraconicModules#miningAoe}. */
        default int miningAoe(ItemStack stack) {
            return 0;
        }

        /** See {@link DraconicModules#miningEnergyCost}. */
        default int miningEnergyCost(ItemStack stack) {
            return 0;
        }

        /** See {@link DraconicModules#attackDamageBonus}. */
        default float attackDamageBonus(ItemStack stack) {
            return 0.0F;
        }

        /** See {@link DraconicModules#meleeAoe}. */
        default boolean meleeAoe(Player player, Entity target, ItemStack stack, float damage) {
            return false;
        }

        /** See {@link DraconicModules#projectile}. */
        default Projectile projectile(ItemStack stack) {
            return Projectile.NONE;
        }

        /** See {@link DraconicModules#shotEnergyCost}. */
        default int shotEnergyCost(ItemStack stack) {
            return 0;
        }
    }

    @Nullable
    private static volatile Bridge bridge;

    /**
     * Called once, from inside the {@code ModList} guard. {@code null} puts the state back where an
     * install without Draconic Evolution has it, which is what a test that installed a fake bridge
     * restores.
     */
    public static void install(@Nullable Bridge installed) {
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

    /**
     * What a speed module multiplies {@code ToolItem#getDestroySpeed}'s own answer by (issue #956
     * phase 2). {@code 1} -- change nothing -- with no Draconic Evolution, no host, no speed module,
     * or a buffer too empty to run one, which is the shape every effect below shares: a module adds
     * to what Forgeweave's own stats already say and never replaces or subtracts from it.
     */
    public static float digSpeedMultiplier(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 1.0F : installed.digSpeedMultiplier(stack);
    }

    /**
     * How many blocks per side an area module widens the tool's own mining box by ({@code AoeHarvest},
     * issue #956 phase 2): Draconic Evolution's {@code AOEData.aoe} is a radius, so {@code n} grows
     * both the width and the height by {@code 2n}. 0 without a module.
     */
    public static int miningAoe(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 0 : installed.miningAoe(stack);
    }

    /**
     * FE one block break costs while a mining module is installed -- Draconic Evolution's own
     * {@code EquipCfg.energyHarvest}, charged per block exactly as its own tools charge it. 0 when
     * no module would spend it, which is what keeps an ordinary tool's break free.
     */
    public static int miningEnergyCost(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 0 : installed.miningEnergyCost(stack);
    }

    /**
     * Flat attack damage a damage module adds on top of the tool's own ({@code ToolItem#attackDamage},
     * issue #956 phase 2, maintainer decision: a Draconic damage module <em>adds</em>, it never
     * replaces Forgeweave's number). 0 with no module or an empty buffer.
     */
    public static float attackDamageBonus(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 0.0F : installed.attackDamageBonus(stack);
    }

    /**
     * Runs an area module's sweep around {@code target} for a blow that just landed with
     * {@code damage} ({@code ToolItem#onLeftClickEntity}). Answers whether anything swept, which is
     * false with no Draconic Evolution, no host, no module or an empty buffer. Never cancels the blow
     * itself; the caller's own attack is untouched either way.
     */
    public static boolean meleeAoe(Player player, Entity target, ItemStack stack, float damage) {
        Bridge installed = bridge;
        return installed != null && installed.meleeAoe(player, target, stack, damage);
    }

    /**
     * What a projectile module does to one shot from this bow ({@code BowItem#shoot}).
     * {@link Projectile#NONE} with no module, and also with a buffer that cannot pay
     * {@link #shotEnergyCost} -- an unpowered module changes nothing rather than blocking the shot,
     * which is where Forgeweave parts company with Draconic Evolution's own bow (it refuses to fire).
     */
    public static Projectile projectile(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? Projectile.NONE : installed.projectile(stack);
    }

    /**
     * FE one boosted shot costs -- Draconic Evolution's own {@code ModularBow#calculateShotEnergy}.
     * 0 with no projectile module.
     */
    public static int shotEnergyCost(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 0 : installed.shotEnergyCost(stack);
    }

    private DraconicModules() {}
}
