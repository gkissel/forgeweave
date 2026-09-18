package dev.gkissel.forgeweave.compat.mekanism.modules;

import java.util.List;

import mekanism.api.MekanismIMC;
import mekanism.api.gear.IModuleContainer;
import mekanism.api.gear.IModuleHelper;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.radiation.item.RadiationShieldingHandler;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.compat.mekanism.ForgeweaveMekanismCompat;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * Forgeweave gear made of {@code atomic_matter_alloy} is a Mekanism module container (issue #993,
 * docs/SCOPE.md M8, D-M8-15): Mekanism's own Modification Station installs MekaTool and MekaSuit
 * modules into it, and Forgeweave's own hooks run their effects.
 *
 * <p>This and {@link MekanismModuleEffects} are the only classes in the package that name a
 * {@code mekanism} type, and both are reached only from inside {@code Forgeweave}'s
 * {@code ModList.get().isLoaded} guard, by way of {@code ForgeweaveMekanismCompat}.
 * {@code MekanismSourceIsolationTest} keeps it that way. Everything the rest of the mod needs from
 * here it asks {@link MekanismGearModules} for.
 *
 * <h2>Verified Mekanism coordinates (Mekanism 1.21.1-10.7.19.85, read off the published jar)</h2>
 *
 * <ul>
 *   <li>{@code mekanism.api.MekanismIMC#addModuleContainer(ItemLike, String)} wraps a
 *       {@code ModuleContainerTarget} and calls
 *       {@code net.neoforged.fml.InterModComms.sendTo("mekanism", "add_module_container", supplier)},
 *       so it has to run during {@code InterModEnqueueEvent}.
 *   <li>{@code MekanismIMC#ADD_MEKA_TOOL_MODULES} and the four
 *       {@code ADD_MEKA_SUIT_{HELMET,BODYARMOR,PANTS,BOOTS}_MODULES} names are the module rosters a
 *       target asks for.
 *   <li>{@code mekanism.api.gear.IModuleHelper#applyModuleContainerProperties(Item.Properties)} is
 *       exactly {@code properties.component(MekanismDataComponents.MODULE_CONTAINER, ModuleContainer.EMPTY)}
 *       -- one default data component, nothing else.
 *   <li>{@code mekanism.common.capabilities.Capabilities#RADIATION_SHIELDING} is an
 *       {@code ItemCapability<IRadiationShielding, Void>}; {@code RadiationShieldingHandler#create(double)}
 *       is the handler Mekanism's own armour uses.
 * </ul>
 *
 * <h2>Why the Modification Station opens, and the one deviation that follows</h2>
 *
 * <p>The station's container slot validator is {@code IModuleHelper#isModuleContainer}, which resolves
 * to {@code isModuleContainer(Item)} and reads the map the IMC above fills. It is keyed on the
 * <em>item</em>, with no per-stack hook anywhere on the path, so registering the IMC is the whole of
 * the GUI work and there is no way to tell Mekanism "only the stacks made of this metal".
 *
 * <p>So the IMC covers every assembled Forgeweave tool and armour piece, and the metal gates the
 * <em>effects</em> instead: {@link MekanismModuleEffects} answers neutrally for a stack with no
 * {@code atomic_matter_alloy} part, so a module installed on a plain iron pickaxe sits there doing
 * nothing. That is the same shape an empty energy buffer already has, and it is recorded as a
 * deviation in the pull request rather than hidden here.
 *
 * <h2>Persistence</h2>
 *
 * <p>The installed modules live in Mekanism's own {@code mekanism:module_container} data component on
 * the Forgeweave stack. Forgeweave neither copies nor migrates it, which is the rule JC-D set for
 * Apotheosis affix data: the component is encoded by Mekanism's own codec over its own registries, so
 * it is unreadable without that mod either way, and uninstalling Mekanism drops it from a Forgeweave
 * pickaxe exactly as it drops it from a MekaTool.
 */
public final class MekanismModuleContainer implements MekanismGearModules.Bridge {

    /** The bridge {@link MekanismGearModules} holds; stateless, one instance. */
    public static final MekanismModuleContainer INSTANCE = new MekanismModuleContainer();

    /**
     * Installs the bridge, the IMC pass, the capability listener and the armour defence seam. Called
     * from {@code ForgeweaveMekanismCompat#register}, which itself only runs with the mod present.
     */
    public static void register(IEventBus modEventBus) {
        MekanismGearModules.install(INSTANCE);
        modEventBus.addListener(MekanismModuleContainer::enqueueImc);
        modEventBus.addListener(MekanismModuleContainer::registerCapabilities);
        // The absorption seam names no mekanism type; it reads the ratio and the cost back across the
        // MekanismGearModules seam, so it is registered here only because this is where the guard is.
        CombatSeams.register(MekanismAbsorption.INSTANCE);
    }

    /**
     * The compat item factory's Mekanism half -- see
     * {@code ForgeweaveMekanismCompat#containerProperties} for why it has to happen at registration and
     * why there is no toggle on this branch.
     */
    public static Item.Properties applyContainerProperties(Item.Properties properties) {
        return IModuleHelper.INSTANCE.applyModuleContainerProperties(properties);
    }

    /**
     * Tells Mekanism which of its module rosters each Forgeweave gear item accepts: the MekaTool's
     * whole set on every tool, and the matching MekaSuit slot set on each armour piece.
     */
    private static void enqueueImc(InterModEnqueueEvent event) {
        for (Item item : gearItems()) {
            MekanismIMC.addModuleContainer(item, imcMethod(item));
        }
    }

    /** Which roster {@code item} asks for -- the MekaSuit's slot set for armour, the MekaTool's otherwise. */
    private static String imcMethod(Item item) {
        if (item instanceof ArmorPieceItem armor) {
            return switch (armor.getEquipmentSlot()) {
                case HEAD -> MekanismIMC.ADD_MEKA_SUIT_HELMET_MODULES;
                case CHEST -> MekanismIMC.ADD_MEKA_SUIT_BODYARMOR_MODULES;
                case LEGS -> MekanismIMC.ADD_MEKA_SUIT_PANTS_MODULES;
                case FEET -> MekanismIMC.ADD_MEKA_SUIT_BOOTS_MODULES;
                default -> MekanismIMC.ADD_MEKA_TOOL_MODULES;
            };
        }
        return MekanismIMC.ADD_MEKA_TOOL_MODULES;
    }

    /**
     * Registers {@code Capabilities.RADIATION_SHIELDING} over every assembled tool and armour piece,
     * the same {@code ToolAssemblyRecipes.ENTRIES} roster and the same per-stack provider shape
     * {@code ToolItem#registerCapabilities} already uses for Forge Energy. The provider answers
     * {@code null} for a stack with no {@code atomic_matter_alloy} part, so an ordinary Forgeweave
     * helmet shields nothing rather than shielding zero.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        ItemLike[] items = ToolAssemblyRecipes.ENTRIES.stream()
                .map(entry -> (ItemLike) entry.tool().get())
                .distinct()
                .toArray(ItemLike[]::new);
        event.registerItem(Capabilities.RADIATION_SHIELDING, (stack, context) -> shieldingFor(stack), items);
    }

    @Nullable
    private static RadiationShieldingHandler shieldingFor(ItemStack stack) {
        double shielding = MekanismModuleEffects.radiationShielding(stack);
        return shielding <= 0.0D ? null : RadiationShieldingHandler.create(shielding);
    }

    /** Every assembled Forgeweave tool and armour piece item, deduplicated. */
    private static List<Item> gearItems() {
        return ToolAssemblyRecipes.ENTRIES.stream()
                .map(entry -> (Item) entry.tool().get())
                .distinct()
                .toList();
    }

    /**
     * The module container on {@code stack}, or {@code null} when there is none -- a stack with no
     * {@code atomic_matter_alloy} part, or the toggle off. Package-visible so
     * {@link MekanismModuleEffects} shares the one gate rather than repeating it.
     */
    @Nullable
    static IModuleContainer containerFor(ItemStack stack) {
        if (!MekanismGearModules.modulesEnabled() || !ForgeweaveMekanismCompat.isContainerStack(stack)) {
            return null;
        }
        return IModuleHelper.INSTANCE.getModuleContainer(stack);
    }

    // The effects all live in MekanismModuleEffects; this class stays about hosting. Every one of them
    // answers neutrally for a stack that is not a container, which is what makes a module on a tool
    // without the metal a no-op rather than a crash.

    @Override
    public int installedModules(ItemStack stack) {
        IModuleContainer container = containerFor(stack);
        return container == null ? 0 : container.installedCount();
    }

    @Override
    public int moduleEnergyCapacity(ItemStack stack) {
        return MekanismModuleEffects.moduleEnergyCapacity(stack);
    }

    @Override
    public float digSpeedMultiplier(ItemStack stack) {
        return MekanismModuleEffects.digSpeedMultiplier(stack);
    }

    @Override
    public int miningAoe(ItemStack stack) {
        return MekanismModuleEffects.miningAoe(stack);
    }

    @Override
    public int miningEnergyCost(ItemStack stack) {
        return MekanismModuleEffects.miningEnergyCost(stack);
    }

    @Override
    public ItemEnchantments moduleEnchantments(ItemStack stack) {
        return MekanismModuleEffects.moduleEnchantments(stack);
    }

    @Override
    public List<BlockPos> veinPositions(ItemStack stack, Level level, BlockPos origin) {
        return MekanismModuleEffects.veinPositions(stack, level, origin);
    }

    @Override
    public MekanismGearModules.Absorption damageAbsorbed(ItemStack piece, LivingEntity defender, float damage) {
        return MekanismModuleEffects.damageAbsorbed(piece, defender, damage);
    }

    @Override
    public double radiationShielding(ItemStack stack) {
        return MekanismModuleEffects.radiationShielding(stack);
    }

    @Override
    public void tickModules(ItemStack stack, Player player, boolean serverSide) {
        MekanismModuleEffects.tickModules(stack, player, serverSide);
    }

    private MekanismModuleContainer() {}
}
