package dev.gkissel.forgeweave.compat.mekanism.modules;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * The Mekanism-free half of Forgeweave's module container compat (issue #993): the wiring table every
 * reader walks, and the seam the rest of the mod calls into without naming a {@code mekanism} type.
 *
 * <p>Same split {@code DraconicModules} uses, for the same reason. This class is classloaded on every
 * install, Mekanism or not, so it names nothing from that mod; {@link MekanismModuleContainer} does,
 * and installs itself here through {@link #install} from inside the {@code ModList} guard in
 * {@code Forgeweave}'s constructor. With no Mekanism present nothing ever installs a bridge and every
 * query below answers neutrally.
 *
 * <h2>What the config toggle can and cannot do</h2>
 *
 * <p>{@code ForgeweaveConfig.MEKANISM_MODULES} is read by the bridge, not here, and it makes the
 * container <em>inert</em> rather than absent: every effect below answers its neutral value, the
 * radiation shielding capability answers zero, and the module screen refuses to open because the
 * capability provider answers null. What it cannot do is unregister anything. A {@code SERVER} spec is
 * not loaded when registries freeze or when inter-mod messages are enqueued, so the module container
 * component and the IMC roster are already committed by the time the toggle could be read. Nothing
 * touches a stack's stored modules either way, which is D-M7-3's inert-not-destructive contract
 * applied to compat: flipping the toggle back on restores an installed module with no reload.
 */
public final class MekanismGearModules {

    /** Where one of Mekanism's modules lands in Forgeweave, for {@link #MODULE_WIRING}. */
    public enum Wiring {
        /** Phase 1 runs this module's effect through a Forgeweave hook. */
        WIRED,
        /** Phase 2 (M8-10, issue #994) opens the interaction site this module needs. */
        DEFERRED,
        /** Deliberately not wired at all, for the reason on the row. */
        UNWIRED
    }

    /**
     * One row of the module-to-hook table issue #993 asks for.
     *
     * @param id the module's registry path, which is {@code MekanismModules}' own field name lowercased
     * @param wiring where it lands
     * @param note why, in one clause -- the hook for a wired row, the reason for the other two
     */
    public record ModuleWiring(String id, Wiring wiring, String note) {}

    /**
     * Every module Mekanism 10.7.19 registers, and where each one lands. Total by construction:
     * {@code MekanismModuleWiringTest} walks {@code mekanism.common.registries.MekanismModules}' own
     * fields and fails on an id this table does not name, so a Mekanism update that adds a module
     * cannot slip through as a silently dropped effect.
     *
     * <p>The classification rule, applied once so no row is a judgement call:
     *
     * <ul>
     *   <li>{@code WIRED} is exactly the phase 1 table in issue #993, plus the energy unit, which is
     *       the buffer every other powered effect spends.
     *   <li>{@code DEFERRED} is exactly the roster issue #994 names: teleportation, farming, shearing,
     *       the jetpack, gravitational modulation and the elytra. Each needs a Forgeweave-side
     *       interaction site phase 1 does not open.
     *   <li>{@code UNWIRED} is everything else, and each row says which Mekanism subsystem it would
     *       drag in or which Forgeweave system it would replace. That is the same call issue #956 made
     *       for Draconic Evolution's arrow penetration and anti-gravity.
     * </ul>
     */
    public static final List<ModuleWiring> MODULE_WIRING = List.of(
            // Shared between the MekaTool and the MekaSuit.
            new ModuleWiring("energy_unit", Wiring.WIRED,
                    "adds to ForgeweaveTraits.energyCapacity, so one EnergyBuffer serves the tool and its modules"),
            new ModuleWiring("color_modulation_unit", Wiring.UNWIRED,
                    "tints Mekanism's own armour model; Forgeweave armour renders from its own material layers"),
            new ModuleWiring("laser_dissipation_unit", Wiring.UNWIRED,
                    "reads Mekanism's laser damage source through its own ILaserDissipation capability, "
                            + "which would replace Forgeweave's defence pass rather than add to it"),
            new ModuleWiring("radiation_shielding_unit", Wiring.WIRED,
                    "the IRadiationShielding item capability"),

            // MekaTool.
            new ModuleWiring("excavation_escalation_unit", Wiring.WIRED, "ToolItem.getDestroySpeed"),
            new ModuleWiring("attack_amplification_unit", Wiring.DEFERRED,
                    "the attack path is a Forgeweave-side site phase 1's table does not open"),
            new ModuleWiring("farming_unit", Wiring.DEFERRED, "useOn, issue #994"),
            new ModuleWiring("shearing_unit", Wiring.DEFERRED, "useOn, issue #994"),
            new ModuleWiring("silk_touch_unit", Wiring.WIRED, "ToolItem.getAllEnchantments"),
            new ModuleWiring("fortune_unit", Wiring.WIRED, "ToolItem.getAllEnchantments"),
            new ModuleWiring("blasting_unit", Wiring.WIRED, "AoeHarvest's own box, not a second block breaker"),
            new ModuleWiring("vein_mining_unit", Wiring.WIRED,
                    "ModuleVeinMiningUnit.findPositions, broken through AoeHarvest.breakEach"),
            new ModuleWiring("teleportation_unit", Wiring.DEFERRED, "use, issue #994"),

            // MekaSuit.
            new ModuleWiring("electrolytic_breathing_unit", Wiring.UNWIRED,
                    "fills a Mekanism chemical tank; no Forgeweave item holds chemicals and gas forms "
                            + "are an M8 non-goal (D-M8-6)"),
            new ModuleWiring("inhalation_purification_unit", Wiring.UNWIRED,
                    "same chemical tank, plus Mekanism's own status effect roster"),
            new ModuleWiring("nutritional_injection_unit", Wiring.UNWIRED,
                    "feeds from a Mekanism chemical tank of nutritional paste"),
            new ModuleWiring("vision_enhancement_unit", Wiring.UNWIRED,
                    "drives Mekanism's own client render and HUD"),
            new ModuleWiring("dosimeter_unit", Wiring.UNWIRED, "a Mekanism HUD readout"),
            new ModuleWiring("geiger_unit", Wiring.UNWIRED, "a Mekanism HUD readout"),
            new ModuleWiring("jetpack_unit", Wiring.DEFERRED, "flight, issue #994"),
            new ModuleWiring("charge_distribution_unit", Wiring.UNWIRED,
                    "moves energy between Mekanism's own gear slots; Forgeweave's buffer is per item"),
            new ModuleWiring("gravitational_modulating_unit", Wiring.DEFERRED, "flight, issue #994"),
            new ModuleWiring("elytra_unit", Wiring.DEFERRED, "flight, issue #994"),
            new ModuleWiring("locomotive_boosting_unit", Wiring.UNWIRED,
                    "rewrites the player's own sprint speed and attack cooldown, which M4's armour "
                            + "attributes already own"),
            new ModuleWiring("gyroscopic_stabilization_unit", Wiring.UNWIRED,
                    "cancels vanilla's own movement penalties from Mekanism's player tick"),
            new ModuleWiring("hydrostatic_repulsor_unit", Wiring.UNWIRED,
                    "same player tick, for swim speed"),
            new ModuleWiring("motorized_servo_unit", Wiring.UNWIRED, "same player tick, for step assist"),
            new ModuleWiring("hydraulic_propulsion_unit", Wiring.UNWIRED, "same player tick, for jump height"),
            new ModuleWiring("magnetic_attraction_unit", Wiring.UNWIRED,
                    "pulls item entities on Mekanism's player tick"),
            new ModuleWiring("frost_walker_unit", Wiring.WIRED,
                    "ToolItem.getAllEnchantments, the same enchantment view silk touch and fortune use"),
            new ModuleWiring("soul_surfer_unit", Wiring.UNWIRED,
                    "cancels soul-sand slowdown from Mekanism's player tick"));

    /** {@link #MODULE_WIRING} by id, so a lookup is not a scan. */
    public static final Map<String, ModuleWiring> WIRING_BY_ID =
            MODULE_WIRING.stream().collect(Collectors.toUnmodifiableMap(ModuleWiring::id, Function.identity()));

    /** What {@link MekanismModuleContainer} answers once Mekanism is installed and the toggle is on. */
    public interface Bridge {

        /** How many modules sit on {@code stack} right now. */
        int installedModules(ItemStack stack);

        /** The FE capacity the stack's installed energy modules add, clamped to {@code int}. */
        int moduleEnergyCapacity(ItemStack stack);

        // Every effect defaults to doing nothing, so a test's fake bridge names only the effect it is
        // about, and a bridge written before an effect existed keeps compiling.

        /** See {@link MekanismGearModules#digSpeedMultiplier}. */
        default float digSpeedMultiplier(ItemStack stack) {
            return 1.0F;
        }

        /** See {@link MekanismGearModules#miningAoe}. */
        default int miningAoe(ItemStack stack) {
            return 0;
        }

        /** See {@link MekanismGearModules#miningEnergyCost}. */
        default int miningEnergyCost(ItemStack stack) {
            return 0;
        }

        /** See {@link MekanismGearModules#moduleEnchantments}. */
        default ItemEnchantments moduleEnchantments(ItemStack stack) {
            return ItemEnchantments.EMPTY;
        }

        /** See {@link MekanismGearModules#veinPositions}. */
        default List<BlockPos> veinPositions(ItemStack stack, Level level, BlockPos origin) {
            return List.of();
        }

        /** See {@link MekanismGearModules#damageAbsorbed}. */
        default Absorption damageAbsorbed(ItemStack piece, LivingEntity defender, float damage) {
            return Absorption.NONE;
        }

        /** See {@link MekanismGearModules#radiationShielding}. */
        default double radiationShielding(ItemStack stack) {
            return 0.0D;
        }

        /** See {@link MekanismGearModules#tickModules}. */
        default void tickModules(ItemStack stack, Player player, boolean serverSide) {}
    }

    /**
     * What a MekaSuit's absorption modules take off one blow, and what that costs (issue #993's
     * {@code getDamageAbsorbed} row).
     *
     * @param ratio the fraction of the blow absorbed, 0 to 1
     * @param energyCost the FE the absorption spends out of the piece's own buffer
     */
    public record Absorption(float ratio, int energyCost) {

        /** No absorption module, no Mekanism, no power, or the toggle off. */
        public static final Absorption NONE = new Absorption(0.0F, 0);

        /** Whether this is worth applying at all. */
        public boolean any() {
            return ratio > 0.0F;
        }
    }

    /** {@link #energyPerBlock()}'s own default: what one block break costs a running mining module. */
    public static final int ENERGY_PER_BLOCK_DEFAULT = 400;

    /** {@link #energyPerAbsorbedPoint()}'s own default: FE per half-heart the suit's modules absorb. */
    public static final int ENERGY_PER_ABSORBED_POINT_DEFAULT = 1000;

    /** {@link #veinMiningMaxBlocks()}'s own default, a cap on top of Mekanism's own traversal limit. */
    public static final int VEIN_MINING_MAX_BLOCKS_DEFAULT = 64;

    /**
     * Whether the bridge does anything -- {@code ForgeweaveConfig.MEKANISM_MODULES}, D-M8-5's family
     * toggle. Off makes the container inert, never absent; see the class javadoc for what that can and
     * cannot reach.
     */
    public static boolean modulesEnabled() {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.MEKANISM_MODULES);
    }

    /** FE one block break costs while a powered Mekanism mining module is installed. */
    public static int energyPerBlock() {
        return ForgeweaveConfig.mekanismEnergyPerBlock();
    }

    /** FE the MekaSuit absorption modules spend per point of damage they take off a blow. */
    public static int energyPerAbsorbedPoint() {
        return ForgeweaveConfig.mekanismEnergyPerAbsorbedPoint();
    }

    /** How many blocks one vein mining swing breaks at most, on top of Mekanism's own traversal limit. */
    public static int veinMiningMaxBlocks() {
        return ForgeweaveConfig.mekanismVeinMiningMaxBlocks();
    }

    @Nullable
    private static volatile Bridge bridge;

    /**
     * Called once, from inside the {@code ModList} guard. {@code null} puts the state back where an
     * install without Mekanism has it, which is what a test that installed a fake bridge restores.
     */
    public static void install(@Nullable Bridge installed) {
        bridge = installed;
    }

    /** How many modules a stack carries; 0 with no Mekanism, no container, or the toggle off. */
    public static int installedModules(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 0 : installed.installedModules(stack);
    }

    /**
     * The FE a stack's Mekanism energy modules add to {@code ForgeweaveTraits#energyCapacity}, so one
     * buffer serves the metal's own {@code infused} trait and an installed energy unit alike (D-M8-15).
     * 0 without Mekanism and 0 for a stack carrying no {@code atomic_matter_alloy} part.
     */
    public static int moduleEnergyCapacity(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 0 : installed.moduleEnergyCapacity(stack);
    }

    /**
     * What an excavation escalation unit multiplies {@code ToolItem#getDestroySpeed}'s own answer by.
     * {@code 1} -- change nothing -- with no Mekanism, no container, no module, or a buffer too empty
     * to run one, which is the shape every effect here shares: a module adds to what Forgeweave's own
     * stats already say and never replaces or subtracts from it.
     */
    public static float digSpeedMultiplier(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 1.0F : installed.digSpeedMultiplier(stack);
    }

    /**
     * How many blocks per side a blasting unit widens the tool's own mining box by ({@code AoeHarvest}).
     * Mekanism's own blast radius is a radius, so {@code n} grows both the width and the height by
     * {@code 2n}, exactly as a Draconic area module's does. 0 without a module.
     */
    public static int miningAoe(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 0 : installed.miningAoe(stack);
    }

    /**
     * FE one block break costs while a powered mining module is installed, charged per block the way
     * Mekanism's own tool charges it. 0 when no module would spend it, which is what keeps an ordinary
     * tool's break free.
     */
    public static int miningEnergyCost(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 0 : installed.miningEnergyCost(stack);
    }

    /**
     * The enchantments a stack's modules grant on top of the ones actually written on it -- silk touch,
     * fortune and frost walker today, and anything else Mekanism ships as an
     * {@code EnchantmentAwareModule}. {@link ItemEnchantments#EMPTY} without Mekanism, so a
     * Forgeweave-only install sees exactly the component on the stack.
     */
    public static ItemEnchantments moduleEnchantments(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? ItemEnchantments.EMPTY : installed.moduleEnchantments(stack);
    }

    /**
     * The extra blocks a vein mining unit wants broken around {@code origin}, from Mekanism's own
     * {@code ModuleVeinMiningUnit#findPositions}. Empty without a module, without the power to run it,
     * or when the block at {@code origin} is not one Mekanism will vein.
     */
    public static List<BlockPos> veinPositions(ItemStack stack, Level level, BlockPos origin) {
        Bridge installed = bridge;
        return installed == null ? List.of() : installed.veinPositions(stack, level, origin);
    }

    /**
     * What the worn piece's absorption modules take off one incoming blow, and what it costs
     * ({@code CombatSeam#onDefend}). {@link Absorption#NONE} with no module, no power, or no Mekanism,
     * in which case the blow lands at exactly the number Forgeweave's own armour worked out.
     */
    public static Absorption damageAbsorbed(ItemStack piece, LivingEntity defender, float damage) {
        Bridge installed = bridge;
        return installed == null ? Absorption.NONE : installed.damageAbsorbed(piece, defender, damage);
    }

    /**
     * The fraction of radiation the stack blocks, 0 to 1 -- what Mekanism's {@code IRadiationShielding}
     * capability answers. 1 for a piece plated in {@code atomic_matter_alloy} per D-M8-15, 0 otherwise.
     */
    public static double radiationShielding(ItemStack stack) {
        Bridge installed = bridge;
        return installed == null ? 0.0D : installed.radiationShielding(stack);
    }

    /**
     * Runs the free {@code ICustomModule} tick hooks on a carried stack -- the ones that need no
     * Forgeweave-side interaction site, only the inventory tick the tool already has. Does nothing
     * without Mekanism.
     */
    public static void tickModules(ItemStack stack, Player player, boolean serverSide) {
        Bridge installed = bridge;
        if (installed != null) {
            installed.tickModules(stack, player, serverSide);
        }
    }

    private MekanismGearModules() {}
}
