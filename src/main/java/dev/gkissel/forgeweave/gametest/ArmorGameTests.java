package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.menu.ToolStationTabs;
import dev.gkissel.forgeweave.tool.ArmorStats;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * M4-3 (issue #678; SCOPE.md D14/D19/D23), moved onto its own block by issue #782 (reversing D13):
 * plate armor assembles at the Armor Station -- no longer the Tool Station or Tool Forge -- from
 * plating + maille with the 1.20 clone's iron {@code PlatingMaterialStats}, the wrong piece's
 * plating is refused, damage taken wears and is attenuated by the plating, a Broken piece stays on
 * and protects nothing, and the station repairs it with the plating's repair item.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ArmorGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);

    /** The 1.20 clone's iron plating rows ({@code MaterialStatsDataProvider}): durability, armor. */
    private static final int[][] IRON = {{165, 2}, {240, 5}, {225, 4}, {195, 2}};

    private static ItemStack piece(GameTestHelper helper, Player player, Block station, ToolConstants.Entry entry) {
        return ToolAssembly.assembleAt(helper, player, STATION, station, ToolAssembly.entryOf(entry),
                List.of("iron", "iron"));
    }

    private static void assertIronPiece(GameTestHelper helper, ItemStack piece, int index, String where) {
        ToolConstants.Entry entry = ToolConstants.ARMOR.get(index);
        helper.assertTrue(piece.getItem() instanceof ArmorPieceItem
                        && BuiltInRegistries.ITEM.getKey(piece.getItem()).getPath().equals(entry.id()),
                where + " must assemble a " + entry.id() + ", got " + piece);
        ArmorStats stats = ArmorPieceItem.stats(piece);
        helper.assertTrue(stats != null, "an assembled piece carries ARMOR_STATS");
        helper.assertTrue(stats.durability() == IRON[index][0] && stats.armor() == IRON[index][1]
                        && stats.toughness() == 0 && stats.knockbackResistance() == 0,
                entry.id() + " must carry iron's " + IRON[index][0] + "/" + IRON[index][1] + ", got " + stats);
        helper.assertTrue(piece.getMaxDamage() == IRON[index][0],
                "max_damage is the plating's durability, got " + piece.getMaxDamage());
        helper.assertTrue(piece.get(ForgeweaveDataComponents.TOOL_STATS.get()) == null,
                "armor never carries tool_stats (D14)");
        helper.assertTrue(piece.getOrDefault(ForgeweaveDataComponents.ENCHANTABILITY.get(), 0) == 14,
                "enchantability is iron's 14 (D20)");
    }

    @GameTest(template = "empty")
    public static void everyPieceAssemblesAtTheArmorStationWithIronValues(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < 4; i++) {
            assertIronPiece(helper, piece(helper, player, ForgeweaveBlocks.ARMOR_STATION.get(), ToolConstants.ARMOR.get(i)),
                    i, "the Armor Station");
        }
        helper.succeed();
    }

    /**
     * Issue #782 (reversing D13): the Tool Station and the Tool Forge no longer build armor at
     * all -- the {@code ToolAssemblyRecipes#resolveAssembly} category gate refuses it even when the
     * parts are loaded directly into the container, bypassing the (now-absent) build tab.
     */
    @GameTest(template = "empty")
    public static void neitherTheStationNorTheForgeAssembleArmorAnyMore(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Block station : List.of(ForgeweaveBlocks.TOOL_STATION.get(), ForgeweaveBlocks.TOOL_FORGE.get())) {
            ItemStack output = piece(helper, player, station, ToolConstants.CHESTPLATE);
            helper.assertTrue(output.isEmpty(), station + " must refuse to assemble armor, got " + output);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void helmetPlatingIsRefusedInTheChestplateRow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(STATION, ForgeweaveBlocks.ARMOR_STATION.get());
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        int tab = ToolStationTabs.indexOfTool(ForgeweaveItems.ARMOR_CHESTPLATE.get());
        helper.assertTrue(tab >= 0 && menu.clickMenuButton(player, tab), "the chestplate tab must exist and be selectable");
        helper.assertTrue(menu.getSlot(0).mayPlace(ToolAssembly.part(ForgeweaveItems.PART_PLATING_CHESTPLATE.get(), "iron")),
                "the chestplate row takes chest plating");
        helper.assertFalse(menu.getSlot(0).mayPlace(ToolAssembly.part(ForgeweaveItems.PART_PLATING_HELMET.get(), "iron")),
                "the chestplate row must refuse helmet plating");
        helper.assertTrue(menu.getSlot(1).mayPlace(ToolAssembly.part(ForgeweaveItems.PART_MAILLE.get(), "iron")),
                "the chestplate row takes maille second");

        // And loaded regardless of the tab, helmet plating + maille is a helmet, never a chestplate.
        blockEntity.container().setItem(0, ToolAssembly.part(ForgeweaveItems.PART_PLATING_HELMET.get(), "iron"));
        blockEntity.container().setItem(1, ToolAssembly.part(ForgeweaveItems.PART_MAILLE.get(), "iron"));
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.ARMOR_HELMET.get()), "helmet plating builds a helmet, got " + output);
        helper.succeed();
    }

    /**
     * A survival mock player wearing a freshly assembled iron piece, ticked once so
     * {@code LivingEntity#detectEquipmentUpdates} has applied the piece's attribute modifiers -- a
     * mock player is not in the level's tick list, and a mock <em>server</em> player carries 60
     * ticks of spawn invulnerability that nothing here would ever count down.
     */
    private static Player wearing(GameTestHelper helper, ToolConstants.Entry entry) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.CHEST, piece(helper, player, ForgeweaveBlocks.ARMOR_STATION.get(), entry));
        player.tick();
        return player;
    }

    /**
     * A blow armor is allowed to stop: an ownerless explosion. Vanilla's {@code generic} sits in
     * {@code #minecraft:bypasses_armor}, and a mob attack scales with the difficulty setting.
     */
    private static DamageSource blow(GameTestHelper helper) {
        return helper.getLevel().damageSources().explosion(null, null);
    }

    /** Vanilla {@code CombatRules#getDamageAfterAbsorb}: 4 damage against 5 armor, 0 toughness. */
    private static final float BLOW = 4.0F;
    private static final float ABSORBED_BLOW = BLOW * (1.0F - Math.max(5.0F - BLOW / 2.0F, 5.0F * 0.2F) / 25.0F);

    @GameTest(template = "empty")
    public static void damageWearsThePlatingAndIsAttenuatedByItsArmor(GameTestHelper helper) {
        Player player = wearing(helper, ToolConstants.CHESTPLATE);
        DamageSource source = blow(helper);

        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(player.getArmorValue() == 5,
                            "an iron chestplate must give 5 armor, got " + player.getArmorValue());
                    float before = player.getHealth();
                    player.hurt(source, BLOW);
                    float lost = before - player.getHealth();
                    helper.assertTrue(Math.abs(lost - ABSORBED_BLOW) < 0.01F,
                            "expected the blow attenuated to " + ABSORBED_BLOW + ", lost " + lost);
                    ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
                    helper.assertTrue(worn.getDamageValue() == 1,
                            "the plating must take max(1, 4/4) durability, got " + worn.getDamageValue());
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void aBrokenPieceStaysEquippedAndProtectsNothing(GameTestHelper helper) {
        Player player = wearing(helper, ToolConstants.CHESTPLATE);
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        // Wear it down through the real path: the clamp stops at max - 1 and raises BROKEN.
        chestplate.hurtAndBreak(chestplate.getMaxDamage() + 50, player, EquipmentSlot.CHEST);
        helper.assertTrue(ToolItem.isBroken(chestplate) && chestplate.getDamageValue() == chestplate.getMaxDamage() - 1,
                "wearing past its durability must leave the piece Broken, not destroyed: " + chestplate);
        player.tick(); // re-collects the equipment: the Broken piece's modifiers come off
        DamageSource source = blow(helper);

        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(player.getArmorValue() == 0,
                            "a Broken piece contributes no armor, got " + player.getArmorValue());
                    float before = player.getHealth();
                    player.hurt(source, BLOW);
                    helper.assertTrue(Math.abs(before - player.getHealth() - BLOW) < 0.01F,
                            "a Broken piece must not attenuate the blow");
                    ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
                    helper.assertTrue(worn.is(ForgeweaveItems.ARMOR_CHESTPLATE.get()) && ToolItem.isBroken(worn),
                            "the Broken piece must stay equipped, got " + worn);
                    helper.assertTrue(worn.getDamageValue() == worn.getMaxDamage() - 1,
                            "a Broken piece takes no further durability damage");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void theStationRepairsAPieceWithThePlatingsRepairItem(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack chestplate = piece(helper, player, ForgeweaveBlocks.ARMOR_STATION.get(), ToolConstants.CHESTPLATE);
        chestplate.set(DataComponents.DAMAGE, chestplate.getMaxDamage() - 1);
        chestplate.set(ForgeweaveDataComponents.BROKEN.get(), true);

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        blockEntity.container().setItem(0, chestplate);
        blockEntity.container().setItem(1, new ItemStack(Items.IRON_INGOT, 1));
        for (int i = 2; i < ToolStationMenu.INPUT_SLOTS; i++) {
            blockEntity.container().setItem(i, ItemStack.EMPTY);
        }
        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).mayPlace(chestplate),
                "the repair tab's head slot must take an armor piece");
        helper.assertTrue(menu.getSlot(1).mayPlace(new ItemStack(Items.IRON_INGOT)),
                "an iron-plated piece repairs with iron (D19)");
        helper.assertFalse(menu.getSlot(1).mayPlace(new ItemStack(Items.DIRT)), "dirt repairs nothing");
        menu.broadcastChanges();

        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(repaired.is(ForgeweaveItems.ARMOR_CHESTPLATE.get()), "expected the repaired piece, got " + repaired);
        // One iron ingot is worth the plating's own 240 durability -- a full restore on a fresh piece.
        helper.assertTrue(repaired.getDamageValue() == 0, "one ingot restores the piece, damage left " + repaired.getDamageValue());
        helper.assertFalse(ToolItem.isBroken(repaired), "a repaired piece is no longer Broken");
        helper.succeed();
    }
}
