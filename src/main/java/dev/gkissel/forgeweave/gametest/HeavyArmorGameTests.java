package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.tool.ArmorStats;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Issue #735 (epic #730, slice 1), moved onto the Armor Station by issue #782 (reversing D13): the
 * heavy set assembles from plating + maille + large plate with armor x1.4 off the plating's block
 * (toughness/knockback resistance/durability unchanged), each worn piece takes 5% off movement
 * speed (the four stack multiplicatively), and a row without its large plate is the plain piece,
 * never the heavy one.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class HeavyArmorGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);

    /** Iron's plating rows (the 1.20 clone's {@code MaterialStatsDataProvider}): durability as-is, armor x1.4. */
    private static final int[] IRON_DURABILITY = {165, 240, 225, 195};
    private static final float[] IRON_HEAVY_ARMOR = {2.8F, 7.0F, 5.6F, 2.8F};

    private static ItemStack piece(GameTestHelper helper, Player player, Block station, ToolConstants.Entry entry) {
        return ToolAssembly.assembleAt(helper, player, STATION, station, ToolAssembly.entryOf(entry),
                List.of("iron", "iron", "iron"));
    }

    private static void assertIronPiece(GameTestHelper helper, ItemStack piece, int index, String where) {
        ToolConstants.Entry entry = ToolConstants.HEAVY_ARMOR.get(index);
        helper.assertTrue(piece.getItem() instanceof ArmorPieceItem
                        && BuiltInRegistries.ITEM.getKey(piece.getItem()).getPath().equals(entry.id()),
                where + " must assemble a " + entry.id() + ", got " + piece);
        ArmorStats stats = ArmorPieceItem.stats(piece);
        helper.assertTrue(stats != null, "an assembled heavy piece carries ARMOR_STATS");
        helper.assertTrue(stats.durability() == IRON_DURABILITY[index]
                        && Math.abs(stats.armor() - IRON_HEAVY_ARMOR[index]) < 1e-4F
                        && stats.toughness() == 0 && stats.knockbackResistance() == 0,
                entry.id() + " must carry iron's " + IRON_DURABILITY[index] + "/" + IRON_HEAVY_ARMOR[index]
                        + ", got " + stats);
        helper.assertTrue(piece.getMaxDamage() == IRON_DURABILITY[index],
                "max_damage is the plating's durability, unchanged by the large plate");
        helper.assertTrue(piece.getOrDefault(ForgeweaveDataComponents.ENCHANTABILITY.get(), 0) == 14,
                "enchantability is iron's 14 (D20)");
    }

    @GameTest(template = "empty")
    public static void everyHeavyPieceAssemblesAtTheArmorStationWithIronValues(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < 4; i++) {
            assertIronPiece(helper, piece(helper, player, ForgeweaveBlocks.ARMOR_STATION.get(),
                    ToolConstants.HEAVY_ARMOR.get(i)), i, "the Armor Station");
        }
        helper.succeed();
    }

    /** Issue #782 (reversing D13): neither tool block builds heavy armor any more either. */
    @GameTest(template = "empty")
    public static void neitherTheStationNorTheForgeAssembleHeavyArmorAnyMore(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Block station : List.of(ForgeweaveBlocks.TOOL_STATION.get(), ForgeweaveBlocks.TOOL_FORGE.get())) {
            ItemStack output = piece(helper, player, station, ToolConstants.HEAVY_CHESTPLATE);
            helper.assertTrue(output.isEmpty(), station + " must refuse to assemble heavy armor, got " + output);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void withoutItsLargePlateTheRowBuildsThePlainPiece(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(STATION, ForgeweaveBlocks.ARMOR_STATION.get());
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        blockEntity.container().setItem(0, ToolAssembly.part(ForgeweaveItems.PART_PLATING_CHESTPLATE.get(), "iron"));
        blockEntity.container().setItem(1, ToolAssembly.part(ForgeweaveItems.PART_MAILLE.get(), "iron"));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.ARMOR_CHESTPLATE.get()),
                "plating + maille alone is the plain chestplate, never the heavy one; got " + output);

        blockEntity.container().setItem(2, ToolAssembly.part(ForgeweaveItems.PART_LARGE_PLATE.get(), "iron"));
        menu.broadcastChanges();
        output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.ARMOR_HEAVY_CHESTPLATE.get()),
                "plating + maille + large plate is the heavy chestplate, got " + output);
        helper.succeed();
    }

    /** Movement speed over its base after wearing {@code entries}, ticked once so the worn modifiers apply. */
    private static double speedWearing(GameTestHelper helper, List<ToolConstants.Entry> entries) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (ToolConstants.Entry entry : entries) {
            ItemStack stack = piece(helper, player, ForgeweaveBlocks.ARMOR_STATION.get(), entry);
            player.setItemSlot(((ArmorPieceItem) stack.getItem()).getEquipmentSlot(), stack);
        }
        player.tick();
        return player.getAttributeValue(Attributes.MOVEMENT_SPEED)
                / player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
    }

    @GameTest(template = "empty")
    public static void eachHeavyPieceTakesFivePercentOffMovementSpeed(GameTestHelper helper) {
        for (ToolConstants.Entry entry : ToolConstants.HEAVY_ARMOR) {
            double ratio = speedWearing(helper, List.of(entry));
            helper.assertTrue(Math.abs(ratio - 0.95) < 1e-6, entry.id() + " must give 95% speed, got " + ratio);
        }
        helper.assertTrue(Math.abs(speedWearing(helper, List.of()) - 1.0) < 1e-6,
                "an unarmored player keeps full speed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theFullHeavySetStacksMultiplicatively(GameTestHelper helper) {
        double ratio = speedWearing(helper, ToolConstants.HEAVY_ARMOR);
        // Four ADD_MULTIPLIED_TOTAL modifiers of -0.05 compound to 0.95^4 (#730: "about -20%").
        helper.assertTrue(Math.abs(ratio - Math.pow(0.95, 4)) < 1e-6, "the set must give 0.95^4 speed, got " + ratio);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPlainPieceLeavesMovementSpeedAlone(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.ARMOR_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of("iron", "iron"));
        player.setItemSlot(EquipmentSlot.CHEST, stack);
        player.tick();
        double ratio = player.getAttributeValue(Attributes.MOVEMENT_SPEED)
                / player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(Math.abs(ratio - 1.0) < 1e-6, "a plate chestplate must not slow the wearer, got " + ratio);
        helper.succeed();
    }
}
