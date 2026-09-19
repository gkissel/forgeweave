package dev.gkissel.forgeweave.compat.draconic.modules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.DataComponentAccessor;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.modules.ModuleCategory;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.data.EnergyData;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.entities.ShieldControlEntity;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleHostImpl;
import com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext;

import org.jetbrains.annotations.Nullable;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import dev.gkissel.forgeweave.compat.draconic.ForgeweaveDraconicCompat;
import dev.gkissel.forgeweave.config.ForgeweaveConfig; // #968
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.UpgradeHosts;

/**
 * Forgeweave gear made of a fusion metal is a Draconic Evolution module host (issue #956,
 * docs/SCOPE.md M8): Draconic Evolution's own module configuration screen opens on it and its
 * modules install into it, with a grid sized by the {@code evolved} level the fusion metal grants.
 *
 * <p>This is the only class in the package that names a {@code com.brandon3055} type, and it is
 * reached only from inside {@code Forgeweave}'s {@code ModList.get().isLoaded} guard, by way of
 * {@code ForgeweaveDraconicCompat#register}. {@code DraconicSourceIsolationTest} keeps it that way.
 * Everything the rest of the mod needs from here it asks {@link DraconicModules} for.
 *
 * <h2>Why the capability is enough for the screen</h2>
 *
 * <p>Draconic Evolution's module key sends one packet, which the server answers with
 * {@code ModularItemMenu#tryOpenGui(ServerPlayer)}. That method takes the main-hand stack, tests it
 * with {@code stack.getCapability(DECapabilities.Host.ITEM) != null} and, failing that, scans the
 * player inventory with the same predicate. There is no mod-id or item-class filter anywhere on the
 * path, in the key handler, the packet, or the menu, so registering the capability is the whole of
 * the GUI work. No Forgeweave keybind or item-use opener is needed. Read off {@code javap -c} of
 * Draconic Evolution 1.21.1-3.1.4.633; no live client has run it, see the pull request.
 *
 * <h2>Persistence</h2>
 *
 * <p>{@code ModuleHostImpl#saveData} and {@code loadData} write Draconic Evolution's own
 * {@code ItemData.MODULE_ENTITIES}, {@code CONFIG_PROPERTIES} and {@code PROVIDER_IDENTITY} data
 * components through the {@code DataComponentAccessor} handed to
 * {@code ModuleHostImpl#updateDataAccess}; the component types are not a parameter. A Forgeweave-owned
 * component would mean overriding both methods and losing {@code gatherProperties}, which is private,
 * and would buy nothing: the entities are encoded by {@code ModuleEntity.CODEC}, which dispatches
 * through Draconic Evolution's registries, so the data is unreadable without that mod either way. So
 * the host writes DE's components on a Forgeweave stack, exactly as {@code CapabilityData} does for
 * DE's own items, and uninstalling Draconic Evolution drops them the same way it drops them from a
 * wyvern pickaxe.
 */
public final class DraconicModuleHost implements DraconicModules.Bridge {

    /** The bridge {@link DraconicModules} holds; stateless, one instance. */
    public static final DraconicModuleHost INSTANCE = new DraconicModuleHost();

    /**
     * The tech level each {@code evolved} level hosts at, index 0 being level 1. Matches
     * {@code ForgeweaveDraconicCompat#FUSION_METALS}: duskweld is draconium, emberweld wyvern,
     * starweld draconic, voidweld chaotic, so a tool hosts at the tier of the metal it is made of.
     * Issue #965 added the inert rung; the three above it are unchanged.
     */
    private static final List<TechLevel> TECH_LEVELS =
            List.of(TechLevel.DRACONIUM, TechLevel.WYVERN, TechLevel.DRACONIC, TechLevel.CHAOTIC);

    /**
     * Installs the bridge and the capability listener. Called from
     * {@code ForgeweaveDraconicCompat#register}, which itself only runs with the mod present.
     */
    public static void register(IEventBus modEventBus) {
        DraconicModules.install(INSTANCE);
        modEventBus.addListener(DraconicModuleHost::registerCapabilities);
        // Issue #1033, maintainer rule 2026-09-18: a part swap that drops or shrinks a tool's module
        // host gives back whatever it can no longer carry rather than losing or stranding it.
        UpgradeHosts.register(DraconicModuleHost::reclaim);
    }

