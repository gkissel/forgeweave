package dev.gkissel.forgeweave.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolArt;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * Tints part items with their material's color (ADR-0002). This is the "greyscale texture + tint"
 * approach 1.12 Tinkers used for per-material rendering, rather than a distinct texture per part
 * per material. Assembled tools reuse the same idea across three layers -- see
 * {@link #toolMaterialTint} -- per docs/SCOPE.md issue #10 ("reuse the existing greyscale parts +
 * ItemColor layered tint approach").
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveItemColors {

    @SubscribeEvent
    static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(ForgeweaveItemColors::materialTint, tintedPartItems());
        event.register(ForgeweaveItemColors::toolMaterialTint, tintedToolItems());
    }

    /**
     * Every item {@link #materialTint} tints: every registered {@link PartItem} (the shard included
     * -- it is one), straight off the item registry. This used to be a hand list, which is exactly
     * how #159/#160's curved blade and katana blade shipped rendering white (issue #256): the two
     * newest parts were never added to it. Derived, a new part item inherits its tint by being
     * registered, the same way {@link #tintedToolItems} already worked. Package-private so
     * {@code ItemColorCoverageTest} can pin the coverage.
     */
    static Item[] tintedPartItems() {
        return ForgeweaveItems.ITEMS.getEntries().stream()
                .<Item>map(DeferredHolder::get)
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

    private static int materialTint(ItemStack stack, int tintIndex) {
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
    private static int toolMaterialTint(ItemStack stack, int tintIndex) {
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

    private static int opaqueMaterialColor(ResourceLocation materialId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (materialId == null || level == null) {
            return -1;
        }
        Material material = level.registryAccess().registryOrThrow(Material.REGISTRY).get(materialId);
        return material != null ? opaqueColor(material.color().getValue()) : -1;
    }

    /**
     * {@code Material.color()} (TextColor) is a bare 0xRRGGBB with alpha 0; ItemColor needs opaque
     * ARGB or the tinted layer renders fully transparent (#8). Same fixup vanilla uses for
     * potion/spawn-egg item colors (see {@code ItemColors#register} uses of {@code
     * FastColor.ARGB32.opaque}). Split out from {@link #opaqueMaterialColor} so it can be exercised
     * directly by a test without a live {@code ClientLevel}/material registry (#79).
     */
    static int opaqueColor(int rawColor) {
        return FastColor.ARGB32.opaque(rawColor);
    }

    private ForgeweaveItemColors() {}
}
