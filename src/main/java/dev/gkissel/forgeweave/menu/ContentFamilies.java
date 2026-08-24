package dev.gkissel.forgeweave.menu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Which of {@link ForgeweaveConfig}'s {@code content} families a thing belongs to, and whether that
 * family is currently on -- the one place the content-family toggles ticket's
 * "unobtainable, never unregistered" rule is decided.
 *
 * <p>Nothing here is a list. A tool's family is
 * {@link ToolConstants.Entry#category()}; everything else is derived by walking
 * {@link ToolAssemblyRecipes#ENTRIES}, which is already the table the station itself assembles
 * from. A tool issue that adds a row therefore inherits the whole gate -- assembly, tabs, its
 * exclusive parts, their patterns and their casts -- with no second table to keep in sync, the same
 * reasoning {@link ToolAssemblyRecipes#LARGE_TOOLS} is a tag rather than a roster for.
 *
 * <h2>What "exclusive" means</h2>
 *
 * <p>A part is unobtainable only when <em>every</em> tool that takes it is in an off family
 * ({@link #partEnabled}). A tool handle is shared by both families and survives either toggle; a
 * sword blade serves only melee weapons and goes with them. A part no tool takes at all (the
 * sharpening kit, a shard) belongs to no family and is never gated. Patterns and casts follow their
 * part through the registry-name convention the item registry already uses -- {@code <part>},
 * {@code pattern_<part>}, {@code cast_<part>}, {@code clay_cast_<part>} -- rather than through a
 * fourth hand-written mapping.
 *
 * <h2>Reading the config safely</h2>
 *
 * <p>Every read goes through {@link ForgeweaveConfig#enabled(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)},
 * which answers "on" whenever the {@code SERVER} spec is not loaded -- see that method for why the
 * permissive fallback is the right one and which callers reach it.
 */
public final class ContentFamilies {

    private static final String PATTERN_PREFIX = "pattern_";
    private static final String CAST_PREFIX = "cast_";
    private static final String CLAY_PREFIX = "clay_";

    /** Whether {@code category}'s family is currently on. */
    public static boolean enabled(ToolConstants.Category category) {
        return ForgeweaveConfig.enabled(switch (category) {
            case HARVEST -> ForgeweaveConfig.HARVEST_TOOLS;
            case MELEE -> ForgeweaveConfig.MELEE_WEAPONS;
            case RANGED -> ForgeweaveConfig.RANGED_WEAPONS;
            case ARMOR -> ForgeweaveConfig.ARMOR;
        });
    }

    /** Whether the family that builds {@code entry} is on, i.e. whether it can be assembled at all. */
    public static boolean toolEnabled(ToolAssemblyRecipes.Entry entry) {
        return enabled(entry.constants().category());
    }

    /**
     * Whether the part registered under {@code partId} is obtainable: true unless every tool taking
     * it sits in an off family. A part no tool takes is in no family and so is always obtainable.
     */
    public static boolean partEnabled(String partId) {
        boolean taken = false;
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            for (ToolConstants.PartSlot slot : entry.constants().parts()) {
                if (slot.partId().equals(partId)) {
                    if (toolEnabled(entry)) {
                        return true;
                    }
                    taken = true;
                }
            }
        }
        return !taken;
    }

    /**
     * Whether {@code item} is obtainable under the current family toggles: false for a tool of an off
     * family, and for the parts, patterns and casts that serve only off families. Anything else --
     * every block, ingot, fluid bucket and modifier item -- answers true, since no family owns it.
     *
     * <p>Matched off the item's own registry name rather than off an item-to-family map: part items
     * are registered under the same id {@link ToolConstants.PartSlot#partId()} names, and their
     * pattern and (gold and clay) cast counterparts under that id with a fixed prefix, which is what
     * {@code ForgeweaveItems} already builds them from.
     */
    public static boolean itemEnabled(Item item) {
        if (item instanceof ToolItem || item instanceof ArmorPieceItem) {
            return ToolAssemblyRecipes.ENTRIES.stream()
                    .filter(entry -> entry.tool().get() == item)
                    .findFirst()
                    .map(ContentFamilies::toolEnabled)
                    .orElse(true);
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (!id.getNamespace().equals(Forgeweave.MODID)) {
            return true;
        }
        String path = id.getPath();
        if (item instanceof PartItem) {
            return partEnabled(path);
        }
        if (path.startsWith(PATTERN_PREFIX)) {
            return partEnabled(path.substring(PATTERN_PREFIX.length()));
        }
        if (path.startsWith(CLAY_PREFIX + CAST_PREFIX)) {
            return partEnabled(path.substring(CLAY_PREFIX.length() + CAST_PREFIX.length()));
        }
        if (path.startsWith(CAST_PREFIX)) {
            return partEnabled(path.substring(CAST_PREFIX.length()));
        }
        return true;
    }

    /** {@link #itemEnabled(Item)} for a stack; an empty stack is never gated. */
    public static boolean itemEnabled(ItemStack stack) {
        return stack.isEmpty() || itemEnabled(stack.getItem());
    }

    /**
     * Why the station refuses this loadout, or {@code null} if no family gate applies -- the message
     * {@link ToolStationMenu#rejection} takes the info panel over with (issue #378). One message for
     * both families: which family is off is the server's business, and naming it would only tell a
     * player to go edit a config they cannot reach.
     */
    public static Component disabledMessage() {
        return Component.translatable("gui.forgeweave.tool_station.family_disabled");
    }

    private ContentFamilies() {}
}
