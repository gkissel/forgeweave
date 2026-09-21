package dev.gkissel.forgeweave.compat.mekanism.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;

import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleContainer;
import mekanism.common.content.gear.mekatool.ModuleAttackAmplificationUnit;
import mekanism.common.content.gear.mekatool.ModuleBlastingUnit;
import mekanism.common.content.gear.mekatool.ModuleExcavationEscalationUnit;
import mekanism.common.content.gear.mekatool.ModuleTeleportationUnit;
import mekanism.common.content.gear.mekatool.ModuleVeinMiningUnit;
import mekanism.common.registries.MekanismModules;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.gkissel.forgeweave.compat.mekanism.ForgeweaveMekanismCompat;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.trait.EnergyBuffer;

/**
 * The arithmetic behind {@link MekanismGearModules}' effects (issue #993 phase 1), replicated in
 * Forgeweave's own hooks rather than by running Mekanism's own tool and suit. Same call issue #956
 * made for Draconic Evolution, and the same three deviations it recorded hold here:
 *
 * <ul>
 *   <li>A module only ever <em>adds</em>. The excavation multiplier is clamped at 1 rather than
 *       replacing the tool's own dig speed the way Mekanism's own tool does, because a Forgeweave
 *       hammer already mines 3x3 at full speed off its own stats.
 *   <li>An empty buffer means a module does nothing, rather than zeroing the dig speed or refusing the
 *       swing.
 *   <li>The tool's own curve stays the tool's own curve: vein mining and blasting feed
 *       {@code AoeHarvest} rather than running a second block breaker beside it.
 * </ul>
 *
 * <p>The only class besides {@link MekanismModuleContainer} that names a {@code mekanism} type. Every
 * public method here answers neutrally for a stack that is not a module container, so the callers on
 * the Forgeweave side never have to ask twice.
 *
 * <h2>Verified Mekanism coordinates (Mekanism 1.21.1-10.7.19.85, read off the published jar)</h2>
 *
 * <ul>
 *   <li>{@code IModuleContainer#moduleBasedEnchantments()} returns an {@code ItemEnchantments} built
 *       from every installed {@code EnchantmentAwareModule}, which is what silk touch, fortune and
 *       frost walker are.
 *   <li>{@code IModuleContainer#getIfEnabled(IModuleDataProvider)} answers {@code null} for a module
 *       that is absent or switched off in the module screen, so the player's own on/off is respected
 *       without Forgeweave reading module config.
 *   <li>{@code ModuleExcavationEscalationUnit#getEfficiency()} is the dig speed its mode asks for;
 *       {@code ModuleBlastingUnit#getBlastRadius()} is a radius; {@code ModuleVeinMiningUnit} carries
 *       {@code extended()}, {@code getExcavationRange()} and the static
 *       {@code findPositions(Level, Map, int, Reference2BooleanMap)} / {@code canVeinBlock(BlockState)}.
 *   <li>{@code ICustomModule#getDamageAbsorbInfo(IModule, DamageSource)} returns a
 *       {@code ModuleDamageAbsorbInfo(FloatSupplier absorptionRatio, LongSupplier energyCost)}; the
 *       ratios of every installed module multiply together, which is Mekanism's own
 *       {@code getDamageAbsorbed} orchestration.
 *   <li>{@code ICustomModule#tickServer} / {@code tickClient} take
 *       {@code (IModule, IModuleContainer, ItemStack, Player)} and are the free hooks: they need no
 *       interaction site, only the inventory tick the tool already has.
 * </ul>
 */
public final class MekanismModuleEffects {

    // ponytail: every method resolves the container afresh per call, as Mekanism's own item code does.
    // The isContainerStack read inside MekanismModuleContainer#containerFor short-circuits every stack
    // without an atomic_matter_alloy part on one data component lookup, which is every stack in a
    // normal inventory. Cache per stack if a profile ever says otherwise.

