package dev.gkissel.forgeweave.compat.draconic.modules;

import java.util.function.Supplier;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.data.AOEData;
import com.brandon3055.draconicevolution.api.modules.data.DamageData;
import com.brandon3055.draconicevolution.api.modules.data.ProjectileData;
import com.brandon3055.draconicevolution.api.modules.data.SpeedData;
import com.brandon3055.draconicevolution.api.modules.lib.ModularOPStorage;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleHostImpl;
import com.brandon3055.draconicevolution.init.EquipCfg;
import com.brandon3055.draconicevolution.items.equipment.DETier;
import com.brandon3055.draconicevolution.items.equipment.IModularMelee;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.trait.EnergyBuffer;

/**
 * The tool-active half of Forgeweave's module host compat (issue #956 phase 2): what a Draconic
 * Evolution module actually does once it is sitting in an evolved tool's grid. One method per hook
 * {@code ToolItem} and {@code BowItem} already own, reached only through
 * {@link DraconicModules}'s bridge, so neither of those classes names a {@code com.brandon3055} type
 * ({@code DraconicSourceIsolationTest}).
 *
 * <h2>The rule every effect here follows</h2>
 *
 * <p>A module <em>adds to</em> what Forgeweave's own stats already say. It never replaces them and
 * never subtracts from them. That is the maintainer's decision on attack damage stated as a rule for
 * all five effects, and it is where this parts company with Draconic Evolution's own equipment, whose
 * modules are the entire stat line:
 *
 * <ul>
 *   <li>Dig speed multiplies, and is clamped at 1. Draconic Evolution's own
 *       {@code IModularItem#handleTick} divides the multiplier by {@code 1 + aoe * 10} while an area
 *       module is installed, which would make a Forgeweave hammer eleven times slower for installing
 *       one; Forgeweave drops that term. Its large tools already mine 3x3 at full speed, and the area
 *       module's cost here is the per-block energy below, nine times over for a 3x3.
 *   <li>An empty buffer means a module simply does nothing that tick. Draconic Evolution instead
 *       zeroes the dig speed and refuses the bow shot outright; a Forgeweave tool out of power still
 *       works at exactly its own numbers.
 *   <li>Attack damage is added after {@code ToolItem#cutoffDamage}, not before. The cutoff curve
 *       exists to keep Forgeweave's <em>own</em> modifier stacking in bounds (issue #295); running a
 *       module's points through it would quietly eat most of them.
 * </ul>
 *
 * <h2>Energy</h2>
 *
 * <p>Every powered effect reads and spends {@code ForgeweaveDataComponents.ENERGY} through
 * {@link EnergyBuffer}, the one buffer a Forgeweave charger fills and a Draconic energy module
 * enlarges (phase 1, {@code ForgeweaveTraits#energyCapacity}). The amounts are Draconic Evolution's
 * own {@code EquipCfg} numbers. A tool with modules but no energy capacity at all -- no
 * {@code energized} trait and no energy module -- therefore has every powered effect idle; the energy
 * module is what turns them on, the same way Draconic Evolution's own gear carries base energy.
 *
 * <p>Melee area damage is the one effect that spends energy inside Draconic Evolution's own code
 * rather than here: it is run through {@link Melee}, an adapter that exists purely so
 * {@code IModularMelee#dealAOEDamage} -- 80 lines of arc, ally, knockback, fire-aspect, particle and
 * stat handling -- can be called as written instead of reimplemented. Its {@code extractEnergy}
 * default reaches the stack's Forge Energy capability through {@code EnergyUtils}, which is
 * {@link EnergyBuffer#capability}, so it lands in the same buffer.
 */
// ponytail: every method resolves the host afresh, so a dig-speed query on an evolved tool builds
// one per call rather than reading a cache. Draconic Evolution instead recomputes its own
// DESTROY_SPEED_DATA component once per tick in handleTick and reads that; if this shows up in a
// profile, mirror it from ToolItem#inventoryTick. A tool with no evolved trait costs one component
// read and stops there, which is every tool in an ordinary inventory.
public final class DraconicModuleEffects {

