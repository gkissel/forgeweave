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
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * An assembled tool (pickaxe, shovel, hatchet -- CONTEXT.md glossary). Carries the three materials
 * it was built from as a {@link ForgeweaveDataComponents#TOOL_MATERIALS} component and lists them
 * in its tooltip, the same approach {@link PartItem} uses for its single material.
 *
 * <p>Plain {@code Item}, not {@code DiggerItem}/{@code TieredItem}: mining behavior, combat, and
 * repair are docs/SCOPE.md issue #11 (a different agent). This class only carries data and shows
 * it; durability is set on the vanilla max-damage component at assembly time
 * ({@code ToolAssemblyRecipes}), which is enough for the durability bar to render, but nothing here
 * reacts to damage yet.
 */
public class ToolItem extends Item {
    public ToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null) {
            return;
        }
        tooltip.add(materialName(context.registries(), materials.head()));
        tooltip.add(materialName(context.registries(), materials.binding()));
        tooltip.add(materialName(context.registries(), materials.handle()));
    }

    private static MutableComponent materialName(HolderLookup.Provider registries, ResourceLocation materialId) {
        MutableComponent name =
                Component.translatable("material." + materialId.getNamespace() + "." + materialId.getPath());
        TextColor color = lookupColor(registries, materialId);
        return color != null ? name.withStyle(Style.EMPTY.withColor(color)) : name;
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
