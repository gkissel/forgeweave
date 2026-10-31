package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.level.GameType;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;

/**
 * Since 1.21.5 the enchanting table reads only {@code minecraft:enchantable}, which 1.21.1 never wrote
 * on a stack. A tool or armor piece assembled on 1.21.1 loads with Forgeweave's
 * {@code forgeweave:enchantability} and the item's default vanilla 14, and has to pick up its
 * material's value the first time it ticks in an inventory (see {@code Enchantability#backfill}).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class EnchantabilityBackfillGameTests {

    /** Paper is worth 22, so an all-paper pickaxe is too. */
    @GameTest(template = "empty")
    public static void toolAssembledBefore26_1EnchantsAtItsMaterialsValue(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "paper", "paper", "paper");
        // What a 1.21.1 save loads as: Forgeweave's 22, vanilla's back at the item default.
        pickaxe.set(DataComponents.ENCHANTABLE, new Enchantable(Material.DEFAULT_ENCHANTABILITY));
        helper.assertTrue(!pickaxe.hasNonDefault(DataComponents.ENCHANTABLE)
                        && pickaxe.get(ForgeweaveDataComponents.ENCHANTABILITY.get()) == 22,
                "setup: the pickaxe should carry only the old component");

        pickaxe.inventoryTick(helper.getLevel(), player, null);

        assertEnchantable(helper, pickaxe, 22, "an all-paper pickaxe from a 1.21.1 save");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorAssembledBefore26_1EnchantsAtItsMaterialsValue(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack helmet = new ItemStack(ForgeweaveItems.ARMOR_HELMET.get());
        helmet.set(ForgeweaveDataComponents.ENCHANTABILITY.get(), 9);

        helmet.inventoryTick(helper.getLevel(), player, null);

        assertEnchantable(helper, helmet, 9, "a helmet from a 1.21.1 save");
        helper.succeed();
    }

    /** A value already on the stack wins: the backfill never overwrites a 26.1 assembly. */
    @GameTest(template = "empty")
    public static void backfillLeavesAFreshAssemblyAlone(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "stone", "stone", "stone");
        pickaxe.set(DataComponents.ENCHANTABLE, new Enchantable(30));

        pickaxe.inventoryTick(helper.getLevel(), player, null);

        assertEnchantable(helper, pickaxe, 30, "a pickaxe whose vanilla value is already set");
        helper.succeed();
    }

    private static void assertEnchantable(GameTestHelper helper, ItemStack stack, int expected, String what) {
        Enchantable enchantable = stack.get(DataComponents.ENCHANTABLE);
        int actual = enchantable == null ? 0 : enchantable.value();
        helper.assertTrue(actual == expected, what + " should enchant at " + expected + ", got " + actual);
    }
}
