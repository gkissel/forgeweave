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
 * tags a Forgeweave tool is in, and the right-click behaviors that follow from a tool's kind --
 * upstream 1.12 {@code Shovel#onItemUse}'s grass paths over the shovel's one block and the
 * excavator's 3x3 (#464), and the axe family's strip/scrape/wax-off (#575).
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

    /**
     * Issue #575: right-clicking a log with an axe-family tool strips it, for one durability --
     * upstream 1.20's {@code stripping} trait's {@code AXE_STRIP} transform, spelled here as vanilla
     * {@code AxeItem#useOn}'s own body ({@code AxeStrip}).
     */
    @GameTest(template = "empty")
    public static void hatchetStripsALog(GameTestHelper helper) {
        BlockPos log = new BlockPos(1, 1, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = axe(helper, player, ForgeweaveItems.TOOL_HATCHET.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        helper.setBlock(log, Blocks.OAK_LOG);

        helper.useBlock(log, player);

        helper.assertBlockPresent(Blocks.STRIPPED_OAK_LOG, log);
        helper.assertTrue(hatchet.getDamageValue() == 1,
                "stripping a log must cost 1 durability, got " + hatchet.getDamageValue());
        helper.succeed();
    }

    /**
     * The other two transforms of the same trait, on the same tool: {@code AXE_SCRAPE} walks weathered
     * copper one stage back and {@code AXE_WAX_OFF} takes the wax off. Both are checked on the lumber
     * axe to also pin that the family shares the behavior, not just the hatchet.
     */
    @GameTest(template = "empty")
    public static void lumberaxeScrapesCopperAndWipesWaxOff(GameTestHelper helper) {
        BlockPos exposed = new BlockPos(1, 1, 3);
        BlockPos waxed = new BlockPos(3, 1, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack lumberaxe = axe(helper, player, ForgeweaveItems.TOOL_LUMBERAXE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, lumberaxe);
        helper.setBlock(exposed, Blocks.EXPOSED_COPPER);
        helper.setBlock(waxed, Blocks.WAXED_COPPER_BLOCK);

        helper.useBlock(exposed, player);
        helper.useBlock(waxed, player);

        helper.assertBlockPresent(Blocks.COPPER_BLOCK, exposed);
        helper.assertBlockPresent(Blocks.COPPER_BLOCK, waxed);
        helper.assertTrue(lumberaxe.getDamageValue() == 2,
                "each transform must cost 1 durability, got " + lumberaxe.getDamageValue());
        helper.succeed();
    }

    /** A Broken tool refuses this like every other action -- upstream's {@code isBroken -> FAIL}. */
    @GameTest(template = "empty")
    public static void aBrokenHatchetStripsNothing(GameTestHelper helper) {
        BlockPos log = new BlockPos(1, 1, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = axe(helper, player, ForgeweaveItems.TOOL_HATCHET.get());
        hatchet.set(ForgeweaveDataComponents.BROKEN.get(), true);
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        helper.setBlock(log, Blocks.OAK_LOG);

        helper.assertFalse(hatchet.canPerformAction(ItemAbilities.AXE_STRIP),
                "a Broken hatchet must not claim the strip ability");
        helper.useBlock(log, player);

        helper.assertBlockPresent(Blocks.OAK_LOG, log);
        helper.succeed();
    }

    /**
     * The mattock stops short of stripping the same way it stops short of pathing: upstream 1.20 gives
     * it {@code tilling} and no other interaction trait (issue #575).
     */
    @GameTest(template = "empty")
    public static void mattockStripsNothing(GameTestHelper helper) {
        BlockPos log = new BlockPos(1, 1, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = ToolAssembly.assemble(helper, player, STATION,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_MATTOCK.get()), List.of("wood", "stone", "stone"));
        player.setItemInHand(InteractionHand.MAIN_HAND, mattock);
        helper.setBlock(log, Blocks.OAK_LOG);

        helper.assertFalse(mattock.canPerformAction(ItemAbilities.AXE_STRIP),
                "a mattock must not claim the strip ability");
        helper.useBlock(log, player);

        helper.assertBlockPresent(Blocks.OAK_LOG, log);
        helper.succeed();
    }

    /**
     * Issue #617: right-clicking the lumber axe on a tree's trunk strips the whole tree, not just the
     * block clicked -- {@code AxeStrip} now spreads over the same trunk shape {@code AoeHarvest}'s
     * mining path fells ({@code LargeToolGameTests#lumberAxeFellsTheTrunkAndStopsAtNonLog}), stopping
     * at the first non-log in every direction.
     */
    @GameTest(template = "empty")
    public static void lumberaxeStripsAWholeTree(GameTestHelper helper) {
        BlockPos base = new BlockPos(1, 1, 3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack lumberaxe = axe(helper, player, ForgeweaveItems.TOOL_LUMBERAXE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, lumberaxe);
        // Canopy first, trunk second, so the trunk's own column stays logs rather than leaves.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(base.offset(x, 6, z), Blocks.OAK_LEAVES);
            }
        }
        for (int y = 0; y < 6; y++) {
            helper.setBlock(base.offset(0, y, 0), Blocks.OAK_LOG);
        }
        // Three blocks of air away: a separate tree as far as the strip is concerned, same as the
        // mining path's own "stops at non-log" case.
        BlockPos detached = base.offset(3, 0, 0);
        helper.setBlock(detached, Blocks.OAK_LOG);

        helper.useBlock(base, player);

        for (int y = 0; y < 6; y++) {
            helper.assertBlockPresent(Blocks.STRIPPED_OAK_LOG, base.offset(0, y, 0));
        }
        helper.assertBlockPresent(Blocks.OAK_LOG, detached);
        helper.assertTrue(lumberaxe.getDamageValue() == 6,
                "stripping a 6-log trunk must cost 1 durability per log, got " + lumberaxe.getDamageValue());
        helper.succeed();
    }

    /**
     * Issue #617: the hatchet's shape is {@code AoeHarvest.Shape#SINGLE} -- no extra blocks without an
     * expander -- so stripping a log leaves its neighbor untouched, unlike the lumber axe above.
     */
    @GameTest(template = "empty")
    public static void hatchetStripsOnlyTheLogClicked(GameTestHelper helper) {
        BlockPos log = new BlockPos(1, 1, 3);
        BlockPos neighbor = log.offset(1, 0, 0);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = axe(helper, player, ForgeweaveItems.TOOL_HATCHET.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        helper.setBlock(log, Blocks.OAK_LOG);
        helper.setBlock(neighbor, Blocks.OAK_LOG);

        helper.useBlock(log, player);

        helper.assertBlockPresent(Blocks.STRIPPED_OAK_LOG, log);
        helper.assertBlockPresent(Blocks.OAK_LOG, neighbor);
        helper.assertTrue(hatchet.getDamageValue() == 1,
                "stripping one log with no expander must cost 1 durability, got " + hatchet.getDamageValue());
        helper.succeed();
    }

    /** An all-stone tool of {@code type}, built at the station like every other tool in this class. */
    private static ItemStack axe(GameTestHelper helper, Player player, ToolItem type) {
        ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(type);
        return ToolAssembly.assembleAtForge(helper, player, STATION, entry,
                Collections.nCopies(entry.slotCount(), "stone"));
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
