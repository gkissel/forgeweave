package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.fluids.SimpleFluidContent;

/**
 * The item form of the seared tank, gauge and window, showing what fluid the block was carrying when
 * it was broken (issue #379). Upstream 1.12's {@code smeltery/item/ItemTank#addInformation}
 * (NOTICE.md) reads the tank NBT its {@code BlockTank#getDrops} put on the stack and prints the
 * fluid's name plus its amount in millibuckets; Forgeweave stores the same thing as the implicit
 * block-entity component {@link ForgeweaveDataComponents#FLUID_CONTENT}, which the tank loot table
 * copies onto the drop ({@code ForgeweaveBlockLootSubProvider#tankDrop}), so the lines come from
 * there instead of raw NBT.
 */
public class SearedTankItem extends BlockItem {

    public SearedTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        SimpleFluidContent content = stack.get(ForgeweaveDataComponents.FLUID_CONTENT.get());
        if (content == null || content.isEmpty()) {
            return;
        }
        tooltip.add(content.copy().getHoverName().copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.forgeweave.tank.amount", content.getAmount())
                .withStyle(ChatFormatting.GRAY));
    }
}
