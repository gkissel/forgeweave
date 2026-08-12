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
        return tool(helper, player, pos, ForgeweaveItems.PART_PICKAXE_HEAD.get(), headMaterial, bindingMaterial, handleMaterial);
    }

    /**
     * As {@link #pickaxe}, for whichever tool the given head part assembles into
     * ({@code ToolAssemblyRecipes} maps pickaxe/shovel/axe head to pickaxe/shovel/hatchet).
     */
    static ItemStack tool(GameTestHelper helper, Player player, BlockPos pos, Item headPart, String headMaterial, String bindingMaterial, String handleMaterial) {
        return toolAt(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), headPart, headMaterial, bindingMaterial, handleMaterial);
    }

    /**
     * As {@link #tool}, but assembled at a Tool Forge. For tests whose tool is caught by issue
     * #152's large-tool gate fixture (the GameTest datapack marks the hatchet as a synthetic large
     * tool, which the Tool Station rightly refuses); the Forge assembles everything. Repair-math
     * tests must NOT use this -- the Forge's 5% repair discount would shift their expectations.
     */
    static ItemStack toolAtForge(GameTestHelper helper, Player player, BlockPos pos, Item headPart, String headMaterial, String bindingMaterial, String handleMaterial) {
        return toolAt(helper, player, pos, ForgeweaveBlocks.TOOL_FORGE.get(), headPart, headMaterial, bindingMaterial, handleMaterial);
    }

    private static ItemStack toolAt(GameTestHelper helper, Player player, BlockPos pos, net.minecraft.world.level.block.Block station, Item headPart, String headMaterial, String bindingMaterial, String handleMaterial) {
        helper.setBlock(pos, station);
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, part(headPart, headMaterial));
        blockEntity.container().setItem(1, part(ForgeweaveItems.PART_TOOL_BINDING.get(), bindingMaterial));
        blockEntity.container().setItem(2, part(ForgeweaveItems.PART_TOOL_HANDLE.get(), handleMaterial));

        ToolStationMenu menu = menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack tool = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, tool);
        return tool;
    }

    /** The menu the block at {@code pos} would open -- Tool Station or Tool Forge, per the block entity. */
    static ToolStationMenu menu(GameTestHelper helper, Player player, BlockPos pos, ToolStationBlockEntity blockEntity) {
        return new ToolStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)),
                blockEntity.findSideInventory(), blockEntity.isForge());
    }

    static ItemStack part(Item item, String material) {
        ItemStack stack = new ItemStack(item);
        stack.set(ForgeweaveDataComponents.MATERIAL.get(),
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, material));
        return stack;
    }

    private ToolAssembly() {}
}