    /**
     * Registers {@code DECapabilities.Host.ITEM} over every assembled tool and armour piece, the same
     * {@code ToolAssemblyRecipes.ENTRIES} roster and the same per-stack provider shape
     * {@code ToolItem#registerCapabilities} already uses for Forge Energy. The provider returns
     * {@code null} for a stack with no {@code evolved} trait, which is what makes a plain iron
     * pickaxe not a host at all rather than a host with an empty grid.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        ItemLike[] items = ToolAssemblyRecipes.ENTRIES.stream()
                .map(entry -> (ItemLike) entry.tool().get())
                .distinct()
                .toArray(ItemLike[]::new);
        event.registerItem(DECapabilities.Host.ITEM, (stack, context) -> hostFor(stack), items);
    }

    /**
     * The host as the capability hands it out: {@link #newHost} plus the stack-backed data access
     * that loads what is already installed and saves what changes. {@code null} for a stack that is
     * not a host.
     */
    @Nullable
    public static ModuleHostImpl hostFor(ItemStack stack) {
        ModuleHostImpl host = newHost(stack);
        if (host != null) {
            host.updateDataAccess(DataComponentAccessor.itemStack(stack));
        }
        return host;
    }

    /**
     * The empty host for {@code stack}: tech level and grid from its {@code evolved} level, categories
     * from its shape. Split from {@link #hostFor} so the shape decisions can be unit tested without
     * Draconic Evolution's data component types, which only exist on a running install.
     *
     * @return {@code null} for a stack carrying no {@code evolved} trait
     */
    @Nullable
    public static ModuleHostImpl newHost(ItemStack stack) {
        // #968 (D-M8-5): with compat.draconicModules off nothing is a host, so Draconic Evolution's
        // ModularItemMenu#tryOpenGui finds no capability and its module screen does not open on
        // Forgeweave gear. The stack's own module data components are left untouched -- not building
        // a host over them is what makes them inert rather than lost.
        if (!ForgeweaveConfig.enabled(ForgeweaveConfig.DRACONIC_MODULES)) {
            return null;
        }
        int evolved = ForgeweaveDraconicCompat.evolvedLevel(stack);
        if (evolved < 1 || evolved > DraconicModules.MAX_EVOLVED) {
            return null;
        }
        // Maintainer decision 2026-09-06: only a tool made of a weld hosts modules; a tool made of a
        // Draconic core takes fusion upgrades instead (ForgeweaveDraconicCompat#isWeldTool).
        if (!ForgeweaveDraconicCompat.isWeldTool(stack)) {
            return null;
        }
        Set<ModuleCategory> categories = categories(stack);
        return new ModuleHostImpl(TECH_LEVELS.get(evolved - 1),
                DraconicModules.gridWidth(evolved),
                DraconicModules.gridHeight(evolved),
                providerName(stack),
                false,
                categories.toArray(ModuleCategory[]::new));
    }

