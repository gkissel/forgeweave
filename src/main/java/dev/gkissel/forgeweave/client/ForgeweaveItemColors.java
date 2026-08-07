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

/**
 * Tints part items with their material's color (ADR-0002). This is the "greyscale texture + tint"
 * approach 1.12 Tinkers used for per-material rendering, rather than a distinct texture per part
 * per material.
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
    }

    private static int materialTint(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) {
            return -1;
        }
        ResourceLocation materialId = stack.get(ForgeweaveDataComponents.MATERIAL.get());
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
