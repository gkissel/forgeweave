package dev.gkissel.forgeweave.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
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
        event.register(ForgeweaveItemColors::materialTint,
                ForgeweaveItems.PART_PICKAXE_HEAD.get(),
                ForgeweaveItems.PART_SHOVEL_HEAD.get(),
                ForgeweaveItems.PART_AXE_HEAD.get(),
                ForgeweaveItems.PART_TOOL_BINDING.get(),
                ForgeweaveItems.PART_TOOL_HANDLE.get());

        event.register(ForgeweaveItemColors::toolMaterialTint,
                ForgeweaveItems.TOOL_PICKAXE.get(),
                ForgeweaveItems.TOOL_SHOVEL.get(),
                ForgeweaveItems.TOOL_HATCHET.get());
    }

    private static int materialTint(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) {
            return -1;
        }
        return opaqueMaterialColor(stack.get(ForgeweaveDataComponents.MATERIAL.get()));
    }

    // Layer0 = head texture (tintIndex 0), layer1 = binding (1), layer2 = handle (2) -- matches
    // ForgeweaveItemModelProvider's tool item models and ToolMaterials's field order.
    private static int toolMaterialTint(ItemStack stack, int tintIndex) {
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null) {
            return -1;
        }
        ResourceLocation materialId = switch (tintIndex) {
            case 0 -> materials.head();
            case 1 -> materials.binding();
            case 2 -> materials.handle();
            default -> null;
        };
        return opaqueMaterialColor(materialId);
    }

    private static int opaqueMaterialColor(ResourceLocation materialId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (materialId == null || level == null) {
            return -1;
        }
        Material material = level.registryAccess().registryOrThrow(Material.REGISTRY).get(materialId);
        // Material.color() (TextColor) is a bare 0xRRGGBB with alpha 0; ItemColor needs opaque ARGB
        // or the tinted layer renders fully transparent. Same fixup vanilla uses for potion/spawn-egg
        // item colors (see ItemColors#register uses of FastColor.ARGB32.opaque).
        return material != null ? FastColor.ARGB32.opaque(material.color().getValue()) : -1;
    }

    private ForgeweaveItemColors() {}
}
