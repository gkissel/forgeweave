package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;

/**
 * Builds tools for the GameTests the way a player would: through a real Tool Station and its real
 * menu. Nothing here constructs an assembled tool by hand, so a behavior test that passes is also
 * evidence the Tool Station wrote the components that behavior needs.
 */
final class ToolAssembly {

    /** Places a Tool Station at {@code pos} and returns the pickaxe it assembles from three materials. */
    static ItemStack pickaxe(GameTestHelper helper, Player player, BlockPos pos, String headMaterial, String bindingMaterial, String handleMaterial) {
        helper.setBlock(pos, ForgeweaveBlocks.TOOL_STATION.get());
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), headMaterial));
        blockEntity.container().setItem(1, part(ForgeweaveItems.PART_TOOL_BINDING.get(), bindingMaterial));
        blockEntity.container().setItem(2, part(ForgeweaveItems.PART_TOOL_HANDLE.get(), handleMaterial));

        ToolStationMenu menu = menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack tool = menu.getSlot(3).getItem().copy();
        menu.getSlot(3).onTake(player, tool);
        return tool;
    }

    static ToolStationMenu menu(GameTestHelper helper, Player player, BlockPos pos, ToolStationBlockEntity blockEntity) {
        return new ToolStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
    }

    static ItemStack part(Item item, String material) {
        ItemStack stack = new ItemStack(item);
        stack.set(ForgeweaveDataComponents.MATERIAL.get(),
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, material));
        return stack;
    }

    private ToolAssembly() {}
}
