package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
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
            // Restore the CONTEXT.md default so later tests don't inherit this test's flag state.
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

    private static ItemStack assembledPickaxe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        return ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
    }
}
