package dev.gkissel.forgeweave.menu;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;

/**
 * The Part Builder's crafting table: which pattern produces which part, and how many material
 * items it costs. Costs are per-part material-item counts, confirmed against upstream 1.12's
 * ingot-based costs (`TinkerTools#registerToolParts`: heads cost 2 ingots, binding/handle cost 1) --
 * Forgeweave's simpler {@code Material} record has no separate "value" concept (ADR-0002), so a
 * material's cost is just a raw item count. No NOTICE.md row: these are fresh integer constants, not
 * copied text or assets.
 *
 * <p>Material identification is "first datapack material (registry order) whose repair_item
 * ingredient matches the input stack" (CONTEXT.md invariant; ADR-0002), same rule PartItem's
 * tooltip and this menu both rely on.
 */
final class PartBuilderRecipes {
    private static final int HEAD_COST = 2;
    private static final int SMALL_PART_COST = 1;

    private record Entry(Supplier<? extends Item> pattern, Supplier<? extends PartItem> part, int materialCost) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry(ForgeweaveItems.PATTERN_PICKAXE_HEAD, ForgeweaveItems.PART_PICKAXE_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_SHOVEL_HEAD, ForgeweaveItems.PART_SHOVEL_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_AXE_HEAD, ForgeweaveItems.PART_AXE_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_TOOL_BINDING, ForgeweaveItems.PART_TOOL_BINDING, SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_TOOL_HANDLE, ForgeweaveItems.PART_TOOL_HANDLE, SMALL_PART_COST));

    /** Whether the pattern slot should accept this stack at all. */
    static boolean isPattern(ItemStack stack) {
        return findEntry(stack).isPresent();
    }

    private static Optional<Entry> findEntry(ItemStack pattern) {
        return ENTRIES.stream().filter(entry -> pattern.is(entry.pattern().get())).findFirst();
    }

    /** A successful pattern+material match: the crafted part and the material items it costs. */
    record Match(ItemStack result, int materialCost) {}

    /**
     * Resolves what the part builder should produce for the current pattern and material slots, or
     * empty if the pattern is missing/unrecognized or no material matches (not enough of it, or none
     * of the registered materials' repair_item ingredient accepts it).
     */
    static Optional<Match> resolve(HolderLookup.Provider registries, ItemStack pattern, ItemStack material) {
        if (pattern.isEmpty() || material.isEmpty()) {
            return Optional.empty();
        }
        return findEntry(pattern).flatMap(entry -> {
            if (material.getCount() < entry.materialCost()) {
                return Optional.empty();
            }
            return matchMaterial(registries, material).map(materialId -> {
                ItemStack result = new ItemStack(entry.part().get());
                result.set(ForgeweaveDataComponents.MATERIAL.get(), materialId);
                return new Match(result, entry.materialCost());
            });
        });
    }

    private static Optional<ResourceLocation> matchMaterial(HolderLookup.Provider registries, ItemStack material) {
        return registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.listElements()
                        .filter(holder -> holder.value().repairItem().test(material))
                        .findFirst())
                .map(holder -> holder.key().location());
    }

    private PartBuilderRecipes() {}
}
