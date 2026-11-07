package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ArmorStats;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * {@link ToolAssemblyRecipes#refreshStats}: gear built before a stat change catches up the first time
 * it ticks in an inventory. "Stale" here means a stack whose stored stats no longer match what its
 * part set builds, which is what a save holds after an update changes a material.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class StatRefreshGameTests {

    private static final ToolStats.Stats STALE_STATS = new ToolStats.Stats(100, 1.0F, 1.0F);

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    /**
     * The refresh alone. A full inventory tick would also run the wood parts' ecological trait,
     * which repairs at random and would make the exact damage checks below flaky.
     */
    private static void tick(GameTestHelper helper, Player player, ItemStack stack) {
        ToolAssemblyRecipes.refreshStats(helper.getLevel().registryAccess(), stack);
    }

    /** A real inventory tick, for the one test whose parts carry no trait that ticks. */
    private static void inventoryTick(GameTestHelper helper, Player player, ItemStack stack) {
        stack.inventoryTick(helper.getLevel(), player, 0, false);
    }

    @GameTest(template = "empty")
    public static void aStaleToolCatchesUpAndKeepsItsModifiersNameAndDamage(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "iron", "wood", "wood");
        ToolStats.Stats current = pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get());
        int currentMax = pickaxe.getMaxDamage();
        List<ModifierEntry> modifiers = List.of(new ModifierEntry(id("haste"), 1));
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), modifiers);
        pickaxe.set(DataComponents.CUSTOM_NAME, Component.literal("Fred"));
        pickaxe.set(ForgeweaveDataComponents.TOOL_STATS.get(), STALE_STATS);
        pickaxe.set(DataComponents.MAX_DAMAGE, STALE_STATS.durability());
        pickaxe.set(DataComponents.DAMAGE, 7);

        tick(helper, player, pickaxe);

        helper.assertTrue(current.equals(pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get())),
                "the stats must be what iron/wood/wood builds now, got "
                        + pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get()));
        helper.assertTrue(pickaxe.getMaxDamage() == currentMax,
                "the durability pool must grow back to " + currentMax + ", got " + pickaxe.getMaxDamage());
        helper.assertTrue(pickaxe.getDamageValue() == 7, "the damage value must stay, got " + pickaxe.getDamageValue());
        helper.assertTrue(modifiers.equals(ForgeweaveModifiers.of(pickaxe)),
                "the modifiers must stay, got " + ForgeweaveModifiers.of(pickaxe));
        helper.assertTrue(ForgeweaveModifiers.effectiveStats(pickaxe).miningSpeed() > current.miningSpeed(),
                "haste must still apply on top of the new stats");
        helper.assertTrue(Component.literal("Fred").equals(pickaxe.get(DataComponents.CUSTOM_NAME)),
                "the name must stay");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aStaleArmorPieceCatchesUp(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = ToolAssembly.assembleAt(helper, player, new BlockPos(1, 1, 1),
                ForgeweaveBlocks.TOOL_STATION.get(), ToolAssembly.entryOf(ToolConstants.ARMOR.get(0)),
                List.of("iron", "iron"));
        ArmorStats current = piece.get(ForgeweaveDataComponents.ARMOR_STATS.get());
        int currentMax = piece.getMaxDamage();
        piece.set(ForgeweaveDataComponents.ARMOR_STATS.get(), new ArmorStats(0.5F, 0.0F, 0.0F, 100));
        piece.set(DataComponents.MAX_DAMAGE, 100);

        inventoryTick(helper, player, piece);

        helper.assertTrue(current.equals(piece.get(ForgeweaveDataComponents.ARMOR_STATS.get())),
                "the stats must be what iron plating builds now, got "
                        + piece.get(ForgeweaveDataComponents.ARMOR_STATS.get()));
        helper.assertTrue(piece.getMaxDamage() == currentMax,
                "the durability pool must follow, got " + piece.getMaxDamage());
        helper.succeed();
    }

    /** A pool that shrinks below the wear must not leave the tool past the point it breaks at. */
    @GameTest(template = "empty")
    public static void wearAboveTheNewPoolStopsOneHitFromBreaking(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "iron", "wood", "wood");
        int currentMax = pickaxe.getMaxDamage();
        pickaxe.set(ForgeweaveDataComponents.TOOL_STATS.get(), new ToolStats.Stats(currentMax * 10, 1.0F, 1.0F));
        pickaxe.set(DataComponents.MAX_DAMAGE, currentMax * 10);
        pickaxe.set(DataComponents.DAMAGE, currentMax * 5);

        tick(helper, player, pickaxe);

        helper.assertTrue(pickaxe.getDamageValue() == currentMax - 1,
                "wear must stop at " + (currentMax - 1) + ", got " + pickaxe.getDamageValue());
        helper.assertFalse(ToolItem.isBroken(pickaxe), "the tool must not break from a refresh");
        helper.succeed();
    }

    /** Broken sits at max - 1. A pool that grows must not leave a Broken tool looking half worn. */
    @GameTest(template = "empty")
    public static void aBrokenToolStaysBrokenAtTheNewPool(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "iron", "wood", "wood");
        int currentMax = pickaxe.getMaxDamage();
        pickaxe.set(ForgeweaveDataComponents.TOOL_STATS.get(), STALE_STATS);
        pickaxe.set(DataComponents.MAX_DAMAGE, STALE_STATS.durability());
        pickaxe.set(DataComponents.DAMAGE, STALE_STATS.durability() - 1);
        pickaxe.set(ForgeweaveDataComponents.BROKEN.get(), true);

        tick(helper, player, pickaxe);

        helper.assertTrue(ToolItem.isBroken(pickaxe), "a refresh must not repair a broken tool");
        helper.assertTrue(pickaxe.getDamageValue() == currentMax - 1,
                "a broken tool sits at " + (currentMax - 1) + ", got " + pickaxe.getDamageValue());
        helper.succeed();
    }

    /** A removed mod's material and a material that lost its head stats both leave the tool as it is. */
    @GameTest(template = "empty")
    public static void partsThatNoLongerBuildLeaveTheToolAlone(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (String head : List.of("no_such_material", "string")) {
            ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "iron", "wood", "wood");
            ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(ForgeweaveItems.TOOL_PICKAXE.get());
            pickaxe.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                    ToolMaterials.of(entry.constants().parts(), List.of(id(head), id("wood"), id("wood"))));
            pickaxe.set(ForgeweaveDataComponents.TOOL_STATS.get(), STALE_STATS);

            tick(helper, player, pickaxe);

            helper.assertTrue(STALE_STATS.equals(pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get())),
                    "a " + head + " head can't build, so the stats must stay, got "
                            + pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get()));
        }
        helper.succeed();
    }
}
