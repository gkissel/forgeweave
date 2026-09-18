package dev.gkissel.forgeweave.compat.mekanism.modules;

import java.util.ArrayList;
import java.util.List;

import mekanism.api.MekanismIMC;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleContainer;
import mekanism.api.gear.IModuleHelper;
import mekanism.common.content.gear.ModuleContainer;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.radiation.item.RadiationShieldingHandler;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.compat.mekanism.ForgeweaveMekanismCompat;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.UpgradeHosts;

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
     * Installs the bridge, the default component pass, the IMC pass, the capability listener and the
     * armour defence seam. Called from {@code ForgeweaveMekanismCompat#register}, which itself only
     * runs with the mod present.
     */
    public static void register(IEventBus modEventBus) {
        MekanismGearModules.install(INSTANCE);
        modEventBus.addListener(MekanismModuleContainer::modifyDefaultComponents);
        modEventBus.addListener(MekanismModuleContainer::enqueueImc);
        modEventBus.addListener(MekanismModuleContainer::registerCapabilities);
        // The absorption seam names no mekanism type; it reads the ratio and the cost back across the
        // MekanismGearModules seam, so it is registered here only because this is where the guard is.
        CombatSeams.register(MekanismAbsorption.INSTANCE);
        // Maintainer rule, 2026-09-18: a part swap that stops the tool being a container gives the
        // modules back rather than losing or stranding them.
        UpgradeHosts.register(MekanismModuleContainer::reclaimModules);
    }

    /**
     * {@code UpgradeHosts.Host}: the modules a part swap has just invalidated, turned back into items.
     *
     * <p>What invalidates them is losing the metal. Mekanism decides which modules an item accepts per
     * <em>item</em>, off the IMC roster, so a swap can never change what a pickaxe supports -- only
     * whether the stack is a container at all, which is {@code atomic_matter_alloy} being in its parts.
     * So: a swap that keeps the metal keeps every module exactly where it was, and a swap that drops
     * the metal hands all of them back and leaves an empty container behind.
     *
     * <p>Returns nothing and strips nothing while {@code mekanismModules} is off. Off is inert, not
     * destructive: the modules stay on the stack and start working again when the toggle returns.
     */
    private static List<ItemStack> reclaimModules(ItemStack original, ItemStack replacement) {
        if (!MekanismGearModules.modulesEnabled()
                || !ForgeweaveMekanismCompat.isContainerStack(original)
                || ForgeweaveMekanismCompat.isContainerStack(replacement)) {
            return List.of();
        }
        IModuleContainer container = IModuleHelper.INSTANCE.getModuleContainer(replacement);
        if (container == null || container.installedCount() == 0) {
            return List.of();
        }
        List<ItemStack> reclaimed = new ArrayList<>();
        for (IModule<?> module : container.modules()) {
            Item item = module.getUntypedData().getItemHolder().value();
            int remaining = module.getInstalledCount();
            while (remaining > 0) {
                int batch = Math.min(remaining, item.getDefaultMaxStackSize());
                reclaimed.add(new ItemStack(item, batch));
                remaining -= batch;
            }
        }
        replacement.set(MekanismDataComponents.MODULE_CONTAINER.get(), ModuleContainer.EMPTY);
        return reclaimed;
    }

    /**
     * The compat item factory D-M8-15 asks for, as a default component pass: every assembled Forgeweave
     * tool and armour piece gains Mekanism's {@code module_container} default component, so the
     * Modification Station has something to install the first module into.
     *
     * <p><b>Deviation from D-M8-15's wording, and why.</b> The decision says
     * {@code IModuleHelper#applyModuleContainerProperties} called at registration through a compat item
     * factory. That method is exactly
     * {@code properties.component(MekanismDataComponents.MODULE_CONTAINER, ModuleContainer.EMPTY)} --
     * verified by disassembling {@code mekanism.common.content.gear.ModuleHelper} -- and
     * {@code Item.Properties#component} dereferences the holder <em>eagerly</em>. Forgeweave builds
     * every gear item's {@code Item.Properties} in {@code ForgeweaveItems}' static initialiser, which
     * runs during mod construction, long before Mekanism's own data component types are bound; calling
     * the API method there throws "Trying to access unbound value". Mekanism's own items get away with
     * it because their properties are built inside a {@code DeferredRegister} supplier.
     *
     * <p>{@code ModifyDefaultComponentsEvent} is NeoForge's own seam for adding a default component to
     * an already-registered item, it fires after every registry is populated, and it sets the identical
     * component. It also leaves {@code ForgeweaveItems} and every item class completely untouched, which
     * is the point the "compat item factory" wording was making: the plain {@code ToolItem} stays
     * Mekanism-free.
     *
     * <p>No toggle on this pass. A {@code SERVER} config is not loaded this early, and D-M8-5's contract
     * is inert rather than absent anyway: an empty container component on a tool nobody ever takes to a
     * Modification Station is invisible.
     */
    private static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        for (Item item : gearItems()) {
            event.modify(item, patch -> patch.set(MekanismDataComponents.MODULE_CONTAINER.get(),
                    ModuleContainer.EMPTY));
        }
    }

    /**
     * Tells Mekanism which of its module rosters each Forgeweave gear item accepts: the MekaTool's
     * whole set on every tool, and the matching MekaSuit slot set on each armour piece.
     */
    private static void enqueueImc(InterModEnqueueEvent event) {
        for (Item item : gearItems()) {
            // The Holder overload: Mekanism 10.7.19 marks every ItemLike and Item route deprecated for
            // removal. Safe to wrap here because this runs on InterModEnqueueEvent, long after the item
            // registry is frozen.
            MekanismIMC.addModuleContainer(BuiltInRegistries.ITEM.wrapAsHolder(item), imcMethod(item));
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
        // No toggle read here: MekanismGearModules#bridge already answers null while mekanismModules is
        // off, so every query through the seam short-circuits before it reaches this class. The one
        // caller that bypasses the seam is the radiation shielding capability provider, which reads the
        // toggle itself.
        if (!ForgeweaveMekanismCompat.isContainerStack(stack)) {
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
