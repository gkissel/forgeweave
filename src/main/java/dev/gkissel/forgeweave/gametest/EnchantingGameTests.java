package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

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

        helper.assertFalse(pickaxe.isEnchantable(),
                "a Forgeweave tool should be rejected by the enchanting table while allowVanillaEnchanting is off");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void toolAcceptedWhenFlagOn(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            ItemStack pickaxe = assembledPickaxe(helper);

            helper.assertTrue(pickaxe.isEnchantable(),
                    "a Forgeweave tool should be accepted by the enchanting table while allowVanillaEnchanting is on");

            helper.succeed();
        } finally {
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
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

            helper.assertTrue(pickaxe.getEnchantmentValue() > 0,
                    "a Forgeweave tool needs a non-zero enchantment value or the table offers nothing, got "
                            + pickaxe.getEnchantmentValue());
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

        helper.assertFalse(pickaxe.isBookEnchantable(new ItemStack(Items.ENCHANTED_BOOK)),
                "a Forgeweave tool should refuse an enchanted book at the anvil while allowVanillaEnchanting is off");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bookAcceptedWhenFlagOn(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            ItemStack pickaxe = assembledPickaxe(helper);

            helper.assertTrue(pickaxe.isBookEnchantable(new ItemStack(Items.ENCHANTED_BOOK)),
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

        helper.assertTrue(pickaxe.getEnchantmentValue() == 0,
                "a Forgeweave tool's enchantment value must be 0 while the flag is off, got "
                        + pickaxe.getEnchantmentValue());
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
        assertTagged(helper, ForgeweaveItems.TOOL_BROADSWORD.get(), ItemTags.SWORD_ENCHANTABLE);
        assertTagged(helper, ForgeweaveItems.TOOL_BROADSWORD.get(), ItemTags.SHARP_WEAPON_ENCHANTABLE);
        // Fire Aspect's tag is `#sword_enchantable` plus the mace, so the swords ride in on the above.
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
        Holder<Enchantment> unbreaking = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
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

            helper.assertTrue(warmace.isEnchantable(),
                    "a warmace must be accepted by the enchanting table while allowVanillaEnchanting is on");

            Registry<Enchantment> enchantments = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            List<EnchantmentInstance> offered = EnchantmentHelper.getAvailableEnchantmentResults(15, warmace,
                    enchantments.getTag(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow().stream());

            helper.assertTrue(
                    offered.stream().anyMatch(instance -> instance.enchantment.is(Enchantments.DENSITY)
                            || instance.enchantment.is(Enchantments.BREACH)),
                    "the enchanting table must actually offer a warmace one of vanilla's own mace enchantments "
                            + "(Density/Breach), not just accept it into the slot; got " + offered);

            helper.succeed();
        } finally {
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
    }

    private static void assertTagged(GameTestHelper helper, Item item, TagKey<Item> tag) {
        helper.assertTrue(item.getDefaultInstance().is(tag), item + " should be in " + tag.location());
    }

    /** The enchantments a table can draw from -- {@code EnchantmentMenu#getEnchantmentList}'s set. */
    private static Stream<Holder<Enchantment>> tableEnchantments(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getTag(EnchantmentTags.IN_ENCHANTING_TABLE).stream().flatMap(holders -> holders.stream());
    }

    private static ItemStack assembledPickaxe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        return ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
    }
}