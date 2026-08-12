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
            ForgeweaveItems.SHARD,
            // M3 roster (docs/SCOPE.md issue #151).
            ForgeweaveItems.PART_SWORD_BLADE,
            ForgeweaveItems.PART_WIDE_GUARD,
            ForgeweaveItems.PART_HAND_GUARD,
            ForgeweaveItems.PART_CROSS_GUARD,
            ForgeweaveItems.PART_SIGN_PLATE,
            ForgeweaveItems.PART_PAN,
            ForgeweaveItems.PART_KNIFE_BLADE,
            ForgeweaveItems.PART_LARGE_SWORD_BLADE,
            ForgeweaveItems.PART_TOUGH_TOOL_ROD,
            ForgeweaveItems.PART_TOUGH_BINDING,
            ForgeweaveItems.PART_LARGE_PLATE,
            ForgeweaveItems.PART_HAMMER_HEAD,
            ForgeweaveItems.PART_EXCAVATOR_HEAD,
            ForgeweaveItems.PART_SCYTHE_HEAD,
            ForgeweaveItems.PART_KAMA_HEAD,
            ForgeweaveItems.PART_BROAD_AXE_HEAD,
            ForgeweaveItems.PART_VEIN_HAMMER_HEAD,
            ForgeweaveItems.PART_WAR_MACE_HEAD);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.forgeweave"))
            .icon(() -> new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()))
            .displayItems(ForgeweaveCreativeTab::addDisplayItems)
            .build());

    // Package-private (not private) so ForgeweaveCreativeTabTest (issue #139) can build the tab's
    // contents directly, without depending on the NeoForge mod event bus being live in unit tests.
    static void addDisplayItems(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        output.accept(ForgeweaveItems.PART_BUILDER.get());
        output.accept(ForgeweaveItems.TOOL_STATION.get());
        output.accept(ForgeweaveItems.TOOL_FORGE.get());
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

        // M3 roster (docs/SCOPE.md issue #151).
        output.accept(ForgeweaveItems.PATTERN_SWORD_BLADE.get());
        output.accept(ForgeweaveItems.PATTERN_WIDE_GUARD.get());
        output.accept(ForgeweaveItems.PATTERN_HAND_GUARD.get());
        output.accept(ForgeweaveItems.PATTERN_CROSS_GUARD.get());
        output.accept(ForgeweaveItems.PATTERN_SIGN_PLATE.get());
        output.accept(ForgeweaveItems.PATTERN_PAN.get());
        output.accept(ForgeweaveItems.PATTERN_KNIFE_BLADE.get());
        output.accept(ForgeweaveItems.PATTERN_LARGE_SWORD_BLADE.get());
        output.accept(ForgeweaveItems.PATTERN_TOUGH_TOOL_ROD.get());
        output.accept(ForgeweaveItems.PATTERN_TOUGH_BINDING.get());
        output.accept(ForgeweaveItems.PATTERN_LARGE_PLATE.get());
        output.accept(ForgeweaveItems.PATTERN_HAMMER_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_EXCAVATOR_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_SCYTHE_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_KAMA_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_BROAD_AXE_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_VEIN_HAMMER_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_WAR_MACE_HEAD.get());

        // Shown component-less (no TOOL_MATERIALS set): unlike parts, a tool has three independent
        // material slots, so there's no small fixed set of "one per material" variants to enumerate
        // -- the creative tab shows the plain (untinted) base tool; assembled variants come from the
        // Tool Station.
        output.accept(ForgeweaveItems.TOOL_PICKAXE.get());
        output.accept(ForgeweaveItems.TOOL_SHOVEL.get());
        output.accept(ForgeweaveItems.TOOL_HATCHET.get());
        // M3 Tool Station weapons (docs/SCOPE.md M3 issue #155), same component-less shape.
        output.accept(ForgeweaveItems.TOOL_BROADSWORD.get());
        output.accept(ForgeweaveItems.TOOL_LONGSWORD.get());
        output.accept(ForgeweaveItems.TOOL_RAPIER.get());
        output.accept(ForgeweaveItems.TOOL_BATTLESIGN.get());
        output.accept(ForgeweaveItems.TOOL_FRYING_PAN.get());
        output.accept(ForgeweaveItems.TOOL_DAGGER.get());
        output.accept(ForgeweaveItems.TOOL_WARMACE.get());

        // Grout (a block since issue #129), seared brick, and the seared brick block family
        // (docs/SCOPE.md M2 issue #93).
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

        // The smeltery multiblock's own blocks (docs/SCOPE.md M2 issue #95; issue #139 fix -- these
        // were never added here, so the maintainer couldn't find the smeltery in the creative tab).
        output.accept(ForgeweaveItems.STANDARD_CORE.get());
        output.accept(ForgeweaveItems.NETHER_CORE.get());
        output.accept(ForgeweaveItems.SEARED_TANK.get());
        output.accept(ForgeweaveItems.SEARED_GAUGE.get());
        output.accept(ForgeweaveItems.SEARED_WINDOW.get());
        output.accept(ForgeweaveItems.SEARED_DRAIN.get());

        // #100 -- casting (docs/SCOPE.md M2 issue #100).
        output.accept(ForgeweaveItems.CASTING_TABLE.get());
        output.accept(ForgeweaveItems.CASTING_BASIN.get());
        output.accept(ForgeweaveItems.FAUCET.get());
        output.accept(ForgeweaveItems.CAST_INGOT.get());
        output.accept(ForgeweaveItems.CAST_NUGGET.get());
        output.accept(ForgeweaveItems.CAST_PICKAXE_HEAD.get());
        output.accept(ForgeweaveItems.CAST_SHOVEL_HEAD.get());
        output.accept(ForgeweaveItems.CAST_AXE_HEAD.get());
        output.accept(ForgeweaveItems.CAST_TOOL_BINDING.get());
        output.accept(ForgeweaveItems.CAST_TOOL_HANDLE.get());

        // #103 -- metal materials (docs/SCOPE.md M2 issue #103): the item forms with no vanilla
        // counterpart, for the four metals that lacked them.
        output.accept(ForgeweaveItems.INGOT_COBALT.get());
        output.accept(ForgeweaveItems.NUGGET_COBALT.get());
        output.accept(ForgeweaveItems.RAW_COBALT.get());
        output.accept(ForgeweaveItems.INGOT_ARDITE.get());
        output.accept(ForgeweaveItems.NUGGET_ARDITE.get());
        output.accept(ForgeweaveItems.RAW_ARDITE.get());
        output.accept(ForgeweaveItems.INGOT_MANYULLYN.get());
        output.accept(ForgeweaveItems.NUGGET_MANYULLYN.get());
        output.accept(ForgeweaveItems.RAW_MANYULLYN.get());
        output.accept(ForgeweaveItems.INGOT_ROSE_GOLD.get());
        output.accept(ForgeweaveItems.NUGGET_ROSE_GOLD.get());
        output.accept(ForgeweaveItems.RAW_ROSE_GOLD.get());

        // #104 -- cobalt + ardite nether ore (docs/SCOPE.md M2 issue #104).
        output.accept(ForgeweaveItems.COBALT_ORE.get());
        output.accept(ForgeweaveItems.ARDITE_ORE.get());

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
