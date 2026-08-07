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
import dev.gkissel.forgeweave.tool.ToolRepair;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Everything the Tool Station can produce from its three input slots. Two recipes share those
 * slots, told apart by what is in the first one:
 *
 * <ul>
 *   <li><b>Assembly</b> -- head part + binding part + handle part. Which head part is used decides
 *       which tool comes out; binding and handle are shared across every M1 tool, because upstream
 *       1.12's pickaxe/shovel/hatchet all use {@code TinkerTools.binding}/{@code toolRod} for those
 *       two slots (see each tool class's constructor, e.g. {@code tools/tools/Pickaxe.java}:
 *       {@code PartMaterialType.handle(toolRod)}, {@code .head(pickHead)}, {@code .extra(binding)}).
 *   <li><b>Repair</b> -- a damaged or Broken tool in the head slot, plus items matching its head
 *       material's {@code repair_item} in the other two. CONTEXT.md puts repair at this station and
 *       makes the head material the one that determines the repair item.
 * </ul>
 *
 * <p>NOTICE.md cites the tool classes for the part composition, {@code ToolNBT.java} for the stat
 * math in {@code ToolStats}, and {@code TinkersItem.java} for the repair math in
 * {@code ToolRepair}.
 */
final class ToolAssemblyRecipes {
    private record Entry(Supplier<? extends PartItem> headPart, Supplier<? extends ToolItem> tool) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry(ForgeweaveItems.PART_PICKAXE_HEAD, ForgeweaveItems.TOOL_PICKAXE),
            new Entry(ForgeweaveItems.PART_SHOVEL_HEAD, ForgeweaveItems.TOOL_SHOVEL),
            new Entry(ForgeweaveItems.PART_AXE_HEAD, ForgeweaveItems.TOOL_HATCHET));

    /**
     * What the station produces and what taking it costs. The per-slot counts are what let the two
     * recipes share one output slot: assembly always spends one of each part, a repair spends the
     * tool plus only as many repair items as it actually consumed.
     */
    record Result(ItemStack output, int headSlotUsed, int bindingSlotUsed, int handleSlotUsed) {}

    static boolean isHeadPart(ItemStack stack) {
        return ENTRIES.stream().anyMatch(entry -> stack.is(entry.headPart().get()));
    }

    static boolean isBindingPart(ItemStack stack) {
        return stack.is(ForgeweaveItems.PART_TOOL_BINDING.get());
    }

    static boolean isHandlePart(ItemStack stack) {
        return stack.is(ForgeweaveItems.PART_TOOL_HANDLE.get());
    }

    /** Whether the head slot holds something the station can work on at all. */
    static boolean isHeadSlotInput(ItemStack stack) {
        return isHeadPart(stack) || stack.getItem() instanceof ToolItem;
    }

    /** Whether {@code stack} repairs the tool currently in the head slot. */
    static boolean isRepairItemFor(HolderLookup.Provider registries, ItemStack headSlotStack, ItemStack stack) {
        return headMaterialOf(registries, headSlotStack)
                .filter(material -> material.repairItem().test(stack))
                .isPresent();
    }

    /**
     * Resolves what the Tool Station should currently produce, or empty if the slots don't form
     * either recipe.
     */
    static Optional<Result> resolve(HolderLookup.Provider registries, ItemStack headStack, ItemStack bindingStack, ItemStack handleStack) {
        return headStack.getItem() instanceof ToolItem
                ? resolveRepair(registries, headStack, bindingStack, handleStack)
                : resolveAssembly(registries, headStack, bindingStack, handleStack);
    }

    private static Optional<Result> resolveAssembly(HolderLookup.Provider registries, ItemStack headStack, ItemStack bindingStack, ItemStack handleStack) {
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

        ToolItem tool = entry.get().tool().get();
        ItemStack result = new ItemStack(tool);
        result.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(), new ToolMaterials(headId, bindingId, handleId));
        result.set(ForgeweaveDataComponents.TOOL_STATS.get(), stats);
        // Mining tier, mining speed, and the durability bar all ride on vanilla components, so
        // vanilla's own block-breaking and rendering paths need no Forgeweave-specific handling.
        result.set(DataComponents.TOOL, tool.toolComponent(head.get(), stats));
        result.set(DataComponents.MAX_DAMAGE, stats.durability());
        result.set(DataComponents.DAMAGE, 0);
        return Optional.of(new Result(result, 1, 1, 1));
    }

    /**
     * Repairs the tool in the head slot with as many matching items as it takes (or as many as are
     * there), spending the binding slot before the handle slot. Every other component -- materials,
     * stats, the vanilla tool component -- rides along untouched on the copy, so a repaired tool is
     * the same tool.
     */
    private static Optional<Result> resolveRepair(HolderLookup.Provider registries, ItemStack toolStack, ItemStack bindingStack, ItemStack handleStack) {
        int damage = toolStack.getDamageValue();
        if (damage <= 0) {
            return Optional.empty(); // undamaged and unbroken: nothing to repair (upstream 1.12 does the same)
        }
        Optional<Material> head = headMaterialOf(registries, toolStack);
        if (head.isEmpty()) {
            return Optional.empty();
        }

        int fromBinding = head.get().repairItem().test(bindingStack) ? bindingStack.getCount() : 0;
        int fromHandle = head.get().repairItem().test(handleStack) ? handleStack.getCount() : 0;
        int available = fromBinding + fromHandle;
        if (available == 0) {
            return Optional.empty();
        }

        int headDurability = head.get().head().durability();
        int maxDamage = toolStack.getMaxDamage();
        int repairCount = toolStack.getOrDefault(ForgeweaveDataComponents.REPAIR_COUNT.get(), 0);
        int used = 0;
        while (damage > 0 && used < available) {
            damage -= ToolRepair.repairIncrement(headDurability, maxDamage, repairCount + used);
            used++;
        }

        ItemStack result = toolStack.copy();
        result.set(DataComponents.DAMAGE, Math.max(0, damage));
        result.set(ForgeweaveDataComponents.REPAIR_COUNT.get(), repairCount + used);
        // Any repair moves the tool off the Broken threshold, since one repair item is always worth
        // at least 1/64 of the durability pool.
        result.remove(ForgeweaveDataComponents.BROKEN.get());
        return Optional.of(new Result(result, 1, Math.min(used, fromBinding), Math.max(0, used - fromBinding)));
    }

    /** The {@code Material} of an assembled tool's head part, which is what a repair needs. */
    private static Optional<Material> headMaterialOf(HolderLookup.Provider registries, ItemStack stack) {
        if (!(stack.getItem() instanceof ToolItem)) {
            return Optional.empty();
        }
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        return materials == null ? Optional.empty() : lookupMaterial(registries, materials.head());
    }

    private static Optional<Material> lookupMaterial(HolderLookup.Provider registries, ResourceLocation id) {
        return registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, id)))
                .map(holder -> holder.value());
    }

    private ToolAssemblyRecipes() {}
}
