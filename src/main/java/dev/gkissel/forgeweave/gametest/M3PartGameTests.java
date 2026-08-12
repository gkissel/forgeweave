package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

/**
 * Covers docs/SCOPE.md M3 issue #151's verification for the new M3 tool parts: each one is
 * craftable at the Part Builder from its own pattern, with the correct material carried onto the
 * result. Same menu-driven approach as {@link PartBuilderGameTests} -- exercises the real
 * {@link PartBuilderMenu}, not a re-implementation of its resolution logic -- but parameterized
 * over all 17 new (pattern, part, cost) triples in one test instead of 17 near-identical methods,
 * since the only thing that varies between them is which pattern/part pair and what it costs.
 *
 * <p>Every craft below pays with wood shards (1 shard-unit each, {@code PartBuilderRecipes
 * #SHARD_VALUE}) exactly equal to the part's cost, so there is never any shard change to account
 * for -- the change-output path is already covered by {@link PartBuilderGameTests
 * #logProducesHeadAndShardChange}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class M3PartGameTests {

    private record Entry(DeferredItem<? extends Item> pattern, DeferredItem<? extends PartItem> part, int cost) {}

    // Mirrors menu.PartBuilderRecipes#ENTRIES' M3 rows (package-private there); duplicated here the
    // same way jei.PartCraftingRecipes does, since a gametest can't see a menu-package-private table.
    private static final List<Entry> M3_ENTRIES = List.of(
            new Entry(ForgeweaveItems.PATTERN_SWORD_BLADE, ForgeweaveItems.PART_SWORD_BLADE, PartBuilderRecipes.HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_WIDE_GUARD, ForgeweaveItems.PART_WIDE_GUARD, PartBuilderRecipes.SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_HAND_GUARD, ForgeweaveItems.PART_HAND_GUARD, PartBuilderRecipes.SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_CROSS_GUARD, ForgeweaveItems.PART_CROSS_GUARD, PartBuilderRecipes.SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_SIGN_PLATE, ForgeweaveItems.PART_SIGN_PLATE, PartBuilderRecipes.MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_PAN, ForgeweaveItems.PART_PAN, PartBuilderRecipes.MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_KNIFE_BLADE, ForgeweaveItems.PART_KNIFE_BLADE, PartBuilderRecipes.SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_LARGE_SWORD_BLADE, ForgeweaveItems.PART_LARGE_SWORD_BLADE, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_TOUGH_TOOL_ROD, ForgeweaveItems.PART_TOUGH_TOOL_ROD, PartBuilderRecipes.MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_TOUGH_BINDING, ForgeweaveItems.PART_TOUGH_BINDING, PartBuilderRecipes.MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_LARGE_PLATE, ForgeweaveItems.PART_LARGE_PLATE, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_HAMMER_HEAD, ForgeweaveItems.PART_HAMMER_HEAD, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_EXCAVATOR_HEAD, ForgeweaveItems.PART_EXCAVATOR_HEAD, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_SCYTHE_HEAD, ForgeweaveItems.PART_SCYTHE_HEAD, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_KAMA_HEAD, ForgeweaveItems.PART_KAMA_HEAD, PartBuilderRecipes.HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_BROAD_AXE_HEAD, ForgeweaveItems.PART_BROAD_AXE_HEAD, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_VEIN_HAMMER_HEAD, ForgeweaveItems.PART_VEIN_HAMMER_HEAD, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_WAR_MACE_HEAD, ForgeweaveItems.PART_WAR_MACE_HEAD, PartBuilderRecipes.LARGE_HEAD_COST));

    private static PartBuilderMenu openMenu(GameTestHelper helper, BlockPos pos, Player player) {
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos);
        return new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    @GameTest(template = "empty")
    public static void everyM3PartCraftsFromItsPattern(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ResourceLocation wood = materialId("wood");

        for (Entry entry : M3_ENTRIES) {
            PartBuilderMenu menu = openMenu(helper, pos, player);

            menu.getSlot(0).set(new ItemStack(entry.pattern().get()));
            ItemStack shards = new ItemStack(ForgeweaveItems.SHARD.get(), entry.cost());
            shards.set(ForgeweaveDataComponents.MATERIAL.get(), wood);
            menu.getSlot(1).set(shards);
            menu.broadcastChanges();

            ItemStack output = menu.getSlot(2).getItem();
            DeferredItem<? extends PartItem> expectedPart = entry.part();
            helper.assertTrue(output.is(expectedPart.get()),
                    "expected " + expectedPart.getId() + ", got " + output);
            helper.assertTrue(wood.equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected " + expectedPart.getId() + "'s material to be forgeweave:wood, got "
                            + output.get(ForgeweaveDataComponents.MATERIAL.get()));

            menu.getSlot(2).onTake(player, output);
            helper.assertTrue(menu.getSlot(1).getItem().isEmpty(),
                    "expected all " + entry.cost() + " shards to be consumed for " + expectedPart.getId());
            helper.assertTrue(menu.getSlot(3).getItem().isEmpty(),
                    "expected no shard change for " + expectedPart.getId() + "'s exact-value craft");
        }

        helper.succeed();
    }
}
