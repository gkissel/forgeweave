package dev.gkissel.forgeweave.menu;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * The Tool Station's assembly table: which head part builds which tool. Binding and handle are
 * shared across every M1 tool -- upstream 1.12's pickaxe/shovel/hatchet all use
 * {@code TinkerTools.binding}/{@code toolRod} for those two slots (see each tool class's
 * constructor, e.g. {@code tools/tools/Pickaxe.java}: {@code PartMaterialType.handle(toolRod)},
 * {@code .head(pickHead)}, {@code .extra(binding)}), so only the head part determines which tool
 * comes out. NOTICE.md cites {@code ToolBuilder.java}/the tool classes for this part composition,
 * and {@code ToolNBT.java} for the stat math in {@code ToolStats}.
 */
final class ToolAssemblyRecipes {
    private record Entry(Supplier<? extends PartItem> headPart, Supplier<? extends ToolItem> tool) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry(ForgeweaveItems.PART_PICKAXE_HEAD, ForgeweaveItems.TOOL_PICKAXE),
            new Entry(ForgeweaveItems.PART_SHOVEL_HEAD, ForgeweaveItems.TOOL_SHOVEL),
            new Entry(ForgeweaveItems.PART_AXE_HEAD, ForgeweaveItems.TOOL_HATCHET));

    static boolean isHeadPart(ItemStack stack) {
        return ENTRIES.stream().anyMatch(entry -> stack.is(entry.headPart().get()));
    }

    static boolean isBindingPart(ItemStack stack) {
        return stack.is(ForgeweaveItems.PART_TOOL_BINDING.get());
    }

    static boolean isHandlePart(ItemStack stack) {
        return stack.is(ForgeweaveItems.PART_TOOL_HANDLE.get());
    }

    /**
     * Resolves the tool the Tool Station should produce for the current head/binding/handle
     * slots, or empty if any slot is missing/wrong/has no material. Assembly-only for M1: repair
     * (issue #11) is a separate resolve path CONTEXT.md assigns to this same station, added
     * alongside this method rather than by restructuring it.
     */
    static Optional<ItemStack> resolve(HolderLookup.Provider registries, ItemStack headStack, ItemStack bindingStack, ItemStack handleStack) {
        if (!isBindingPart(bindingStack) || !isHandlePart(handleStack)) {
            return Optional.empty();
        }
        Optional<Entry> entry = ENTRIES.stream().filter(candidate -> headStack.is(candidate.headPart().get())).findFirst();
        if (entry.isEmpty()) {
            return Optional.empty();
        }

        ResourceLocation headId = headStack.get(ForgeweaveDataComponents.MATERIAL.get());
        ResourceLocation bindingId = bindingStack.get(ForgeweaveDataComponents.MATERIAL.get());
        ResourceLocation handleId = handleStack.get(ForgeweaveDataComponents.MATERIAL.get());
        if (headId == null || bindingId == null || handleId == null) {
            return Optional.empty();
        }

        Optional<Material> head = lookupMaterial(registries, headId);
        Optional<Material> binding = lookupMaterial(registries, bindingId);
        Optional<Material> handle = lookupMaterial(registries, handleId);
        if (head.isEmpty() || binding.isEmpty() || handle.isEmpty()) {
            return Optional.empty();
        }

        ToolStats.Stats stats = ToolStats.compute(head.get(), binding.get(), handle.get());

        ItemStack result = new ItemStack(entry.get().tool().get());
        result.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(), new ToolMaterials(headId, bindingId, handleId));
        // Sets the vanilla durability bar; issue #11 makes the tool actually consume it while mining.
        result.set(DataComponents.MAX_DAMAGE, stats.durability());
        result.set(DataComponents.DAMAGE, 0);
        return Optional.of(result);
    }

    private static Optional<Material> lookupMaterial(HolderLookup.Provider registries, ResourceLocation id) {
        return registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, id)))
                .map(holder -> holder.value());
    }

    private ToolAssemblyRecipes() {}
}
