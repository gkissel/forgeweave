package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.material.Material;

/**
 * The single Forgeweave creative tab. Materials are a datapack registry (ADR-0002), so the part
 * item variants are enumerated at display time from the registry access the display-items event
 * provides, not fixed at registration time.
 */
public final class ForgeweaveCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Forgeweave.MODID);

    private static final List<DeferredItem<PartItem>> PART_ITEMS = List.of(
            ForgeweaveItems.PART_PICKAXE_HEAD,
            ForgeweaveItems.PART_SHOVEL_HEAD,
            ForgeweaveItems.PART_AXE_HEAD,
            ForgeweaveItems.PART_TOOL_BINDING,
            ForgeweaveItems.PART_TOOL_HANDLE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.forgeweave"))
            .icon(() -> new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()))
            .displayItems(ForgeweaveCreativeTab::addDisplayItems)
            .build());

    private static void addDisplayItems(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        output.accept(ForgeweaveItems.PART_BUILDER.get());
        output.accept(ForgeweaveItems.PATTERN_BLANK.get());
        output.accept(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_SHOVEL_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_AXE_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_TOOL_BINDING.get());
        output.accept(ForgeweaveItems.PATTERN_TOOL_HANDLE.get());

        List<Holder.Reference<Material>> materials =
                parameters.holders().lookupOrThrow(Material.REGISTRY).listElements().toList();
        for (DeferredItem<PartItem> partItem : PART_ITEMS) {
            for (Holder.Reference<Material> material : materials) {
                ItemStack stack = new ItemStack(partItem.get());
                stack.set(ForgeweaveDataComponents.MATERIAL.get(), material.key().location());
                output.accept(stack);
            }
        }
    }

    private ForgeweaveCreativeTab() {}
}
