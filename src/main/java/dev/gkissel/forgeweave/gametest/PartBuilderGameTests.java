package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;

/**
 * Covers docs/SCOPE.md M1 issue #9's verification: pattern + material -> correct part item.
 * Exercises the real {@link PartBuilderMenu} crafting resolution (not a duplicate of its logic).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class PartBuilderGameTests {

    @GameTest(template = "empty")
    public static void patternAndMaterialProduceMatchingPart(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());

        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        // Stone's repair_item is #minecraft:stone_tool_materials (cobblestone/blackstone); a head
        // costs 2 material items (PartBuilderRecipes).
        blockEntity.container().setItem(1, new ItemStack(Items.COBBLESTONE, 2));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(2).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected a pickaxe head part, got " + output);
        helper.assertTrue(
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "stone")
                        .equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the pickaxe head's material to be forgeweave:stone, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));

        helper.succeed();
    }
}
