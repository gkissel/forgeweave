package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import dev.gkissel.forgeweave.material.Material;

/**
 * A tool part item (pickaxe head, tool binding, ...). Carries the material it was crafted from as
 * a {@link ForgeweaveDataComponents#MATERIAL} data component and shows that material's name in its
 * tooltip. Per-material rendering is a client-side color tint (see {@code ForgeweaveItemColors}),
 * not a distinct texture per material.
 */
public class PartItem extends Item {
    public PartItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ResourceLocation materialId = stack.get(ForgeweaveDataComponents.MATERIAL.get());
        if (materialId == null) {
            return;
        }

        MutableComponent name =
                Component.translatable("material." + materialId.getNamespace() + "." + materialId.getPath());
        TextColor color = lookupColor(context.registries(), materialId);
        if (color != null) {
            name = name.withStyle(Style.EMPTY.withColor(color));
        }
        tooltip.add(name);
    }

    private static TextColor lookupColor(HolderLookup.Provider registries, ResourceLocation materialId) {
        if (registries == null) {
            return null;
        }
        return registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, materialId)))
                .map(holder -> holder.value().color())
                .orElse(null);
    }
}
