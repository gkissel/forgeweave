package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
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
 * durability (25) = 160.
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
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(3).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected a pickaxe, got " + output);

        ToolMaterials materials = output.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        helper.assertTrue(new ToolMaterials(stone, wood, wood).equals(materials),
                "expected head=stone binding=wood handle=wood, got " + materials);

        Integer maxDamage = output.get(DataComponents.MAX_DAMAGE);
        helper.assertTrue(maxDamage != null && maxDamage == 160,
                "expected max durability 160 ((120 + 15) * 1.0 + 25), got " + maxDamage);

        // Simulates taking the crafted tool: all three parts are consumed (unlike the Part Builder's
        // reusable pattern, there's nothing here that survives the craft).
        menu.getSlot(3).onTake(player, output);
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "expected the head part to be consumed");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "expected the binding part to be consumed");
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "expected the handle part to be consumed");

        helper.succeed();
    }

    /**
     * docs/SCOPE.md M1 issue #11's repair verification. The pickaxe above (durability pool 160, stone
     * head) is broken outright, then repaired with a single cobblestone -- stone's {@code repair_item}
     * is {@code #minecraft:stone_tool_materials}.
     *
     * <p>One repair item is worth the head material's head durability, so 120 of the 159 damage comes
     * off and the tool lands at 39 damage, unbroken and usable again ({@code ToolRepair}, ported from
     * upstream 1.12's {@code TinkersItem#calculateRepairAmount}/{@code #calculateRepair}).
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

        ItemStack repaired = menu.getSlot(3).getItem();
        helper.assertTrue(repaired.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected the repaired pickaxe, got " + repaired);
        helper.assertFalse(ToolItem.isBroken(repaired), "repair must clear the Broken state");
        helper.assertTrue(repaired.getDamageValue() == 39,
                "expected 159 - 120 = 39 damage left after one cobblestone, got " + repaired.getDamageValue());
        helper.assertTrue(repaired.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()),
                "a repaired tool should harvest again");
        helper.assertTrue(new ToolMaterials(
                        ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "stone"),
                        ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood"),
                        ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood"))
                        .equals(repaired.get(ForgeweaveDataComponents.TOOL_MATERIALS.get())),
                "repair must not disturb the tool's materials");

        menu.getSlot(3).onTake(player, repaired);
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "expected the repaired tool to leave the input slot");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "expected the one cobblestone to be consumed");

        helper.succeed();
    }
}
