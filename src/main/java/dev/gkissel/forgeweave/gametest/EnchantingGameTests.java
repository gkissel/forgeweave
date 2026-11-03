package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.modifier.GrantedEnchantments;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import dev.gkissel.forgeweave.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.EventHooks;

import dev.gkissel.forgeweave.gametest.GameTestHolder;
import dev.gkissel.forgeweave.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;

/**
 * docs/SCOPE.md M1 issue #13: the {@code allowVanillaEnchanting} config flag. Covers both flag
 * states against a real assembled tool (see {@link ToolAssembly}), through {@link ItemStack#isEnchantable()}
 * -- the same method {@code EnchantmentMenu} consults to decide whether to offer the item any
 * enchantments at all.
 *
 * <p>Parity audit T54 (issue #485) added the rest: the flag being ON has to actually put something
 * in the table's three slots and let an anvil book land, which needs an enchantment value
 * ({@code ToolItem#getEnchantmentValue}) and membership in the {@code minecraft:enchantable/*} item
 * tags every vanilla enchantment names as its {@code supported_items}. Both halves stay gated, so a
 * default (OFF) world still matches 1.12, where {@code TinkersItem#isBookEnchantable} is
 * {@code false} and no {@code getItemEnchantability} override lifts a tool off vanilla's 0.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class EnchantingGameTests {

    /** CONTEXT.md invariant: off (the default) by default. */
    @GameTest(template = "empty")
    public static void toolRejectedWhenFlagOff(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        ItemStack pickaxe = assembledPickaxe(helper);

        helper.assertFalse(tableOpens(helper, pickaxe),
                "a Forgeweave tool should be rejected by the enchanting table while allowVanillaEnchanting is off");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void toolAcceptedWhenFlagOn(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            ItemStack pickaxe = assembledPickaxe(helper);

            helper.assertTrue(pickaxe.isEnchantable() && tableOpens(helper, pickaxe),
                    "a Forgeweave tool should be accepted by the enchanting table while allowVanillaEnchanting is on");

            helper.succeed();
        } finally {
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
    }

    /**
     * A tool's modifiers must not keep it off the table. Vanilla's menu, like Apothic Enchanting's,
     * refuses an item with anything in {@code minecraft:enchantments}, so Luck's Fortune stays out of
     * that component, and survives whatever the table adds.
     */
    @GameTest(template = "empty")
    public static void aToolWithLuckCanStillBeEnchantedAtTheTable(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            Player player = helper.makeMockPlayer(GameType.CREATIVE);
            BlockPos pos = new BlockPos(1, 1, 1);
            ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
            var registries = helper.getLevel().registryAccess();
            var fortune = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
            ItemStack lucky = ModifierApplication.applyLevel(registries, pickaxe,
                    Identifier.fromNamespaceAndPath(Forgeweave.MODID, "luck"), 60).output();
            helper.assertValueEqual(lucky.getEnchantmentLevel(fortune), 1, "Luck's Fortune on the tool");
            helper.assertTrue(lucky.isEnchantable(), "a tool with Luck must still go on the table");

            EnchantmentMenu menu = new EnchantmentMenu(0, player.getInventory(),
                    ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            menu.getSlot(0).set(lucky);
            menu.slotsChanged(menu.getSlot(0).container);
            helper.assertTrue(menu.costs[0] > 0, "the table must offer the modified tool something");
            player.experienceLevel = 100;
            helper.assertTrue(menu.clickMenuButton(player, 0), "the table must accept the click");

            ItemStack enchanted = menu.getSlot(0).getItem();
            helper.assertTrue(enchanted.isEnchanted(), "the table's enchantment lands on the tool");
            helper.assertValueEqual(enchanted.getEnchantmentLevel(fortune) >= 1, true, "Luck's Fortune survives the table");
            helper.succeed();
        } finally {
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
    }

    /** Silk Touch would switch Luck's Fortune off, so neither the table nor an anvil may add it. */
    @GameTest(template = "empty")
    public static void silkTouchStaysOffALuckTool(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            ItemStack pickaxe = ToolAssembly.pickaxe(helper, helper.makeMockPlayer(GameType.CREATIVE), new BlockPos(1, 1, 1),
                    "stone", "wood", "wood");
            var registries = helper.getLevel().registryAccess();
            var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
            var silkTouch = enchantments.getOrThrow(Enchantments.SILK_TOUCH);
            helper.assertTrue(pickaxe.isPrimaryItemFor(silkTouch), "a plain pickaxe can roll Silk Touch");
            ItemStack lucky = ModifierApplication.applyLevel(registries, pickaxe,
                    Identifier.fromNamespaceAndPath(Forgeweave.MODID, "luck"), 60).output();
            helper.assertFalse(lucky.isPrimaryItemFor(silkTouch), "the table must not roll Silk Touch onto Luck");
            helper.assertFalse(lucky.supportsEnchantment(silkTouch), "the anvil must not add Silk Touch onto Luck");
            helper.assertTrue(lucky.isPrimaryItemFor(enchantments.getOrThrow(Enchantments.EFFICIENCY)), "Efficiency still fits");
            helper.assertTrue(lucky.isPrimaryItemFor(enchantments.getOrThrow(Enchantments.FORTUNE)), "Fortune fits and adds up");
            helper.succeed();
        } finally {
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
    }

    /** Table Fortune III on Luck VI is Fortune IX, shown on one line. */
    @GameTest(template = "empty")
    public static void tableFortuneAddsToLuckOnOneTooltipLine(GameTestHelper helper) {
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, helper.makeMockPlayer(GameType.CREATIVE), new BlockPos(1, 1, 1),
                "stone", "wood", "wood");
        var registries = helper.getLevel().registryAccess();
        var fortune = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        ItemStack lucky = ModifierApplication.applyLevel(registries, pickaxe,
                Identifier.fromNamespaceAndPath(Forgeweave.MODID, "luck"), 360).output();
        helper.assertValueEqual(lucky.getEnchantmentLevel(fortune), 6, "Luck VI alone");
        lucky.enchant(fortune, 3);

        helper.assertValueEqual(lucky.getEnchantmentLevel(fortune), 9, "the two add up");
        helper.assertValueEqual(GrantedEnchantments.withStored(lucky).getLevel(fortune), 9, "for the loot predicates too");
        String name = Enchantment.getFullname(fortune, 1).getString().split(" ")[0];
        long lines = lucky.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL).stream()
                .filter(line -> line.getString().startsWith(name)).count();
        helper.assertValueEqual(lines, 1L, "Fortune tooltip lines");
        helper.succeed();
    }

    /**
     * Vanilla's loot tables are what actually pay out a granted enchantment. Fortune reads through
     * {@code EnchantmentHelper}; Silk Touch reads through a {@code match_tool} predicate.
     */
    @GameTest(template = "empty")
    public static void grantedEnchantmentsPayOutInRealBlockDrops(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(1, 1, 1);
        var registries = helper.getLevel().registryAccess();
        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack silky = ToolAssembly.pickaxe(helper, player, pos, "iron", "wood", "wood");
        GrantedEnchantments.upgrade(silky, enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);

        helper.assertTrue(Block.getDrops(Blocks.STONE.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos),
                null, player, silky).stream().anyMatch(drop -> drop.is(Items.STONE)),
                "a granted Silk Touch must make stone drop stone");

        ItemStack lucky = ToolAssembly.pickaxe(helper, player, pos, "iron", "wood", "wood");
        GrantedEnchantments.upgrade(lucky, enchantments.getOrThrow(Enchantments.FORTUNE), 6);
        int diamonds = 0;
        for (int i = 0; i < 40; i++) {
            diamonds += Block.getDrops(Blocks.DIAMOND_ORE.defaultBlockState(), helper.getLevel(),
                    helper.absolutePos(pos), null, player, lucky).stream().mapToInt(ItemStack::getCount).sum();
        }
        helper.assertTrue(diamonds > 80, "Fortune VI must multiply the drop, 40 ores paid " + diamonds);
        helper.succeed();
    }

    /** A modifier's grant on an armor piece has to read back the same way a tool's does. */
    @GameTest(template = "empty")
    public static void armorReadsItsGrantedEnchantments(GameTestHelper helper) {
        var frostWalker = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FROST_WALKER);
        ItemStack boots = new ItemStack(ForgeweaveItems.ARMOR_HEAVY_BOOTS.get());
        GrantedEnchantments.upgrade(boots, frostWalker, 2);

        helper.assertValueEqual(boots.getEnchantmentLevel(frostWalker), 2, "the granted level on the boots");
        helper.assertFalse(boots.isEnchanted(), "a grant must not read as a table enchantment");
        helper.succeed();
    }

    /** A tool from before the grants moved: its modifier's Fortune moves over, a table's higher level stays. */
    @GameTest(template = "empty")
    public static void oldToolsMoveTheirGrantsOffTheStoredEnchantments(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(1, 1, 1);
        var registries = helper.getLevel().registryAccess();
        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var fortune = enchantments.getOrThrow(Enchantments.FORTUNE);
        var efficiency = enchantments.getOrThrow(Enchantments.EFFICIENCY);
        ItemStack old = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        old.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(
                new ModifierEntry(Identifier.fromNamespaceAndPath(Forgeweave.MODID, "luck"), 60)));
        old.remove(ForgeweaveDataComponents.GRANTED_ENCHANTMENTS.get());
        old.enchant(fortune, 1);
        old.enchant(efficiency, 2);

        GrantedEnchantments.migrate(registries, old);

        helper.assertValueEqual(old.get(DataComponents.ENCHANTMENTS).getLevel(fortune), 0, "stored Fortune moved");
        helper.assertValueEqual(old.getEnchantmentLevel(fortune), 1, "the tool still has its Fortune");
        helper.assertValueEqual(old.get(DataComponents.ENCHANTMENTS).getLevel(efficiency), 2, "a table's enchantment stays");
        helper.succeed();
    }

    /**
     * T54's core regression: the table accepted the tool and then offered it nothing, because a
     * tool's enchantment value stayed at vanilla {@code Item}'s 0 and
     * {@code EnchantmentHelper#getEnchantmentCost} returns 0 for anything whose value is 0.
     */
    @GameTest(template = "empty")
    public static void tableOffersEnchantmentsWhenFlagOn(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            ItemStack pickaxe = assembledPickaxe(helper);

            helper.assertTrue(enchantmentValue(pickaxe) > 0,
                    "a Forgeweave tool needs a non-zero enchantment value or the table offers nothing, got "
                            + enchantmentValue(pickaxe));
            List<EnchantmentInstance> offers =
                    EnchantmentHelper.getAvailableEnchantmentResults(30, pickaxe, tableEnchantments(helper));
            helper.assertTrue(!offers.isEmpty(),
                    "an enchanting table should have something to offer an assembled pickaxe while the flag is on");

            helper.succeed();
        } finally {
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
    }

    /**
     * Parity audit T81 (issue #512): upstream {@code TinkersItem#isBookEnchantable} refuses an
     * anvil-applied enchanted book unconditionally, but Forgeweave's tools are gated on
     * {@code allowVanillaEnchanting} rather than always-off (issue #13 already makes that call for
     * the enchanting table), so the anvil path follows the same flag -- otherwise a player with the
     * flag off could still smuggle enchantments in through the anvil.
     */
    @GameTest(template = "empty")
    public static void bookRejectedWhenFlagOff(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        ItemStack pickaxe = assembledPickaxe(helper);

        helper.assertFalse(((ToolItem) pickaxe.getItem()).isBookEnchantable(pickaxe, new ItemStack(Items.ENCHANTED_BOOK))
                        || pickaxe.supportsEnchantment(unbreaking(helper)),
                "a Forgeweave tool should refuse an enchanted book at the anvil while allowVanillaEnchanting is off");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bookAcceptedWhenFlagOn(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            ItemStack pickaxe = assembledPickaxe(helper);

            helper.assertTrue(((ToolItem) pickaxe.getItem()).isBookEnchantable(pickaxe, new ItemStack(Items.ENCHANTED_BOOK))
                            && pickaxe.supportsEnchantment(unbreaking(helper)),
                    "a Forgeweave tool should accept an enchanted book at the anvil while allowVanillaEnchanting is on");

            helper.succeed();
        } finally {
            // Restore the CONTEXT.md default so later tests don't inherit this test's flag state.
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
    }

    /** The same query with the flag off must come back empty -- 1.12's enchantability 0. */
    @GameTest(template = "empty")
    public static void tableOffersNothingWhenFlagOff(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        ItemStack pickaxe = assembledPickaxe(helper);

        helper.assertFalse(tableOpens(helper, pickaxe),
                "every enchanting table row must come back at level 0 for a Forgeweave tool while the flag is off");
        helper.assertTrue(EnchantmentHelper.getAvailableEnchantmentResults(30, pickaxe, tableEnchantments(helper)).isEmpty(),
                "an enchanting table must offer a Forgeweave tool nothing while allowVanillaEnchanting is off");

        helper.succeed();
    }

    /**
     * The item-tag half, one probe per tool family: a pickaxe takes mining enchantments, a broadsword
     * sword ones, a bludgeon the mace-shaped weapon ones, both launchers bow ones. Bare stacks -- tag
     * membership is a property of the item, not of an assembled tool's components.
     */
    @GameTest(template = "empty")
    public static void toolsJoinTheVanillaEnchantableTags(GameTestHelper helper) {
        assertTagged(helper, ForgeweaveItems.TOOL_PICKAXE.get(), ItemTags.MINING_ENCHANTABLE);
        assertTagged(helper, ForgeweaveItems.TOOL_PICKAXE.get(), ItemTags.MINING_LOOT_ENCHANTABLE);
        assertTagged(helper, ForgeweaveItems.TOOL_PICKAXE.get(), ItemTags.DURABILITY_ENCHANTABLE);
        // VANISHING_ENCHANTABLE is `#durability_enchantable` plus a few extras upstream of us, so
        // durability membership is what carries Curse of Vanishing too.
        assertTagged(helper, ForgeweaveItems.TOOL_PICKAXE.get(), ItemTags.VANISHING_ENCHANTABLE);
        assertTagged(helper, ForgeweaveItems.TOOL_BROADSWORD.get(), ItemTags.MELEE_WEAPON_ENCHANTABLE);
        assertTagged(helper, ForgeweaveItems.TOOL_BROADSWORD.get(), ItemTags.SWEEPING_ENCHANTABLE);
        assertTagged(helper, ForgeweaveItems.TOOL_BROADSWORD.get(), ItemTags.SHARP_WEAPON_ENCHANTABLE);
        // Fire Aspect's tag is `#melee_weapon` plus the mace, so the swords ride in on the above.
        assertTagged(helper, ForgeweaveItems.TOOL_BROADSWORD.get(), ItemTags.FIRE_ASPECT_ENCHANTABLE);
        assertTagged(helper, ForgeweaveItems.TOOL_FRYING_PAN.get(), ItemTags.WEAPON_ENCHANTABLE);
        assertTagged(helper, ForgeweaveItems.TOOL_SHORTBOW.get(), ItemTags.BOW_ENCHANTABLE);
        assertTagged(helper, ForgeweaveItems.TOOL_CROSSBOW.get(), ItemTags.BOW_ENCHANTABLE);

        helper.succeed();
    }

    /**
     * The anvil half. 1.12's {@code TinkersItem#isBookEnchantable} refuses every enchanted book
     * unconditionally, so the flag has to gate {@code supportsEnchantment} too -- otherwise a tool in
     * a vanilla tag family would take books from an anvil in a default (OFF) world, which is the one
     * thing 1.12 is explicit about.
     */
    @GameTest(template = "empty")
    public static void anvilBooksFollowTheFlag(GameTestHelper helper) {
        Holder<Enchantment> unbreaking = unbreaking(helper);
        ItemStack pickaxe = ForgeweaveItems.TOOL_PICKAXE.get().getDefaultInstance();

        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        helper.assertFalse(pickaxe.supportsEnchantment(unbreaking),
                "an anvil must not apply an enchanted book to a Forgeweave tool while the flag is off");

        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            helper.assertTrue(pickaxe.supportsEnchantment(unbreaking),
                    "an anvil should apply an enchanted book to a Forgeweave tool while the flag is on");
            helper.succeed();
        } finally {
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
    }

    /**
     * T80 (parity audit checklist -- PR #362's own "honest limits" flagged this gap and left it for
     * later): {@code isEnchantable()} being true only means the enchanting table will *accept* the
     * item; it says nothing about whether the table has anything to *offer* it. A tool the config
     * accepts but that matches none of vanilla's own {@code enchantable/*} item tags (issue T33 -- no
     * Forgeweave tool joins any of them except the warmace's {@code mace_enchantable}, added for
     * wind_burst's modifier gate, {@link dev.gkissel.forgeweave.data.ForgeweaveItemTagsProvider}) would
     * sit in the table with three empty, unusable slots. This drives the exact candidate list
     * {@code EnchantmentMenu#getEnchantmentList} computes ({@link EnchantmentHelper#getAvailableEnchantmentResults},
     * over the real {@code #minecraft:in_enchanting_table} tag) against a real warmace and asserts it
     * is not one of those empty-offer traps: vanilla's own Density and Breach both key off
     * {@code #minecraft:enchantable/mace}, which the warmace already joins.
     */
    @GameTest(template = "empty")
    public static void warmaceIsOfferedRealEnchantmentsWhenFlagOn(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            BlockPos pos = new BlockPos(1, 1, 1);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack warmace = ToolAssembly.assembleAtForge(helper, player, pos,
                    ToolAssembly.entryFor(ForgeweaveItems.TOOL_WARMACE.get()), List.of("wood", "stone", "wood"));

            helper.assertTrue(warmace.isEnchantable() && tableOpens(helper, warmace),
                    "a warmace must be accepted by the enchanting table while allowVanillaEnchanting is on");

            Registry<Enchantment> enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            List<EnchantmentInstance> offered = EnchantmentHelper.getAvailableEnchantmentResults(15, warmace,
                    enchantments.get(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow().stream());

            helper.assertTrue(
                    offered.stream().anyMatch(instance -> instance.enchantment().is(Enchantments.DENSITY)
                            || instance.enchantment().is(Enchantments.BREACH)),
                    "the enchanting table must actually offer a warmace one of vanilla's own mace enchantments "
                            + "(Density/Breach), not just accept it into the slot; got " + offered);

            helper.succeed();
        } finally {
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
    }

    // --------------------------------------------------------------- #593: per-material value

    /**
     * Issue #593 (maintainer decision on PR #574's open question): the enchantment value is the
     * material's, averaged across the tool's parts, not the flat iron 14 T54 shipped. Driven through
     * {@link ItemStack#getEnchantmentValue()} on real Tool-Station output, so the component the
     * station bakes is what is being read.
     *
     * <p>Shipped values: paper 22 (vanilla gold's), stone 5 (vanilla stone's). An all-paper pickaxe
     * is worth 22, an all-stone one 5, and a paper head on a stone body (22+5+5)/3 = 11.
     */
    @GameTest(template = "empty")
    public static void enchantmentValueFollowsTheMaterialsWhenFlagOn(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            assertValue(helper, pickaxe(helper, "paper", "paper", "paper"), 22, "an all-paper pickaxe");
            assertValue(helper, pickaxe(helper, "stone", "stone", "stone"), 5, "an all-stone pickaxe");
            assertValue(helper, pickaxe(helper, "paper", "stone", "stone"), 11,
                    "a paper-headed, stone-bodied pickaxe ((22 + 5 + 5) / 3)");

            helper.succeed();
        } finally {
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
    }

    /** The flag still wins: material or not, an OFF world offers a tool nothing. */
    @GameTest(template = "empty")
    public static void materialEnchantabilityStaysGatedByTheFlag(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        ItemStack paper = pickaxe(helper, "paper", "paper", "paper");
        // 26.1: the value is the static minecraft:enchantable component, so it stays the material's;
        // the flag closes the table instead, every row forced to level 0 (ToolItem#onEnchantmentLevelSet).
        assertValue(helper, paper, 22, "an all-paper pickaxe while allowVanillaEnchanting is off");
        helper.assertFalse(tableOpens(helper, paper),
                "an all-paper pickaxe must still be offered nothing while allowVanillaEnchanting is off");
        helper.succeed();
    }

    /**
     * What the value is <em>for</em>: {@code EnchantmentHelper#selectEnchantment} rolls the level it
     * enchants at as {@code level + 1 + rand(v/4 + 1) + rand(v/4 + 1)}, so a higher-enchantability
     * material reaches enchantments a lower one cannot. Asserted over a fixed seed sequence rather
     * than one roll, because any single roll can tie -- what has to differ is the reachable ceiling.
     */
    @GameTest(template = "empty")
    public static void betterMaterialReachesStrongerEnchantments(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            ItemStack paper = pickaxe(helper, "paper", "paper", "paper");
            ItemStack stone = pickaxe(helper, "stone", "stone", "stone");

            int paperBest = bestOfferedLevel(helper, paper);
            int stoneBest = bestOfferedLevel(helper, stone);
            helper.assertTrue(paperBest > stoneBest,
                    "the table should offer a paper pickaxe (enchantability 22) stronger enchantments than a "
                            + "stone one (5); got best levels " + paperBest + " vs " + stoneBest);

            helper.succeed();
        } finally {
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
    }

    /**
     * The strongest enchantment level the table's top slot ever offers this stack over 200 fixed
     * seeds -- the real {@code EnchantmentMenu} path ({@code selectEnchantment} over the
     * {@code #minecraft:in_enchanting_table} tag), which is where the enchantment value is read.
     */
    private static int bestOfferedLevel(GameTestHelper helper, ItemStack stack) {
        int best = 0;
        for (int seed = 0; seed < 200; seed++) {
            List<EnchantmentInstance> offers = EnchantmentHelper.selectEnchantment(
                    RandomSource.create(seed), stack, 30, tableEnchantments(helper));
            for (EnchantmentInstance offer : offers) {
                best = Math.max(best, offer.level());
            }
        }
        return best;
    }

    private static void assertValue(GameTestHelper helper, ItemStack stack, int expected, String what) {
        helper.assertTrue(enchantmentValue(stack) == expected,
                what + " should have enchantment value " + expected + ", got " + enchantmentValue(stack));
    }

    private static ItemStack pickaxe(GameTestHelper helper, String head, String binding, String handle) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        return ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), head, binding, handle);
    }

    private static void assertTagged(GameTestHelper helper, Item item, TagKey<Item> tag) {
        helper.assertTrue(item.getDefaultInstance().is(tag), item + " should be in " + tag.location());
    }

    /** The enchantments a table can draw from -- {@code EnchantmentMenu#getEnchantmentList}'s set. */
    private static Stream<Holder<Enchantment>> tableEnchantments(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(EnchantmentTags.IN_ENCHANTING_TABLE).stream().flatMap(holders -> holders.stream());
    }

    /**
     * The enchantment value the table and {@code EnchantmentHelper} read since 1.21.5: the stack's
     * {@code minecraft:enchantable} component, 0 without one (vanilla's own fallback).
     */
    private static int enchantmentValue(ItemStack stack) {
        Enchantable enchantable = stack.get(DataComponents.ENCHANTABLE);
        return enchantable == null ? 0 : enchantable.value();
    }

    /**
     * The enchanting table's three rows for {@code stack}, exactly as {@code EnchantmentMenu#slotsChanged}
     * computes them with fifteen bookshelves: vanilla's cost, zeroed below the row's floor, then handed
     * to NeoForge's {@code EnchantmentLevelSetEvent}, which is where Forgeweave's config gate closes
     * the table since 1.21.5 retired {@code Item#isEnchantable}.
     */
    static boolean tableOpens(GameTestHelper helper, ItemStack stack) {
        if (!stack.isEnchantable()) {
            return false;
        }
        RandomSource random = RandomSource.create(0L);
        boolean open = false;
        for (int row = 0; row < 3; row++) {
            int cost = EnchantmentHelper.getEnchantmentCost(random, row, 15, stack);
            if (cost < row + 1) {
                cost = 0;
            }
            cost = EventHooks.onEnchantmentLevelSet(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), row, 15, stack, cost);
            open |= cost > 0;
        }
        return open;
    }

    private static Holder<Enchantment> unbreaking(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
    }

    private static ItemStack assembledPickaxe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        return ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
    }
}