    /**
     * Draconic Evolution's speed curve, {@code MathHelper.map((s+1)^2, 1, 2, 1, 1.65)} out of
     * {@code IModularItem#handleTick}, written out: the multiplier climbs 0.65 for every whole step
     * the squared speed takes past 1.
     */
    private static final float SPEED_CURVE_SLOPE = 0.65F;

    /** {@code IModularMelee#onLeftClickEntity}: the sweep only runs on a near-full swing. */
    private static final float MELEE_SWING_THRESHOLD = 0.9F;

    /** {@code IModularMelee#onLeftClickEntity}'s {@code aoe * 1.5} radius in blocks. */
    private static final double MELEE_AOE_PER_LEVEL = 1.5;

    /** {@code ModularBow#calculateDamage}'s two constants, {@code 2f} base and its {@code 3f} scale. */
    private static final float BOW_BASE_DAMAGE = 2.0F;
    private static final float BOW_DAMAGE_SCALE = 3.0F;

    /**
     * {@code ToolItem#getDestroySpeed}'s multiplier. See the class javadoc for the two deviations
     * from {@code IModularItem#handleTick}: the clamp at 1 and the dropped area penalty.
     */
    public static float digSpeedMultiplier(ItemStack stack) {
        try (ModuleHostImpl host = DraconicModuleHost.hostFor(stack)) {
            return host == null ? 1.0F : digSpeedMultiplier(host, stack);
        }
    }

    /**
     * {@link #digSpeedMultiplier(ItemStack)} once the host is resolved. Package-private and taking the
     * host as a parameter so a unit test can drive it from a stub: building a real host needs Draconic
     * Evolution's registries, which only a running install has. Same split on every effect below.
     */
    static float digSpeedMultiplier(ModuleHost host, ItemStack stack) {
        if (EnergyBuffer.stored(stack) < EquipCfg.energyHarvest) {
            return 1.0F;
        }
        SpeedData speed = host.getModuleData(ModuleTypes.SPEED);
        float multiplier = speed == null ? 1.0F : curve((float) speed.speedMultiplier());
        // The player's own "mining_speed" slider in the module screen, squared as DE squares it.
        float setting = 1.0F;
        if (host.hasDecimal("mining_speed")) {
            setting = (float) host.getDecimal("mining_speed").getValue();
            setting *= setting;
        }
        return Math.max(1.0F, multiplier * setting);
    }

    /** {@code (speed + 1)^2} mapped from {@code [1, 2]} onto {@code [1, 1.65]}. */
    private static float curve(float speed) {
        float squared = (speed + 1.0F) * (speed + 1.0F);
        return 1.0F + (squared - 1.0F) * SPEED_CURVE_SLOPE;
    }

    /**
     * {@code AoeHarvest}'s widening. Draconic Evolution's {@code AOEData.aoe} is a radius -- its own
     * {@code IModularMiningTool#getMiningArea} builds the box {@code aoe} out in each direction
     * perpendicular to the face -- and the player's {@code mining_aoe} slider overrides it.
     */
    public static int miningAoe(ItemStack stack) {
        try (ModuleHostImpl host = DraconicModuleHost.hostFor(stack)) {
            return host == null ? 0 : miningAoe(host);
        }
    }

    /** See {@link #digSpeedMultiplier(ModuleHost, ItemStack)} for why the host is a parameter. */
    static int miningAoe(ModuleHost host) {
        if (host.hasInt("mining_aoe")) {
            return Math.max(0, host.getInt("mining_aoe").getValue());
        }
        AOEData aoe = host.getModuleData(ModuleTypes.AOE);
        return aoe == null ? 0 : Math.max(0, aoe.aoe());
    }

