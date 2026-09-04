package dev.gkissel.forgeweave.compat.draconic.modules;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.DataComponentAccessor;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.modules.ModuleCategory;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.data.EnergyData;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleHostImpl;

import org.jetbrains.annotations.Nullable;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import dev.gkissel.forgeweave.compat.draconic.ForgeweaveDraconicCompat;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolConstants;

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
     * The tech level each {@code evolved} level hosts at, index 0 being level I. Matches
     * {@code ForgeweaveDraconicCompat#FUSION_METALS}: emberweld is wyvern, starweld draconic,
     * voidweld chaotic, so a tool hosts at the tier of the metal it is made of.
     */
    private static final List<TechLevel> TECH_LEVELS =
            List.of(TechLevel.WYVERN, TechLevel.DRACONIC, TechLevel.CHAOTIC);

    /**
     * Installs the bridge and the capability listener. Called from
     * {@code ForgeweaveDraconicCompat#register}, which itself only runs with the mod present.
     */
    public static void register(IEventBus modEventBus) {
        DraconicModules.install(INSTANCE);
        modEventBus.addListener(DraconicModuleHost::registerCapabilities);
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
        int evolved = ForgeweaveDraconicCompat.evolvedLevel(stack);
        if (evolved < 1 || evolved > DraconicModules.MAX_EVOLVED) {
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
