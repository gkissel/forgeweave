package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.menu.ToolStationTabs;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * Covers docs/SCOPE.md M1 issue #10's verification: stone pickaxe head + wood binding + wood
 * handle parts -> a pickaxe whose stored durability derives from those materials' stats, and whose
 * component lists the three materials used. Plus issue #11's repair half: the same station takes a
 * Broken tool and the head material's repair item back to usable.
 *
 * <p>Expected durability is {@code ToolStats}'s ported 1.12 formula (see its javadoc), computed by
 * hand from the shipped material JSONs: stone's head durability (120) + wood's extra_durability
 * (15, as the binding) = 135; * wood's handle durability_modifier (1.0) = 135; + wood's handle
 * durability (25) = 160; * stone's head trait {@code cheap} carrying upstream's {@code cheapskate}
 * penalty (160 * 80 / 100) = 128.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ToolStationGameTests {

    @GameTest(template = "empty")
    public static void threePartsAssemblePickaxeWithDerivedStats(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.TOOL_STATION.get());

        ResourceLocation stone = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "stone");
        ResourceLocation wood = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood");

        ItemStack head = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());
        head.set(ForgeweaveDataComponents.MATERIAL.get(), stone);
        ItemStack binding = new ItemStack(ForgeweaveItems.PART_TOOL_BINDING.get());
        binding.set(ForgeweaveDataComponents.MATERIAL.get(), wood);
        ItemStack handle = new ItemStack(ForgeweaveItems.PART_TOOL_HANDLE.get());
        handle.set(ForgeweaveDataComponents.MATERIAL.get(), wood);

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, head);
        blockEntity.container().setItem(1, binding);
        blockEntity.container().setItem(2, handle);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolStationMenu menu = new ToolStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory(), blockEntity.isForge());
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected a pickaxe, got " + output);

        ToolMaterials materials = output.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        helper.assertTrue(new ToolMaterials(stone, wood, wood).equals(materials),
                "expected head=stone binding=wood handle=wood, got " + materials);

        Integer maxDamage = output.get(DataComponents.MAX_DAMAGE);
        helper.assertTrue(maxDamage != null && maxDamage == 128,
                "expected max durability 128 (((120 + 15) * 1.0 + 25) * 80 / 100), got " + maxDamage);

        // Simulates taking the crafted tool: all three parts are consumed (unlike the Part Builder's
        // reusable pattern, there's nothing here that survives the craft).
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "expected the head part to be consumed");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "expected the binding part to be consumed");
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "expected the handle part to be consumed");

        helper.succeed();
    }

    /**
     * docs/SCOPE.md M1 issue #11's repair verification. The pickaxe above (durability pool 128, stone
     * head) is broken outright, then repaired with a single cobblestone -- stone's {@code repair_item}
     * is {@code #minecraft:stone_tool_materials}.
     *
     * <p>One repair item is worth the head material's head durability, so 120 of the 127 damage comes
     * off ({@code ToolRepair}, ported from upstream 1.12's
     * {@code TinkersItem#calculateRepairAmount}/{@code #calculateRepair}), plus the 5% stone's
     * {@code forgeweave:cheap} trait adds -- 126 in all, leaving the tool at 1 damage, unbroken and
     * usable again. The trait's own test is {@link TraitGameTests#cheapRepairsMoreThanTheBaseAmount}.
     */
    @GameTest(template = "empty")
    public static void repairRestoresDurabilityAndClearsBroken(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.hurtAndBreak(1_000, helper.getLevel(), player, brokenItem -> {});
        helper.assertTrue(ToolItem.isBroken(pickaxe), "the test needs a Broken tool to repair");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.COBBLESTONE, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(repaired.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected the repaired pickaxe, got " + repaired);
        helper.assertFalse(ToolItem.isBroken(repaired), "repair must clear the Broken state");
        helper.assertTrue(repaired.getDamageValue() == 1,
                "expected 127 - (120 + cheap's 5%) = 1 damage left after one cobblestone, got "
                        + repaired.getDamageValue());
        helper.assertTrue(repaired.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()),
                "a repaired tool should harvest again");
        helper.assertTrue(new ToolMaterials(
                        ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "stone"),
                        ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood"),
                        ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood"))
                        .equals(repaired.get(ForgeweaveDataComponents.TOOL_MATERIALS.get())),
                "repair must not disturb the tool's materials");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, repaired);
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "expected the repaired tool to leave the input slot");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "expected the one cobblestone to be consumed");

        helper.succeed();
    }

    /**
     * Issue #47's tab-based menu: selecting a tool's tab through the vanilla menu-button path moves
     * the three input slots to that tool's layout, narrows what each one accepts to that tool's parts,
     * and still assembles the same tool the pre-tab menu did.
     */
    @GameTest(template = "empty")
    public static void pickaxeTabRepositionsSlotsAndStillAssembles(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.TOOL_STATION.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);

        helper.assertTrue(menu.getSelectedTab() == ToolStationTabs.REPAIR,
                "a freshly opened station starts on the repair tab, as upstream's does");

        int pickaxeTab = ToolStationTabs.TABS.indexOf(ToolStationTabs.TABS.stream()
                .filter(tab -> !tab.isRepair() && tab.tool().get() == ForgeweaveItems.TOOL_PICKAXE.get())
                .findFirst().orElseThrow());
        helper.assertTrue(menu.clickMenuButton(player, pickaxeTab), "selecting the pickaxe tab must be accepted");
        helper.assertFalse(menu.clickMenuButton(player, ToolStationTabs.TABS.size()),
                "an out-of-range tab index must be rejected server-side");

        ToolStationTabs.Pos headPos = ToolStationTabs.get(pickaxeTab).slots().get(ToolStationMenu.HEAD_SLOT);
        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).x == headPos.x()
                        && menu.getSlot(ToolStationMenu.HEAD_SLOT).y == headPos.y(),
                "the head slot must move to the pickaxe layout's position");

        ItemStack head = ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "stone");
        ItemStack wrongHead = ToolAssembly.part(ForgeweaveItems.PART_SHOVEL_HEAD.get(), "stone");
        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).mayPlace(head),
                "the pickaxe tab's head slot must accept a pickaxe head");
        helper.assertFalse(menu.getSlot(ToolStationMenu.HEAD_SLOT).mayPlace(wrongHead),
                "the pickaxe tab's head slot must reject another tool's head");

        menu.getSlot(ToolStationMenu.HEAD_SLOT).set(head);
        menu.getSlot(ToolStationMenu.BINDING_SLOT).set(ToolAssembly.part(ForgeweaveItems.PART_TOOL_BINDING.get(), "wood"));
        menu.getSlot(ToolStationMenu.HANDLE_SLOT).set(ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "wood"));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.TOOL_PICKAXE.get()),
                "assembly through the pickaxe tab must still produce a pickaxe, got " + output);
        helper.succeed();
    }

    /**
     * The repair tab is the same three slots with the other filter: a tool in the middle and its head
     * material's repair item beside it (issue #47), reaching the unchanged repair path from #11.
     */
    @GameTest(template = "empty")
    public static void repairTabAcceptsToolsAndTheirRepairItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.hurtAndBreak(1_000, helper.getLevel(), player, brokenItem -> {});

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        helper.assertTrue(menu.clickMenuButton(player, ToolStationTabs.REPAIR), "the repair tab must be selectable");

        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).mayPlace(pickaxe),
                "the repair tab's first slot must accept an assembled tool");
        helper.assertFalse(menu.getSlot(ToolStationMenu.HEAD_SLOT)
                        .mayPlace(ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "stone")),
                "the repair tab's first slot must reject loose parts");

        menu.getSlot(ToolStationMenu.HEAD_SLOT).set(pickaxe);
        helper.assertTrue(menu.getSlot(ToolStationMenu.BINDING_SLOT).mayPlace(new ItemStack(Items.COBBLESTONE)),
                "with a stone-headed tool loaded, the repair slots must accept cobblestone");
        // Dirt, not diamond (issue #106): diamond became a valid modifier reagent (forgeweave:diamond).
        helper.assertFalse(menu.getSlot(ToolStationMenu.BINDING_SLOT).mayPlace(new ItemStack(Items.DIRT)),
                "the repair slots must reject an item that is not the head material's repair item");

        menu.getSlot(ToolStationMenu.BINDING_SLOT).set(new ItemStack(Items.COBBLESTONE, 1));
        menu.broadcastChanges();
        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(repaired.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected the repaired pickaxe, got " + repaired);
        helper.assertFalse(ToolItem.isBroken(repaired), "repair through the repair tab must clear the Broken state");
        helper.succeed();
    }

    /**
     * The rename field (issue #47) reaches the output stack server-side, exactly like a vanilla anvil,
     * and the menu -- not the client -- is what filters and length-caps the text.
     */
    @GameTest(template = "empty")
    public static void renamingTheOutputSetsItsCustomName(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.TOOL_STATION.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "stone"));
        blockEntity.container().setItem(1, ToolAssembly.part(ForgeweaveItems.PART_TOOL_BINDING.get(), "wood"));
        blockEntity.container().setItem(2, ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "wood"));

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.setToolName("Rockbiter");

        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        Component name = output.get(DataComponents.CUSTOM_NAME);
        helper.assertTrue(name != null && "Rockbiter".equals(name.getString()),
                "expected the output to be renamed, got " + name);

        menu.setToolName("");
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().get(DataComponents.CUSTOM_NAME) == null,
                "clearing the field must leave the output with its default name");
        helper.succeed();
    }

    /**
     * Issue #40's follow-up: the side-inventory panel extended from the Crafting Station to the Tool
     * Station (and the Part Builder, {@code PartBuilderGameTests}'s own coverage isn't duplicated
     * here). Same shape as {@code CraftingStationGameTests#adjacentChestInventoryIsExposedThroughTheMenu}.
     */
    @GameTest(template = "empty")
    public static void adjacentChestInventoryIsExposedThroughTheMenu(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = stationPos.east();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(stationPos, ForgeweaveBlocks.TOOL_STATION.get());

        if (!(helper.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            helper.fail("expected a chest block entity next to the station");
            return;
        }
        chest.setItem(0, new ItemStack(Items.DIAMOND));

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(stationPos);
        ToolStationMenu menu = new ToolStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(stationPos)), blockEntity.findSideInventory(), blockEntity.isForge());

        helper.assertTrue(menu.sideInventorySlotCount > 0, "expected the adjacent chest to be detected as a side inventory");

        boolean foundDiamond = false;
        for (int i = ToolStationMenu.CONTAINER_SLOTS; i < ToolStationMenu.CONTAINER_SLOTS + menu.sideInventorySlotCount; i++) {
            if (menu.getSlot(i).getItem().is(Items.DIAMOND)) {
                foundDiamond = true;
                break;
            }
        }
        helper.assertTrue(foundDiamond, "expected the chest's diamond to be visible through a side-inventory slot");

        helper.succeed();
    }
}