    /**
     * {@code EquipCfg.energyHarvest} per block, charged whenever a speed or area module is installed
     * -- the two that make a break do more than it otherwise would. 0 otherwise, which is what keeps
     * an evolved tool carrying only, say, an energy module from paying anything to mine.
     */
    public static int miningEnergyCost(ItemStack stack) {
        try (ModuleHostImpl host = DraconicModuleHost.hostFor(stack)) {
            return host == null ? 0 : miningEnergyCost(host);
        }
    }

    /** See {@link #digSpeedMultiplier(ModuleHost, ItemStack)} for why the host is a parameter. */
    static int miningEnergyCost(ModuleHost host) {
        boolean powered = host.getModuleData(ModuleTypes.SPEED) != null
                || host.getModuleData(ModuleTypes.AOE) != null;
        return powered ? Math.max(0, EquipCfg.energyHarvest) : 0;
    }

    /**
     * {@code IModularTieredItem#getAttackDamage}'s module half: the damage module's own points, and
     * its energy gate ({@code energyAttack} per point). The tier bonus and damage multiplier that
     * method adds on top are Draconic Evolution's own weapon stats, which a Forgeweave tool does not
     * have and does not want -- it has its own.
     */
    public static float attackDamageBonus(ItemStack stack) {
        try (ModuleHostImpl host = DraconicModuleHost.hostFor(stack)) {
            return host == null ? 0.0F : attackDamageBonus(host, stack);
        }
    }

    /** See {@link #digSpeedMultiplier(ModuleHost, ItemStack)} for why the host is a parameter. */
    static float attackDamageBonus(ModuleHost host, ItemStack stack) {
        DamageData damage = host.getModuleData(ModuleTypes.DAMAGE);
        if (damage == null || damage.damagePoints() <= 0.0) {
            return 0.0F;
        }
        return EnergyBuffer.stored(stack) < EquipCfg.energyAttack * damage.damagePoints()
                ? 0.0F
                : (float) damage.damagePoints();
    }

    /**
     * {@code IModularMelee#onLeftClickEntity}'s own arithmetic -- radius, swing-strength gate, damage
     * scaling and per-entity energy -- handing off to {@code dealAOEDamage} through {@link Melee}.
     * {@code damage} is the blow Forgeweave itself just dealt ({@code ToolItem#attackDamage}, which
     * already includes {@link #attackDamageBonus}), standing in for the weapon damage Draconic
     * Evolution reads off its own tier.
     */
    public static boolean meleeAoe(Player player, Entity target, ItemStack stack, float damage) {
        double radius;
        try (ModuleHostImpl host = DraconicModuleHost.hostFor(stack)) {
            if (host == null || damage <= 0.0F) {
                return false;
            }
            radius = meleeAoeRadius(host);
        }
        if (radius <= 0.0) {
            return false;
        }
        float swing = player.getAttackStrengthScale(0.5F);
        if (swing <= MELEE_SWING_THRESHOLD) {
            return false;
        }
        long energyPerHit = (long) (EquipCfg.energyAttack * damage);
        float sweepDamage = damage * (0.2F + swing * swing * 0.8F);
        Melee.INSTANCE.dealAOEDamage(player, target, stack, energyPerHit, sweepDamage, radius);
        return true;
    }

    /**
     * The radius an area module sweeps around a melee target: {@code aoe * 1.5} blocks, or the
     * player's own {@code attack_aoe} slider where the module screen offers one.
     */
    static double meleeAoeRadius(ModuleHost host) {
        if (host.hasDecimal("attack_aoe")) {
            return host.getDecimal("attack_aoe").getValue();
        }
        AOEData aoe = host.getModuleData(ModuleTypes.AOE);
        return aoe == null ? 0.0 : aoe.aoe() * MELEE_AOE_PER_LEVEL;
    }