    /**
     * What an excavation escalation unit multiplies the tool's own dig speed by. Mekanism's own tool
     * <em>replaces</em> its speed with the module's efficiency; dividing by vanilla's diamond-tier
     * speed turns that number into the multiplier Forgeweave needs, and the clamp at 1 is the first
     * deviation in the class javadoc.
     */
    public static float digSpeedMultiplier(ItemStack stack) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        if (container == null || EnergyBuffer.stored(stack) < MekanismGearModules.energyPerBlock()) {
            return 1.0F;
        }
        IModule<ModuleExcavationEscalationUnit> module =
                container.getIfEnabled(MekanismModules.EXCAVATION_ESCALATION_UNIT);
        if (module == null) {
            return 1.0F;
        }
        float efficiency = module.getCustomInstance().getEfficiency();
        return Math.max(1.0F, efficiency / EXCAVATION_REFERENCE_SPEED);
    }

    /**
     * Vanilla's diamond dig speed, the denominator that turns Mekanism's absolute efficiency into a
     * multiplier. Mekanism's own excavation modes run 4 to 128 against this 8, so the slow mode is a
     * no-op under the clamp and the fastest is a 16x tool.
     */
    private static final float EXCAVATION_REFERENCE_SPEED = 8.0F;

    /**
     * How far a blasting unit widens {@code AoeHarvest}'s box, per side. Mapped onto Forgeweave's own
     * sweep rather than running {@code IBlastingItem#findPositions} as a second block breaker, which
     * is the third deviation in the class javadoc.
     */
    public static int miningAoe(ItemStack stack) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        if (container == null || EnergyBuffer.stored(stack) < MekanismGearModules.energyPerBlock()) {
            return 0;
        }
        IModule<ModuleBlastingUnit> module = container.getIfEnabled(MekanismModules.BLASTING_UNIT);
        return module == null ? 0 : module.getCustomInstance().getBlastRadius();
    }

    /** FE one block break costs while any powered mining module is installed; 0 when none is. */
    public static int miningEnergyCost(ItemStack stack) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        if (container == null) {
            return 0;
        }
        boolean powered = container.getIfEnabled(MekanismModules.EXCAVATION_ESCALATION_UNIT) != null
                || container.getIfEnabled(MekanismModules.BLASTING_UNIT) != null
                || container.getIfEnabled(MekanismModules.VEIN_MINING_UNIT) != null;
        return powered ? MekanismGearModules.energyPerBlock() : 0;
    }

    /**
     * The enchantments the installed modules grant -- silk touch, fortune and frost walker today.
     * Delegated straight to the container, so a module the player has switched off grants nothing and
     * Forgeweave never has to know which modules are enchantment-shaped.
     */
    public static ItemEnchantments moduleEnchantments(ItemStack stack) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        return container == null ? ItemEnchantments.EMPTY : container.moduleBasedEnchantments();
    }

    /**
     * The extra blocks a vein mining unit wants broken around {@code origin}, from Mekanism's own
     * {@code findPositions}. The origin itself is dropped -- the caller is already breaking it -- and
     * the list is capped by {@link MekanismGearModules#veinMiningMaxBlocks()} on top of Mekanism's own
     * traversal limit, so a vein in a modpack with a generous Mekanism config cannot stall a tick.
     */
    public static List<BlockPos> veinPositions(ItemStack stack, Level level, BlockPos origin) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        if (container == null || EnergyBuffer.stored(stack) < MekanismGearModules.energyPerBlock()) {
            return List.of();
        }
        IModule<ModuleVeinMiningUnit> module = container.getIfEnabled(MekanismModules.VEIN_MINING_UNIT);
        if (module == null) {
            return List.of();
        }
        BlockState state = level.getBlockState(origin);
        ModuleVeinMiningUnit vein = module.getCustomInstance();
        boolean isOre = ModuleVeinMiningUnit.canVeinBlock(state);
        if (!isOre && !vein.extended()) {
            // Mekanism's own tool only veins ore unless extended mode is on.
            return List.of();
        }
        Reference2BooleanMap<Block> oreTracker = new Reference2BooleanOpenHashMap<>();
        oreTracker.put(state.getBlock(), isOre);
        int extendedRange = vein.extended() ? vein.getExcavationRange() : 0;
        Object2IntMap<BlockPos> found =
                ModuleVeinMiningUnit.findPositions(level, Map.of(origin, state), extendedRange, oreTracker);
        int limit = MekanismGearModules.veinMiningMaxBlocks();
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : found.keySet()) {
            if (pos.equals(origin)) {
                continue;
            }
            if (positions.size() >= limit) {
                break;
            }
            positions.add(pos.immutable());
        }
        return positions;
    }

    /**
     * Mekanism's {@code getDamageAbsorbed} orchestration: every installed module that answers a
     * {@code ModuleDamageAbsorbInfo} for this damage source contributes its ratio, the ratios multiply,
     * and the cost is the absorbed points times the configured rate. {@code Absorption.NONE} when no
     * module absorbs anything.
     */
    public static MekanismGearModules.Absorption damageAbsorbed(ItemStack piece, LivingEntity defender,
            float damage) {
        IModuleContainer container = MekanismModuleContainer.containerFor(piece);
        if (container == null || damage <= 0.0F) {
            return MekanismGearModules.Absorption.NONE;
        }
        float remaining = 1.0F;
        for (IModule<?> module : container.modules()) {
            if (!module.isEnabled()) {
                continue;
            }
            float ratio = absorptionRatio(module, defender);
            if (ratio > 0.0F) {
                remaining *= 1.0F - Math.min(1.0F, ratio);
            }
        }
        float absorbed = 1.0F - remaining;
        if (absorbed <= 0.0F) {
            return MekanismGearModules.Absorption.NONE;
        }
        int cost = (int) Math.ceil(absorbed * damage * MekanismGearModules.energyPerAbsorbedPoint());
        return new MekanismGearModules.Absorption(absorbed, cost);
    }

    /**
     * One module's absorption ratio for the blow the defender is taking. Generic over the module's own
     * custom type, which is what {@code ICustomModule#getDamageAbsorbInfo} is declared on.
     */
    private static <MODULE extends ICustomModule<MODULE>> float absorptionRatio(IModule<MODULE> module,
            LivingEntity defender) {
        ICustomModule.ModuleDamageAbsorbInfo info = module.getCustomInstance()
                .getDamageAbsorbInfo(module, defender.damageSources().generic());
        return info == null ? 0.0F : info.absorptionRatio().getAsFloat();
    }

    /**
     * 100% radiation shielding for a stack plated in {@code atomic_matter_alloy} (D-M8-15), 0 for
     * anything else. Deliberately a property of the metal rather than of an installed module: D-M8-15
     * puts the shielding on the metal, and phase 2's partial modifier is what covers ordinary armour.
     */
    public static double radiationShielding(ItemStack stack) {
        boolean plated = MekanismGearModules.modulesEnabled()
                && ForgeweaveMekanismCompat.isContainerStack(stack);
        if (plated) {
            return 1.0D;
        }
        // #994: the rayward modifier's own contribution, the answer for armour a player can build
        // long before the metal. Outside the mekanismModules gate deliberately: that toggle covers
        // the module container and the ore chains, and rayward is a Forgeweave modifier that rides
        // the modifiers content toggle like every other one. See ForgeweaveModifiers#RAYWARD.
        return ForgeweaveModifiers.radiationShielding(stack);
    }

    /**
     * The free {@code ICustomModule} tick hooks, dispatched from the inventory tick the tool already
     * has. Nothing else on {@code ICustomModule} fires without an interaction site issue #993's phase 1
     * does not open -- see {@link MekanismGearModules#MODULE_WIRING} for where each of those lands.
     */
    public static void tickModules(ItemStack stack, Player player, boolean serverSide) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        if (container == null) {
            return;
        }
        for (IModule<?> module : container.modules()) {
            if (module.isEnabled()) {
                tickOne(module, container, stack, player, serverSide);
            }
        }
    }

    private static <MODULE extends ICustomModule<MODULE>> void tickOne(IModule<MODULE> module,
            IModuleContainer container, ItemStack stack, Player player, boolean serverSide) {
        if (serverSide) {
            module.getCustomInstance().tickServer(module, container, stack, player);
        } else {
            module.getCustomInstance().tickClient(module, container, stack, player);
        }
    }

    /**
     * The FE an installed energy unit adds to {@code ForgeweaveTraits#energyCapacity}, so one buffer
     * serves the metal's own {@code energized4} trait and the module alike. Clamped to {@code int} because
     * Mekanism counts energy in {@code long} and Forgeweave's buffer does not.
     */
    public static int moduleEnergyCapacity(ItemStack stack) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        if (container == null) {
            return 0;
        }
        IModule<?> module = container.getIfEnabled(MekanismModules.ENERGY_UNIT);
        if (module == null) {
            return 0;
        }
        long capacity = module.getEnergyContainer(stack) == null
                ? 0L
                : module.getEnergyContainer(stack).getMaxEnergy();
        return (int) Math.min(capacity, Integer.MAX_VALUE);
    }

    // ------------------------------------------------------------------ phase 2 (issue #994, M8-10)

    /**
     * Mekanism's own {@code ICustomModule#onItemUse}, run from Forgeweave's {@code ToolItem#useOn}:
     * the farming unit's till, flatten and strip, and the shearing unit's beehive and pumpkin. The
     * same delegation shape phase 1 gave {@link #tickModules} -- the interaction site is Forgeweave's,
     * the behaviour behind it is the module's own, so a Mekanism update that retunes a farming radius
     * lands here with no change.
     *
     * <p>Stops at the first module that consumes the click, the way Mekanism's own tool does.
     */
    public static InteractionResult useOnBlock(ItemStack stack, UseOnContext context) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        if (container == null) {
            return InteractionResult.PASS;
        }
        for (IModule<?> module : container.modules()) {
            if (!module.isEnabled()) {
                continue;
            }
            InteractionResult result = onItemUse(module, context);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return InteractionResult.PASS;
    }

    private static <MODULE extends ICustomModule<MODULE>> InteractionResult onItemUse(IModule<MODULE> module,
            UseOnContext context) {
        return module.getCustomInstance().onItemUse(module, context);
    }

    /**
     * Mekanism's own {@code ICustomModule#onInteract}, run from
     * {@code ToolItem#interactLivingEntity}: the shearing unit's shear. Same delegation and same
     * first-one-wins order as {@link #useOnBlock}.
     */
    public static InteractionResult interactEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        if (container == null) {
            return InteractionResult.PASS;
        }
        for (IModule<?> module : container.modules()) {
            if (!module.isEnabled()) {
                continue;
            }
            InteractionResult result = onInteract(module, player, target, hand, container, stack);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return InteractionResult.PASS;
    }

    private static <MODULE extends ICustomModule<MODULE>> InteractionResult onInteract(IModule<MODULE> module,
            Player player, LivingEntity target, InteractionHand hand, IModuleContainer container,
            ItemStack stack) {
        return module.getCustomInstance().onInteract(module, player, target, hand, container, stack);
    }

    /**
     * The teleportation unit's jump. Mekanism puts this one in its own tool's {@code use} rather than
     * behind an {@code ICustomModule} hook, so unlike farming and shearing it is replicated here
     * instead of delegated: ray-trace the player's own look vector out to
     * {@link MekanismGearModules#teleportMaxDistance()}, land them on top of whatever it hits, and
     * charge the buffer. {@code requiresBlockTarget()} is the module's own setting for whether a jump
     * into open air is allowed at all.
     *
     * <p>Server side only, and never through a block: the destination is the face the ray hit, which
     * is always the near side of a solid block or a spot in open air. False when anything is missing
     * -- no module, no power, nothing in range -- and the tool's own right-click is then untouched.
     */
    public static boolean teleport(ItemStack stack, Player player) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        int cost = MekanismGearModules.energyPerTeleport();
        if (container == null || player.level().isClientSide() || EnergyBuffer.stored(stack) < cost) {
            return false;
        }
        IModule<ModuleTeleportationUnit> module = container.getIfEnabled(MekanismModules.TELEPORTATION_UNIT);
        if (module == null) {
            return false;
        }
        int range = MekanismGearModules.teleportMaxDistance();
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, reach, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        Vec3 destination;
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos above = hit.getBlockPos().above();
            destination = new Vec3(above.getX() + 0.5D, above.getY(), above.getZ() + 0.5D);
        } else if (module.getCustomInstance().requiresBlockTarget()) {
            return false;
        } else {
            destination = reach;
        }
        EnergyBuffer.extract(stack, cost, false);
        player.teleportTo(destination.x, destination.y, destination.z);
        player.resetFallDistance();
        player.level().playSound(null, destination.x, destination.y, destination.z,
                SoundEvents.ENDERMAN_TELEPORT, player.getSoundSource(), 1.0F, 1.0F);
        return true;
    }

    /**
     * What an attack amplification unit adds to the tool's attack damage. Added on top of Forgeweave's
     * own number and after its cutoff curve, the same call issue #956 made for a Draconic damage
     * module -- the curve exists to bound Forgeweave's own modifier stacking, and running a module's
     * points through it would quietly eat most of them. 0 with no module or a buffer too empty to
     * swing one.
     */
    public static float attackDamageBonus(ItemStack stack) {
        IModuleContainer container = MekanismModuleContainer.containerFor(stack);
        if (container == null || EnergyBuffer.stored(stack) < MekanismGearModules.energyPerBlock()) {
            return 0.0F;
        }
        IModule<ModuleAttackAmplificationUnit> module =
                container.getIfEnabled(MekanismModules.ATTACK_AMPLIFICATION_UNIT);
        return module == null ? 0.0F : module.getCustomInstance().getDamage();
    }

    private MekanismModuleEffects() {}
}
