package dev.gkissel.forgeweave.gametest;

import java.util.List;


import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.tool.ToolConstants;

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
     * As {@link #pickaxe}, for whichever M1 tool the given head part assembles into -- those three
     * all take the same (head, binding, handle) slots.
     */
    static ItemStack tool(GameTestHelper helper, Player player, BlockPos pos, Item headPart, String headMaterial, String bindingMaterial, String handleMaterial) {
        return assemble(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), entryTaking(headPart),
                List.of(headMaterial, bindingMaterial, handleMaterial));
    }

    /**
     * As {@link #tool}, but assembled at a Tool Forge. For tests whose tool is caught by issue
     * #152's large-tool gate; the Forge assembles everything. Repair-math tests must NOT use this --
     * the Forge's 5% repair discount would shift their expectations.
     */
    static ItemStack toolAtForge(GameTestHelper helper, Player player, BlockPos pos, Item headPart, String headMaterial, String bindingMaterial, String handleMaterial) {
        return assemble(helper, player, pos, ForgeweaveBlocks.TOOL_FORGE.get(), entryTaking(headPart),
                List.of(headMaterial, bindingMaterial, handleMaterial));
    }

    /**
     * Assembles {@code entry}'s tool at the Tool Station from one material name per part slot, in the
     * entry's own slot order -- the M3 shape (issue #155), where both order and count are per-tool.
     */
    static ItemStack assemble(GameTestHelper helper, Player player, BlockPos pos,
            ToolAssemblyRecipes.Entry entry, List<String> materials) {
        return assemble(helper, player, pos, ForgeweaveBlocks.TOOL_STATION.get(), entry, materials);
    }

    /**
     * As {@link #assemble(GameTestHelper, Player, BlockPos, ToolAssemblyRecipes.Entry, List)}, at a
     * Tool Forge -- the only station that will build a large tool at all (issue #152's gate, which
     * #161's warmace is the first real member of).
     */
    static ItemStack assembleAtForge(GameTestHelper helper, Player player, BlockPos pos,
            ToolAssemblyRecipes.Entry entry, List<String> materials) {
        return assemble(helper, player, pos, ForgeweaveBlocks.TOOL_FORGE.get(), entry, materials);
    }

    /** The station table's row built from {@code constants} -- armor rows have no {@code ToolItem} to look up by (#678). */
    static ToolAssemblyRecipes.Entry entryOf(ToolConstants.Entry constants) {
        return ToolAssemblyRecipes.ENTRIES.stream()
                .filter(entry -> entry.constants() == constants)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no station entry builds " + constants.id()));
    }

    static ItemStack assembleAt(GameTestHelper helper, Player player, BlockPos pos, Block station,
            ToolAssemblyRecipes.Entry entry, List<String> materials) {
        return assemble(helper, player, pos, station, entry, materials);
    }

    private static ItemStack assemble(GameTestHelper helper, Player player, BlockPos pos, Block station,
            ToolAssemblyRecipes.Entry entry, List<String> materials) {
        helper.setBlock(pos, station);
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            blockEntity.container().setItem(i,
                    i < entry.slotCount() ? part(entry.part(i), materials.get(i)) : ItemStack.EMPTY);
        }

        ToolStationMenu menu = menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack tool = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, tool);
        return tool;
    }

    /** The station entry that builds {@code tool}. */
    static ToolAssemblyRecipes.Entry entryFor(ToolItem tool) {
        return ToolAssemblyRecipes.entryFor(new ItemStack(tool))
                .orElseThrow(() -> new AssertionError("no Tool Station entry builds " + tool));
    }

    /** The M1 pickaxe's part slots, for a test asserting the shape of its {@code ToolMaterials}. */
    static List<ToolConstants.PartSlot> pickaxeSlots() {
        return entryTaking(ForgeweaveItems.PART_PICKAXE_HEAD.get()).constants().parts();
    }

    private static ToolAssemblyRecipes.Entry entryTaking(Item headPart) {
        return ToolAssemblyRecipes.ENTRIES.stream()
                .filter(candidate -> candidate.slotCount() == 3 && candidate.part(0) == headPart)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no M1-shaped tool takes " + headPart + " in its first slot"));
    }

    /** The menu the block at {@code pos} would open -- Tool Station or Tool Forge, per the block entity. */
    static ToolStationMenu menu(GameTestHelper helper, Player player, BlockPos pos, ToolStationBlockEntity blockEntity) {
        return new ToolStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)),
                blockEntity.findSideInventory(), blockEntity.isForge(), blockEntity.isArmorStation());
    }

    static ItemStack part(Item item, String material) {
        ItemStack stack = new ItemStack(item);
        stack.set(ForgeweaveDataComponents.MATERIAL.get(),
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, material));
        return stack;
    }

    private ToolAssembly() {}
}
