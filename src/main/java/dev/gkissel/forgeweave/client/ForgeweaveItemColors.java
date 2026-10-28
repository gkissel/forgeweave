package dev.gkissel.forgeweave.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.Fortification;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolArt;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * Tints part items with their material's color (ADR-0002). This is the "greyscale texture + tint"
 * approach 1.12 Tinkers used for per-material rendering, rather than a distinct texture per part
 * per material. Assembled tools reuse the same idea across three layers -- see
 * {@link #toolMaterialTint} -- per docs/SCOPE.md issue #10 ("reuse the existing greyscale parts +
 * ItemColor layered tint approach").
 *
 * <p>26.1 has no item color handlers: tints come from the client item, so this is now the colour
 * math the {@code forgeweave:part} and {@code forgeweave:tool} item models call per stack. The fluid
 * buckets' tint moved into NeoForge's {@code neoforge:fluid_container} item model itself, and the
 * glass and foliage block items take a {@code minecraft:constant} tint in their generated client
 * items.
 */
public final class ForgeweaveItemColors {

    /** Every fluid bucket (#286), off {@link ForgeweaveFluids#all} rather than a hand list -- same reason as {@link #tintedPartItems}. Package-private so {@code ItemColorCoverageTest} can pin the coverage. */
    static Item[] tintedBucketItems() {
        return ForgeweaveFluids.all().stream().<Item>map(fluid -> fluid.bucket().get()).toArray(Item[]::new);
    }

    /**
     * Every item {@link #materialTint} tints: every registered {@link PartItem} (the shard included
     * -- it is one), straight off the item registry. This used to be a hand list, which is exactly
     * how #159/#160's curved blade and katana blade shipped rendering white (issue #256): the two
     * newest parts were never added to it. Derived, a new part item inherits its tint by being
     * registered, the same way {@link #tintedToolItems} already worked. Package-private so
     * {@code ItemColorCoverageTest} can pin the coverage.
     *
     * <p>Issue #1066 widened the walk from Forgeweave's own {@code DeferredRegister} to the item
     * registry, which by the time colour handlers are registered holds every mod's items. A part
     * another mod registered is a {@link PartItem} carrying a material like any other, and rendering
     * it white was the last thing standing between a registered part and looking right.
     */
    static Item[] tintedPartItems() {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof PartItem)
                .toArray(Item[]::new);
    }

    /**
     * Every item {@link #toolMaterialTint} tints: every assemblable tool, straight off the station's
     * table (issue #155) rather than a hand list that a new tool can be left out of.
     */
    static Item[] tintedToolItems() {
        return ToolAssemblyRecipes.ENTRIES.stream().map(entry -> entry.tool().get()).toArray(Item[]::new);
    }

    /** A part's tint: its material's color on layer 0, untinted elsewhere. */
    public static int materialTint(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) {
            return -1;
        }
        return opaqueMaterialColor(stack.get(ForgeweaveDataComponents.MATERIAL.get()));
    }

    /**
     * Each model layer takes the colour of the part it draws. Which part that is comes from
     * {@link ToolArt#layerSlots} -- the same mapping {@code ForgeweaveItemModelProvider} used to pick
     * that layer's texture, so the two cannot disagree about, say, whether layer2 is a battleaxe's
     * second head or a broadsword's guard (issue #159). Layer order is upstream's own drawing order
     * (handle behind, then heads, then the extra part), which is not every tool's part order.
     */
    public static int toolMaterialTint(ItemStack stack, int tintIndex) {
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null) {
            return -1;
        }
        return ToolAssemblyRecipes.entryFor(stack)
                .map(entry -> ToolArt.layerSlots(entry.constants().parts()))
                // A layer with no part behind it tints nothing; likewise a stack whose materials
                // list is shorter than its entry says (a tool saved before that entry changed).
                .filter(slots -> tintIndex < slots.size() && slots.get(tintIndex) < materials.parts().size())
                .map(slots -> opaqueMaterialColor(materials.parts().get(slots.get(tintIndex))))
                .orElse(-1);
    }

    /**
     * The fortification overlay's color: the material of whichever {@code fortification.<material>}
     * entry {@link Fortification#on} finds, or untinted if the stack carries none -- a tool with no
     * fortification never draws this overlay in the first place ({@link
     * dev.gkissel.forgeweave.tool.ModifierArt#overlay}), so this only runs for a stack that has one.
     */
    public static int fortificationTint(ItemStack stack) {
        ModifierEntry entry = Fortification.on(stack);
        return entry == null ? -1 : opaqueMaterialColor(Fortification.materialOf(entry.id()));
    }

    /** A material's bare 0xRRGGBB from the client level's registry, or -1 when unknown (#733's station preview). */
    public static int materialColor(@Nullable Identifier materialId) {
        int opaque = opaqueMaterialColor(materialId);
        return opaque == -1 ? -1 : opaque & 0xFFFFFF;
    }

    /** The material's opaque ARGB colour, or {@code -1} (untinted white) when there is no such material or no level yet. Shared with the worn armor tint ({@code ForgeweaveItemClientExtensions}, #726). */
    public static int opaqueMaterialColor(@Nullable Identifier materialId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (materialId == null || level == null) {
            return -1;
        }
        Material material = level.registryAccess().lookupOrThrow(Material.REGISTRY).getValue(materialId);
        return material != null ? opaqueColor(material.color().getValue()) : -1;
    }

    /**
     * {@code Material.color()} (TextColor) is a bare 0xRRGGBB with alpha 0; a tint needs opaque
     * ARGB or the tinted layer renders fully transparent (#8). Same fixup vanilla's own tint
     * sources apply ({@code ARGB#opaque}). Split out from {@link #opaqueMaterialColor} so it can be exercised
     * directly by a test without a live {@code ClientLevel}/material registry (#79).
     */
    static int opaqueColor(int rawColor) {
        return ARGB.opaque(rawColor);
    }

    private ForgeweaveItemColors() {}
}