    /** {@code ModularBow#releaseUsing}'s three projectile numbers, or none installed. */
    public static DraconicModules.Projectile projectile(ItemStack stack) {
        try (ModuleHostImpl host = DraconicModuleHost.hostFor(stack)) {
            return host == null ? DraconicModules.Projectile.NONE : projectile(host, stack);
        }
    }

    /** See {@link #digSpeedMultiplier(ModuleHost, ItemStack)} for why the host is a parameter. */
    static DraconicModules.Projectile projectile(ModuleHost host, ItemStack stack) {
        ProjectileData data = host.getModuleData(ModuleTypes.PROJ_MODIFIER);
        if (data == null) {
            return DraconicModules.Projectile.NONE;
        }
        DraconicModules.Projectile projectile =
                new DraconicModules.Projectile(data.velocity(), data.accuracy(), data.damage());
        if (!projectile.any()) {
            return DraconicModules.Projectile.NONE;
        }
        return EnergyBuffer.stored(stack) < shotEnergy(data)
                ? DraconicModules.Projectile.NONE
                : projectile;
    }

    /** {@code ModularBow#calculateShotEnergy}, 0 with no projectile module. */
    public static int shotEnergyCost(ItemStack stack) {
        try (ModuleHostImpl host = DraconicModuleHost.hostFor(stack)) {
            return host == null ? 0 : shotEnergyCost(host);
        }
    }

    /** See {@link #digSpeedMultiplier(ModuleHost, ItemStack)} for why the host is a parameter. */
    static int shotEnergyCost(ModuleHost host) {
        ProjectileData data = host.getModuleData(ModuleTypes.PROJ_MODIFIER);
        return data == null ? 0 : shotEnergy(data);
    }

    /**
     * {@code ModularBow#calculateShotEnergy}: {@code bowBaseEnergy * calculateDamage}, where the
     * damage is {@code 2 * (1 + damage) * 3 * (1 + velocity)}. Clamped to {@code int} because
     * {@link EnergyBuffer} counts in FE ints, not Draconic Evolution's longs.
     */
    private static int shotEnergy(ProjectileData data) {
        float damage = BOW_BASE_DAMAGE * (1.0F + data.damage()) * BOW_DAMAGE_SCALE * (1.0F + data.velocity());
        return (int) Math.min((long) (damage * EquipCfg.bowBaseEnergy), Integer.MAX_VALUE);
    }

    /**
     * Exists so {@code IModularMelee#dealAOEDamage} can be called on a Forgeweave stack. That method
     * is a default one, so it needs an instance of the interface; it reads nothing off that instance
     * beyond the two energy defaults, which go through the {@link ItemStack} it is handed rather than
     * through any state here. The six methods below are the interface's abstract ones, present only
     * to make the class concrete -- {@code dealAOEDamage} calls none of them, which is why the two
     * Draconic-shaped ones can answer with what a Forgeweave tool has, i.e. nothing.
     */
    private static final class Melee implements IModularMelee {

        static final Melee INSTANCE = new Melee();

        @Override
        public TechLevel getTechLevel() {
            return TechLevel.WYVERN;
        }

        @Override
        public ModuleHostImpl instantiateHost(ItemStack stack) {
            return DraconicModuleHost.newHost(stack);
        }

        @Override
        public ModularOPStorage instantiateOPStorage(ItemStack stack, Supplier<ModuleHost> host) {
            return null;
        }

        @Override
        public DETier getItemTier() {
            return null;
        }

        @Override
        public double getSwingSpeedMultiplier() {
            return 1.0;
        }

        @Override
        public double getDamageMultiplier() {
            return 1.0;
        }

        /**
         * NeoForge's {@code IItemExtension} is meant to be mixed into {@code Item} and leaves this one
         * method abstract for it. Nothing on the sweep's path asks, and this adapter is not an item.
         */
        @Override
        public boolean isRepairable(ItemStack stack) {
            return false;
        }
    }

    private DraconicModuleEffects() {}
}
