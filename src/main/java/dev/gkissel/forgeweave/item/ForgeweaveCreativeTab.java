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
            ForgeweaveItems.PART_TOOL_HANDLE,
            ForgeweaveItems.SHARD);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.forgeweave"))
            .icon(() -> new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()))
            .displayItems(ForgeweaveCreativeTab::addDisplayItems)
            .build());

    private static void addDisplayItems(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        output.accept(ForgeweaveItems.PART_BUILDER.get());
        output.accept(ForgeweaveItems.TOOL_STATION.get());
        output.accept(ForgeweaveItems.CRAFTING_STATION.get());
        output.accept(ForgeweaveItems.STENCIL_TABLE.get());
        output.accept(ForgeweaveItems.PATTERN_CHEST.get());
        output.accept(ForgeweaveItems.PART_CHEST.get());
        output.accept(ForgeweaveItems.PATTERN_BLANK.get());
        output.accept(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_SHOVEL_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_AXE_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_TOOL_BINDING.get());
        output.accept(ForgeweaveItems.PATTERN_TOOL_HANDLE.get());

        // Shown component-less (no TOOL_MATERIALS set): unlike parts, a tool has three independent
        // material slots, so there's no small fixed set of "one per material" variants to enumerate
        // -- the creative tab shows the plain (untinted) base tool; assembled variants come from the
        // Tool Station.
        output.accept(ForgeweaveItems.TOOL_PICKAXE.get());
        output.accept(ForgeweaveItems.TOOL_SHOVEL.get());
        output.accept(ForgeweaveItems.TOOL_HATCHET.get());

        // Grout, seared brick, and the seared brick block family (docs/SCOPE.md M2 issue #93).
        output.accept(ForgeweaveItems.GROUT.get());
        output.accept(ForgeweaveItems.SEARED_BRICK.get());

        // #107 batch: modifier reagent items (docs/SCOPE.md M2 issue #107).
        output.accept(ForgeweaveItems.MOSS.get());
        output.accept(ForgeweaveItems.MENDING_MOSS.get());
        output.accept(ForgeweaveItems.REINFORCED_PLATE.get());
        output.accept(ForgeweaveItems.SILKY_CLOTH.get());
        output.accept(ForgeweaveItems.SILKY_JEWEL.get());
        output.accept(ForgeweaveItems.EXTRA_MODIFIER.get());
        output.accept(ForgeweaveItems.SEARED_STONE.get());
        output.accept(ForgeweaveItems.SEARED_COBBLESTONE.get());
        output.accept(ForgeweaveItems.SEARED_PAVER.get());
        output.accept(ForgeweaveItems.SEARED_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_CRACKED_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_FANCY_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_SQUARE_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_TRIANGLE_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_SMALL_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_ROAD.get());
        output.accept(ForgeweaveItems.SEARED_TILE.get());
        output.accept(ForgeweaveItems.SEARED_CREEPER.get());

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
