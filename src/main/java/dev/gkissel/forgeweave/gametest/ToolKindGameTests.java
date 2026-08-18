package dev.gkissel.forgeweave.gametest;

import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * Tool kind, end to end (parity audit 2026-08-18 T33, issue #464): the vanilla and {@code c:} tool
 * tags a Forgeweave tool is in, and the one behavior this ticket adds -- upstream 1.12
 * {@code Shovel#onItemUse}'s grass paths, over the shovel's one block and the excavator's 3x3.
 *
 * <p>The per-kind {@code canPerformAction} sets themselves are unit-tested ({@code ToolAbilityTest});
 * what needs a server is the datapack half (tags only resolve against loaded data) and the flatten
 * itself (which runs through {@code BlockState#getToolModifiedState} and real block changes).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ToolKindGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);

    /**
     * Every tool sits in the vanilla tag for its upstream tool class, and so -- through NeoForge's own
     * {@code c:tools}, which reads all five vanilla tags -- in the {@code c:} tool tree other mods
     * query. Spot-checked one tool per tag rather than all twenty-four; the provider adds them in one
     * block, so a missing tag is missing for the whole family.
     */
    @GameTest(template = "empty")
    public static void toolsCarryTheirVanillaAndConventionToolTags(GameTestHelper helper) {
        assertTagged(helper, ForgeweaveItems.TOOL_PICKAXE.get(), ItemTags.PICKAXES);
        assertTagged(helper, ForgeweaveItems.TOOL_HAMMER.get(), ItemTags.PICKAXES);
        assertTagged(helper, ForgeweaveItems.TOOL_SHOVEL.get(), ItemTags.SHOVELS);
        assertTagged(helper, ForgeweaveItems.TOOL_EXCAVATOR.get(), ItemTags.SHOVELS);
        assertTagged(helper, ForgeweaveItems.TOOL_HATCHET.get(), ItemTags.AXES);
        assertTagged(helper, ForgeweaveItems.TOOL_MATTOCK.get(), ItemTags.AXES);
        assertTagged(helper, ForgeweaveItems.TOOL_MATTOCK.get(), ItemTags.HOES);
        assertTagged(helper, ForgeweaveItems.TOOL_KAMA.get(), ItemTags.HOES);
        assertTagged(helper, ForgeweaveItems.TOOL_BROADSWORD.get(), ItemTags.SWORDS);
        assertTagged(helper, ForgeweaveItems.TOOL_CLEAVER.get(), ItemTags.SWORDS);

        assertTagged(helper, ForgeweaveItems.TOOL_PICKAXE.get(), conventionTag("tools"));
        assertTagged(helper, ForgeweaveItems.TOOL_BROADSWORD.get(), conventionTag("tools/melee_weapon"));
        assertTagged(helper, ForgeweaveItems.TOOL_WARMACE.get(), conventionTag("tools/mace"));
        assertTagged(helper, ForgeweaveItems.TOOL_WARMACE.get(), conventionTag("tools"));
        assertTagged(helper, ForgeweaveItems.TOOL_EXCAVATOR.get(), conventionTag("tools/mining_tool"));
        assertTagged(helper, ForgeweaveItems.TOOL_KAMA.get(), conventionTag("tools/shear"));
        assertTagged(helper, ForgeweaveItems.TOOL_SHORTBOW.get(), conventionTag("tools/bow"));
        assertTagged(helper, ForgeweaveItems.TOOL_CROSSBOW.get(), conventionTag("tools/crossbow"));
        assertTagged(helper, ForgeweaveItems.TOOL_LONGBOW.get(), conventionTag("tools/ranged_weapon"));
        assertTagged(helper, ForgeweaveItems.TOOL_SHORTBOW.get(), conventionTag("tools"));

        // A weapon is not a mining tool and a harvest tool is not a melee weapon -- upstream's own
        // Category.WEAPON / Category.HARVEST split, which is what these two c: tags mean.
        helper.assertFalse(new ItemStack(ForgeweaveItems.TOOL_BROADSWORD.get()).is(conventionTag("tools/mining_tool")),
                "a broadsword is not a mining tool");
        helper.assertFalse(new ItemStack(ForgeweaveItems.TOOL_HATCHET.get()).is(conventionTag("tools/melee_weapon")),
                "the hatchet is a harvest tool upstream, not a melee weapon");
        helper.succeed();
    }

    /**
     * Joining {@code #minecraft:swords} joins every {@code #minecraft:enchantable/*} tag vanilla builds
     * from it, which is the whole gate on an anvil's enchanted-book merge -- so the invariant upstream
     * states as {@code TinkersItem#isBookEnchantable == false} has to be stated here too, and is, on
     * the same {@code allowVanillaEnchanting} toggle the enchanting table already uses (default off).
     */
    @GameTest(template = "empty")
    public static void taggedToolsStillRefuseAnEnchantedBook(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        for (Item tool : List.of(ForgeweaveItems.TOOL_BROADSWORD.get(), ForgeweaveItems.TOOL_PICKAXE.get(),
                ForgeweaveItems.TOOL_HATCHET.get())) {
            helper.assertFalse(new ItemStack(tool).isBookEnchantable(book),
                    tool + " must not take an enchanted book at an anvil by default");
        }
        helper.succeed();
    }

    /**
     * Upstream {@code Shovel#onItemUse}: right-clicking grass makes a path, for one durability. The
     * plain shovel's own {@code getAOEBlocks} is 1x1x1, so it is the clicked block alone.
     */
    @GameTest(template = "empty")
    public static void shovelMakesAGrassPath(GameTestHelper helper) {
        BlockPos grass = new BlockPos(1, 1, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack shovel = ToolAssembly.tool(helper, player, STATION, ForgeweaveItems.PART_SHOVEL_HEAD.get(),
                "stone", "wood", "wood");
        player.setItemInHand(InteractionHand.MAIN_HAND, shovel);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);

        helper.useBlock(grass, player);

        helper.assertBlockPresent(Blocks.DIRT_PATH, grass);
        helper.assertTrue(shovel.getDamageValue() == 1,
                "making a path must cost 1 durability, got " + shovel.getDamageValue());
        helper.succeed();
    }

    /** Same swing on a Broken shovel does nothing -- upstream opens with {@code isBroken -> FAIL}. */
    @GameTest(template = "empty")
    public static void aBrokenShovelMakesNoPath(GameTestHelper helper) {
        BlockPos grass = new BlockPos(1, 1, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack shovel = ToolAssembly.tool(helper, player, STATION, ForgeweaveItems.PART_SHOVEL_HEAD.get(),
                "stone", "wood", "wood");
        shovel.set(ForgeweaveDataComponents.BROKEN.get(), true);
        player.setItemInHand(InteractionHand.MAIN_HAND, shovel);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);

        helper.assertFalse(shovel.canPerformAction(ItemAbilities.SHOVEL_FLATTEN),
                "a Broken shovel must not claim the flatten ability");
        helper.useBlock(grass, player);

        helper.assertBlockPresent(Blocks.GRASS_BLOCK, grass);
        helper.succeed();
    }

    /**
     * The excavator inherits the path from {@code Shovel} and applies it over its own
     * {@code getAOEBlocks} -- upstream's "only do the AOE path if the selected block is grass or grass
     * path", read after the first block is already flattened. A 3x3 of grass, one click in the middle,
     * nine paths.
     */
    @GameTest(template = "empty")
    public static void excavatorPathsItsWholeThreeByThree(GameTestHelper helper) {
        BlockPos center = new BlockPos(3, 1, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(ForgeweaveItems.TOOL_EXCAVATOR.get());
        ItemStack excavator = ToolAssembly.assembleAtForge(helper, player, STATION, entry,
                Collections.nCopies(entry.slotCount(), "stone"));
        player.setItemInHand(InteractionHand.MAIN_HAND, excavator);

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(center.offset(x, 0, z), Blocks.GRASS_BLOCK);
            }
        }
        // The area's orientation comes from where the player is actually looking (AoeHarvest#aim),
        // not from the click's own face, so put the player above it looking straight down -- which is
        // how a player making a path stands anyway, and gives the horizontal 3x3 rather than a wall.
        player.setPos(Vec3.atCenterOf(helper.absolutePos(center.above(2))));
        player.setXRot(90.0F);
        player.setYRot(0.0F);

        helper.useBlock(center, player);

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.assertBlockPresent(Blocks.DIRT_PATH, center.offset(x, 0, z));
            }
        }
        helper.assertTrue(excavator.getDamageValue() == 9,
                "expected 1 durability per flattened block of the 3x3, got " + excavator.getDamageValue());
        helper.succeed();
    }

    /**
     * The mattock digs as a shovel but never paths: upstream 1.12's {@code Mattock#onItemUse} only
     * hoes, and 1.20 gives the pathing trait to the excavator alone.
     */
    @GameTest(template = "empty")
    public static void mattockTillsWhereAShovelWouldPath(GameTestHelper helper) {
        BlockPos grass = new BlockPos(1, 1, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = ToolAssembly.assemble(helper, player, STATION,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_MATTOCK.get()), List.of("wood", "stone", "stone"));
        player.setItemInHand(InteractionHand.MAIN_HAND, mattock);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);

        helper.assertFalse(mattock.canPerformAction(ItemAbilities.SHOVEL_FLATTEN),
                "a mattock must not claim the flatten ability");
        helper.useBlock(grass, player);

        helper.assertBlockPresent(Blocks.FARMLAND, grass);
        helper.succeed();
    }

    private static void assertTagged(GameTestHelper helper, Item item, TagKey<Item> tag) {
        helper.assertTrue(new ItemStack(item).is(tag), item + " must be in " + tag.location());
    }

    private static TagKey<Item> conventionTag(String path) {
        return TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private ToolKindGameTests() {}
}
