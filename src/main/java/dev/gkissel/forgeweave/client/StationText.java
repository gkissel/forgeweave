package dev.gkissel.forgeweave.client;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * The lines the stations' info panels show (issue #47). Everything here is a read of data that
 * already exists on the stack or in the material registry -- an assembled tool carries its
 * {@link ToolStats.Stats}, its {@link ToolMaterials} and its trait ids as data components written at
 * assembly time -- so no stat formula is restated: {@code ToolStats} stays the only place that
 * computes them, and this is only how they are worded.
 *
 * <p>Trait names and descriptions come from the {@code trait.<namespace>.<path>.name}/{@code
 * .description} keys {@code ForgeweaveLanguageProvider} already ships (issue #12), keyed by the ids
 * on the stack; nothing here needs {@code ForgeweaveTraits}, so an unknown id from a datapack
 * material degrades to a visible untranslated key rather than to nothing at all.
 */
public final class StationText {
    /** Trailing-zero-free numbers, so 1.0 reads "1" and 1.25 reads "1.25" (upstream's {@code Util.df}). */
    private static final DecimalFormat FORMAT =
            new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    /** Durability, mining speed and attack damage of an assembled tool, in upstream's order. */
    public static List<Component> toolStats(ItemStack tool) {
        ToolStats.Stats stats = tool.get(ForgeweaveDataComponents.TOOL_STATS.get());
        if (stats == null) {
            return List.of();
        }
        int remaining = stats.durability() - tool.getDamageValue();
        return List.of(
                stat("durability", FORMAT.format(remaining) + "/" + FORMAT.format(stats.durability())),
                stat("mining_speed", FORMAT.format(stats.miningSpeed())),
                stat("attack_damage", FORMAT.format(stats.attackDamage())));
    }

    /** The three materials an assembled tool is made of, each in its own tint. */
    public static List<Component> toolMaterials(@Nullable HolderLookup.Provider registries, ToolMaterials materials) {
        return List.of(
                MaterialDisplay.name(registries, materials.head()),
                MaterialDisplay.name(registries, materials.binding()),
                MaterialDisplay.name(registries, materials.handle()));
    }

    /** What one material contributes, whichever part it ends up in (the Part Builder's info panel). */
    public static List<Component> materialStats(Material material) {
        return List.of(
                stat("durability", FORMAT.format(material.head().durability())),
                stat("mining_speed", FORMAT.format(material.head().miningSpeed())),
                stat("attack_damage", FORMAT.format(material.head().attackDamage())),
                stat("handle_modifier", FORMAT.format(material.handle().durabilityModifier())),
                stat("handle_durability", FORMAT.format(material.handle().durability())),
                stat("extra_durability", FORMAT.format(material.extraDurability())));
    }

    /** Each trait's name followed by its description, with a blank line between entries. */
    public static List<Component> traits(List<ResourceLocation> traitIds) {
        List<Component> lines = new ArrayList<>();
        for (ResourceLocation id : traitIds) {
            String base = "trait." + id.getNamespace() + "." + id.getPath();
            lines.add(Component.translatable(base + ".name").withStyle(ChatFormatting.WHITE));
            lines.add(Component.translatable(base + ".description").withStyle(ChatFormatting.GRAY));
            lines.add(null); // spacer
        }
        if (!lines.isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /** The trait ids stored on an assembled tool, or an empty list for anything else. */
    public static List<ResourceLocation> traitIdsOf(ItemStack stack) {
        List<ResourceLocation> ids = stack.get(ForgeweaveDataComponents.TRAITS.get());
        return ids == null ? List.of() : ids;
    }

    private static Component stat(String key, String value) {
        return Component.translatable("gui.forgeweave.stat." + key, value);
    }

    private StationText() {}
}