    /**
     * {@link UpgradeHosts.Host}: whatever {@code replacement} can no longer host, above the new tech
     * level or outside the new grid, turned back into the modules' own items (issue #1033, maintainer
     * rule 2026-09-18). Registered by {@link #register}, so it runs only with Draconic Evolution
     * present, and reads {@code compat.draconicModules} itself rather than through {@link #newHost} --
     * that toggle must make this method inert, not treat "off" as "every module is stranded".
     *
     * <p>{@code original} decides only whether there was ever anything to look at: if it never hosted
     * modules at all ({@link #newHost} answers {@code null} for it), nothing could be installed and
     * this returns empty without touching {@code replacement}. Everything installed is then read off
     * {@code replacement} itself -- {@code result = toolStack.copy()} in {@code resolveExchange} carries
     * Draconic Evolution's own module data components across the swap untouched, so a host shaped like
     * the original but bound to the replacement's storage sees exactly what was installed before this
     * exchange ran.
     *
     * <p><b>What "no longer fits" means.</b> A module that still fits keeps its stored grid position --
     * this method never repacks the grid to make more of them fit. It comes back only if the
     * replacement no longer hosts at all, if its own tech level ({@link
     * com.brandon3055.draconicevolution.api.modules.Module#getModuleTechLevel}) is higher than the new
     * host's, or if {@link ModuleEntity#isPosValid} says its stored position falls outside the new
     * grid -- Draconic Evolution's own placement bounds check, not a Forgeweave-invented packing rule.
     *
     * <p><b>Energy.</b> {@code EnergyEntity}'s own stored charge only ever moves through Draconic
     * Evolution's {@code IOPStorage} capability ({@code CapabilityOP.ITEM}), which {@link
     * #registerCapabilities} never registers on Forgeweave gear (only {@code DECapabilities.Host.ITEM}
     * is). So {@link StackModuleContext#getOpStorage} answers {@code null} here exactly as it would on
     * a Forgeweave stack at every other call site, an energy module's own charge field is never written
     * above zero in the first place, and a reclaimed one always comes back empty. The tool's own shared
     * Forge Energy buffer ({@code EnergyBuffer}, what the module's capacity phase 1 actually adds to)
     * belongs to the tool, not to any one module, and this method never touches it.
     */
    static List<ItemStack> reclaim(ItemStack original, ItemStack replacement) {
        if (!ForgeweaveConfig.enabled(ForgeweaveConfig.DRACONIC_MODULES)) {
            return List.of();
        }
        ModuleHostImpl installedView = newHost(original);
        if (installedView == null) {
            return List.of(); // original never hosted modules, so nothing could be installed on it
        }
        try (installedView) {
            installedView.updateDataAccess(DataComponentAccessor.itemStack(replacement));
            List<ModuleEntity<?>> installed = installedView.getModuleEntities();
            if (installed.isEmpty()) {
                return List.of();
            }
            try (ModuleHostImpl newShape = newHost(replacement)) {
                List<ModuleEntity<?>> stranded =
                        installed.stream().filter(entity -> !stillFits(entity, newShape)).toList();
                if (stranded.isEmpty()) {
                    return List.of();
                }
                StackModuleContext context = new StackModuleContext(replacement, null, null);
                List<ItemStack> reclaimed = new ArrayList<>(stranded.size());
                for (ModuleEntity<?> entity : stranded) {
                    installedView.removeModule(entity, context);
                    ItemStack moduleStack = new ItemStack(entity.getModule().getItem());
                    entity.saveEntityToStack(moduleStack, context);
                    reclaimed.add(moduleStack);
                }
                installedView.saveData();
                return List.copyOf(reclaimed);
            }
        }
    }

    /** Whether {@code entity}'s tech level and stored grid position both still fit {@code newShape}. */
    private static boolean stillFits(ModuleEntity<?> entity, @Nullable ModuleHostImpl newShape) {
        return newShape != null
                && entity.getModule().getModuleTechLevel().compareTo(newShape.getHostTechLevel()) <= 0
                && entity.isPosValid(newShape.getGridWidth(), newShape.getGridHeight());
    }

    /**
     * Which of Draconic Evolution's module categories this stack accepts, read off the shape
     * Forgeweave already knows: the block tags a tool mines, whether it is a weapon, its
     * {@code ToolConstants.Category}, and for armour the slot it is worn in.
     *
     * <p>{@code ENERGY} is on everything, matching Draconic Evolution, where every
     * {@code IModularEnergyItem} gets it. {@code CHESTPIECE} goes on the chestplate alone: it is the
     * category Draconic Evolution's flight, shield, undying and auto-feed modules live in, and
     * {@code ModularArmorEventHandler} applies those to any equipped stack with a host, so a
     * Forgeweave chestplate gets them for free.
     */
    private static Set<ModuleCategory> categories(ItemStack stack) {
        Set<ModuleCategory> categories = new LinkedHashSet<>();
        categories.add(ModuleCategory.ENERGY);
        Item item = stack.getItem();
        if (item instanceof ArmorPieceItem armor) {
            switch (armor.getEquipmentSlot()) {
                case HEAD -> categories.add(ModuleCategory.ARMOR_HEAD);
                case CHEST -> {
                    categories.add(ModuleCategory.ARMOR_CHEST);
                    categories.add(ModuleCategory.CHESTPIECE);
                }
                case LEGS -> categories.add(ModuleCategory.ARMOR_LEGS);
                case FEET -> categories.add(ModuleCategory.ARMOR_FEET);
                default -> { }
            }
            return categories;
        }
        if (item instanceof ToolItem tool) {
            List<TagKey<Block>> mineable = tool.mineableBlocks();
            if (mineable.contains(BlockTags.MINEABLE_WITH_PICKAXE)
                    || mineable.contains(BlockTags.MINEABLE_WITH_AXE)
                    || mineable.contains(BlockTags.MINEABLE_WITH_SHOVEL)
                    || mineable.contains(BlockTags.MINEABLE_WITH_HOE)) {
                categories.add(ModuleCategory.MINING_TOOL);
            }
            if (mineable.contains(BlockTags.MINEABLE_WITH_AXE)) {
                categories.add(ModuleCategory.TOOL_AXE);
            }
            if (mineable.contains(BlockTags.MINEABLE_WITH_SHOVEL)) {
                categories.add(ModuleCategory.TOOL_SHOVEL);
            }
            if (mineable.contains(BlockTags.MINEABLE_WITH_HOE)) {
                categories.add(ModuleCategory.TOOL_HOE);
            }
            if (tool.isWeapon()) {
                categories.add(ModuleCategory.MELEE_WEAPON);
            }
        }
        if (ToolAssemblyRecipes.entryFor(stack)
                .map(entry -> entry.constants().category() == ToolConstants.Category.RANGED)
                .orElse(false)) {
            categories.add(ModuleCategory.RANGED_WEAPON);
        }
        return categories;
    }

    /**
     * The {@code providerName} Draconic Evolution groups a host's config properties under. Its own
     * items pass a bare shape word ({@code "pickaxe"}, {@code "chestpiece"}); Forgeweave passes the
     * tool's {@code ToolConstants.Entry} id, which reads the same way. Not player-facing prose, so no
     * lang key: the item config screen shows the stack's own hover name above it.
     */
    private static String providerName(ItemStack stack) {
        return ToolAssemblyRecipes.entryFor(stack)
                .map(entry -> entry.constants().id())
                .orElseGet(() -> stack.getItem().toString());
    }

    @Override
    public int installedModules(ItemStack stack) {
        try (ModuleHostImpl host = hostFor(stack)) {
            return host == null ? 0 : host.getModuleEntities().size();
        }
    }

    // The tool-active effects (issue #956 phase 2) all live in DraconicModuleEffects; this class
    // stays about hosting. Every one of them answers neutrally for a stack that is not a host, which
    // is what makes a module effect on a tool without one a no-op rather than a crash.

    @Override
    public float digSpeedMultiplier(ItemStack stack) {
        return DraconicModuleEffects.digSpeedMultiplier(stack);
    }

    @Override
    public int miningAoe(ItemStack stack) {
        return DraconicModuleEffects.miningAoe(stack);
    }

    @Override
    public int miningEnergyCost(ItemStack stack) {
        return DraconicModuleEffects.miningEnergyCost(stack);
    }

    @Override
    public float attackDamageBonus(ItemStack stack) {
        return DraconicModuleEffects.attackDamageBonus(stack);
    }

    @Override
    public boolean meleeAoe(Player player, Entity target, ItemStack stack, float damage) {
        return DraconicModuleEffects.meleeAoe(player, target, stack, damage);
    }

    @Override
    public DraconicModules.Projectile projectile(ItemStack stack) {
        return DraconicModuleEffects.projectile(stack);
    }

    @Override
    public int shotEnergyCost(ItemStack stack) {
        return DraconicModuleEffects.shotEnergyCost(stack);
    }

    /**
     * {@link DraconicModules#drainShield}: the shield controller on the target's chestpiece, if it
     * wears one with a Draconic module host, loses {@code amount} shield points. Draconic Evolution's
     * own armor handler blocks incoming damage out of those points, so draining them is what lets
     * the hit after this one land.
     */
    @Override
    public boolean drainShield(LivingEntity target, double amount) {
        ItemStack chest = target.getItemBySlot(EquipmentSlot.CHEST);
        ModuleHost host = chest.isEmpty() ? null : DECapabilities.getHost(chest);
        if (host == null) {
            return false;
        }
        boolean drained = false;
        for (ModuleEntity<?> entity : host.getEntitiesByType(ModuleTypes.SHIELD_CONTROLLER).toList()) {
            if (entity instanceof ShieldControlEntity shield && shield.getShieldPoints() > 0) {
                shield.subtractShieldPoints(amount);
                drained = true;
            }
        }
        return drained;
    }

    @Override
    public int moduleEnergyCapacity(ItemStack stack) {
        // ponytail: builds a host per call, as Draconic Evolution's own capability provider does.
        // The evolvedLevel read inside newHost short-circuits every non-evolved stack on one data
        // component lookup, which is every stack in a normal inventory. Cache per stack if a profile
        // ever says otherwise.
        try (ModuleHostImpl host = hostFor(stack)) {
            if (host == null) {
                return 0;
            }
            EnergyData energy = host.getModuleData(ModuleTypes.ENERGY_STORAGE);
            if (energy == null) {
                return 0;
            }
            return (int) Math.min(energy.capacity(), Integer.MAX_VALUE);
        }
    }

    private DraconicModuleHost() {}
}
