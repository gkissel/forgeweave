package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * docs/SCOPE.md M3.2 issue #228's verification: one test per mining/durability-economy trait,
 * exercising the behavior that distinguishes it. Like {@link MetalTraitGameTests}, most tools here
 * are assembled by hand ({@link #pickaxe}) because the materials that will grant these traits
 * (obsidian, prismarine, paper, sponge, firewood, ...) land in the M3.2 roster batches, not this
 * one. The two behaviors that only exist on the assembly path -- writable's slot math and squeaky's
 * assembly-time Silk Touch grant -- go through a real Tool Station instead, using the shipped
 * {@code paper.json}/{@code sponge.json} materials (issue #231; they used GameTest-only stand-ins
 * before the roster landed).
 *
 * <p>Magnitudes are upstream 1.12's, cited per trait in {@code ForgeweaveTraits}; deviations forced
 * by the port are cited there too and in the PR body.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MiningTraitGameTests {

    /**
     * Obsidian -&gt; {@code forgeweave:duritos}: per durability loss, 10% double cost, 40% no cost,
     * 50% unchanged -- so a cost of 1 only ever resolves to 0, 1 or 2, and all three occur.
     */
    @GameTest(template = "empty")
    public static void duritosSometimesDoublesAndOftenNegatesDurabilityCost(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("duritos")), 1000, 1.0F, 1.0F);
        RandomSource random = helper.getLevel().getRandom();

        int doubled = 0;
        int negated = 0;
        int unchanged = 0;
        for (int i = 0; i < 600; i++) {
            int cost = ForgeweaveTraits.durabilityDamage(pickaxe, random, 1);
            switch (cost) {
                case 2 -> doubled++;
                case 0 -> negated++;
                case 1 -> unchanged++;
                default -> helper.fail("duritos on a cost of 1 must yield 0, 1 or 2, got " + cost);
            }
        }
        // 600 rolls at 10%/40%/50%: the chance any bucket stays empty is < 1e-27.
        helper.assertTrue(doubled > 0 && negated > 0 && unchanged > 0,
                "expected all three outcomes in 600 rolls, got doubled=" + doubled + " negated=" + negated
                        + " unchanged=" + unchanged);
        helper.succeed();
    }

    /** Prismarine, head only -&gt; {@code forgeweave:jagged}: attack damage rises as durability drops. */
    @GameTest(template = "empty")
    public static void jaggedGrowsAttackDamageAsDurabilityDrops(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("jagged")), 200, 1.0F, 3.0F);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        float fresh = ForgeweaveTraits.bonusDamageAgainst(pickaxe, target, 3.0F);
        helper.assertTrue(fresh == 0.0F, "no bonus at full durability, got " + fresh);

        pickaxe.setDamageValue(100);
        double expected = Math.log(100 / 72.0 + 1.0) * 2.0;
        float worn = ForgeweaveTraits.bonusDamageAgainst(pickaxe, target, 3.0F);
        helper.assertTrue(Math.abs(worn - expected) < 0.001, "expected " + expected + ", got " + worn);

        target.discard();
        helper.succeed();
    }

    /**
     * Prismarine -&gt; {@code forgeweave:aquadynamic}: even dry and under a clear sky the coeff starts
     * at 1, i.e. the pre-trait break speed is doubled (upstream's counter to water's mining penalty;
     * the in-water +5.5 and rain terms ride the same one formula).
     */
    @GameTest(template = "empty")
    public static void aquadynamicDoublesBreakSpeedEvenOnDryLand(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("aquadynamic")), 100, 2.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        helper.assertTrue(!player.level().isRaining(), "test assumes a clear sky");

        PlayerEvent.BreakSpeed event = new PlayerEvent.BreakSpeed(player, Blocks.STONE.defaultBlockState(), 2.0F,
                helper.absolutePos(new BlockPos(1, 1, 1)));
        ForgeweaveTraits.onBreakSpeed(event);

        helper.assertTrue(Math.abs(event.getNewSpeed() - 4.0F) < 0.001F,
                "expected 2 + 2 * 1 = 4 on dry land, got " + event.getNewSpeed());
        helper.succeed();
    }

    /**
     * Netherrack, head only -&gt; {@code forgeweave:aridiculous}: break speed and attack damage scale
     * with the biome's heat/dryness. Expected values are recomputed here from the test position's own
     * biome, so the assertion holds in whatever biome the GameTest world uses.
     */
    @GameTest(template = "empty")
    public static void aridiculousScalesMiningAndDamageWithBiomeHeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        ItemStack pickaxe = pickaxe(List.of(traitId("aridiculous")), 100, 2.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);

        // Upstream TraitAridiculous#calcAridiculousness, recomputed from the biome the trait reads.
        Biome biome = level.getBiome(player.blockPosition()).value();
        float rainfall = biome.getModifiedClimateSettings().downfall();
        float rain = level.isRaining() ? rainfall / 2.0F : 0.0F;
        float calc = (float) (Math.pow(1.25, 3.0 * (0.5F + biome.getBaseTemperature() - rainfall)) - 1.25) - rain;

        PlayerEvent.BreakSpeed event = new PlayerEvent.BreakSpeed(player, Blocks.STONE.defaultBlockState(), 2.0F, pos);
        ForgeweaveTraits.onBreakSpeed(event);
        float expectedSpeed = 2.0F + 2.0F * (calc / 10.0F);
        helper.assertTrue(Math.abs(event.getNewSpeed() - expectedSpeed) < 0.001F,
                "expected break speed " + expectedSpeed + ", got " + event.getNewSpeed());

        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        float bonus = ForgeweaveTraits.bonusDamageAgainst(pickaxe, target, 3.0F);
        helper.assertTrue(Math.abs(bonus - 2.0F * calc) < 0.01F,
                "expected bonus damage " + 2.0F * calc + ", got " + bonus);

        target.discard();
        helper.succeed();
    }

    /**
     * Knightslime, head only -&gt; {@code forgeweave:crumbling}: blocks that need no tool break at
     * {@code speed * (toolMiningSpeed * 0.5)}; blocks that need one are untouched.
     */
    @GameTest(template = "empty")
    public static void crumblingSpeedsUpOnlyBlocksThatNeedNoTool(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("crumbling")), 100, 4.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));

        PlayerEvent.BreakSpeed dirt = new PlayerEvent.BreakSpeed(player, Blocks.DIRT.defaultBlockState(), 2.0F, pos);
        ForgeweaveTraits.onBreakSpeed(dirt);
        helper.assertTrue(Math.abs(dirt.getNewSpeed() - 4.0F) < 0.001F,
                "expected 2 * (4 * 0.5) = 4 on a no-tool block, got " + dirt.getNewSpeed());

        PlayerEvent.BreakSpeed stone = new PlayerEvent.BreakSpeed(player, Blocks.STONE.defaultBlockState(), 2.0F, pos);
        ForgeweaveTraits.onBreakSpeed(stone);
        helper.assertTrue(stone.getNewSpeed() == 2.0F,
                "crumbling must leave tool-requiring blocks alone, got " + stone.getNewSpeed());
        helper.succeed();
    }

    /**
     * Knightslime -&gt; {@code forgeweave:unnatural}: +1 break speed per tier level the tool sits
     * above the block's requirement, nothing at or below it.
     */
    @GameTest(template = "empty")
    public static void unnaturalAddsSpeedPerTierAboveTheBlocksRequirement(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));

        // Diamond-tier head (ladder index 3): dirt requires nothing (0) -> +3; iron ore requires
        // stone (1) -> +2.
        ItemStack diamondTier = pickaxe(List.of(traitId("unnatural")), 100, 2.0F, 1.0F,
                "incorrect_for_netherite_tool");
        player.setItemInHand(InteractionHand.MAIN_HAND, diamondTier);
        PlayerEvent.BreakSpeed dirt = new PlayerEvent.BreakSpeed(player, Blocks.DIRT.defaultBlockState(), 2.0F, pos);
        ForgeweaveTraits.onBreakSpeed(dirt);
        helper.assertTrue(dirt.getNewSpeed() == 5.0F, "expected 2 + 3 on dirt, got " + dirt.getNewSpeed());
        PlayerEvent.BreakSpeed ore = new PlayerEvent.BreakSpeed(player, Blocks.IRON_ORE.defaultBlockState(), 2.0F, pos);
        ForgeweaveTraits.onBreakSpeed(ore);
        helper.assertTrue(ore.getNewSpeed() == 4.0F, "expected 2 + 2 on iron ore, got " + ore.getNewSpeed());

        // Wood-tier head (ladder index 0): no block sits below it, so nothing changes.
        ItemStack woodTier = pickaxe(List.of(traitId("unnatural")), 100, 2.0F, 1.0F, "incorrect_for_stone_tool");
        player.setItemInHand(InteractionHand.MAIN_HAND, woodTier);
        PlayerEvent.BreakSpeed level0 = new PlayerEvent.BreakSpeed(player, Blocks.DIRT.defaultBlockState(), 2.0F, pos);
        ForgeweaveTraits.onBreakSpeed(level0);
        helper.assertTrue(level0.getNewSpeed() == 2.0F,
                "a wood-tier tool is never above dirt's requirement, got " + level0.getNewSpeed());
        helper.succeed();
    }

    /**
     * Bronze -&gt; {@code forgeweave:dense}: never cheaper at full durability; at 90% worn, a
     * {@code (0.75 * 0.9)^3} (~31%) chance to pay {@code cost - max(cost / 2, 1)}.
     */
    @GameTest(template = "empty")
    public static void denseSometimesHalvesDurabilityCostOnAWornTool(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("dense")), 1000, 1.0F, 1.0F);
        RandomSource random = helper.getLevel().getRandom();

        for (int i = 0; i < 100; i++) {
            int cost = ForgeweaveTraits.durabilityDamage(pickaxe, random, 4);
            helper.assertTrue(cost == 4, "dense must never trigger at full durability, got " + cost);
        }

        pickaxe.setDamageValue(900);
        int full = 0;
        int reduced = 0;
        for (int i = 0; i < 300; i++) {
            int cost = ForgeweaveTraits.durabilityDamage(pickaxe, random, 4);
            if (cost == 4) {
                full++;
            } else if (cost == 2) {
                reduced++;
            } else {
                helper.fail("dense on a cost of 4 must yield 4 or 2, got " + cost);
            }
        }
        // 300 rolls at ~31%: the chance either bucket stays empty is < 1e-47.
        helper.assertTrue(full > 0 && reduced > 0,
                "expected both outcomes in 300 worn rolls, got full=" + full + " reduced=" + reduced);
        helper.succeed();
    }

    /**
     * Paper -&gt; {@code forgeweave:writable}/{@code writable2} (docs/SCOPE.md M3.2 acceptance: "an
     * all-paper tool shows +2 modifier slots"): assembled through a real Tool Station from the
     * shipped {@code paper.json} (issue #231), whose head scope carries {@code writable2} and general
     * scope {@code writable} -- +1 each, +2 together.
     */
    @GameTest(template = "empty")
    public static void writablePairGrantsTwoExtraModifierSlotsOnAnAllPaperTool(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1),
                "paper", "paper", "paper");
        helper.assertTrue(!pickaxe.isEmpty(), "the station must assemble the all-writable pickaxe");

        List<ResourceLocation> traits = pickaxe.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(traits != null && traits.contains(traitId("writable")) && traits.contains(traitId("writable2")),
                "expected both writable ids on the tool, got " + traits);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == ForgeweaveModifiers.DEFAULT_SLOTS + 2,
                "expected " + (ForgeweaveModifiers.DEFAULT_SLOTS + 2) + " free slots on an all-paper tool, got "
                        + ForgeweaveModifiers.freeSlots(pickaxe));
        helper.succeed();
    }

    /**
     * Sponge -&gt; {@code forgeweave:squeaky} (docs/SCOPE.md M3.2 acceptance: "squeaky silk-touch +
     * zero-damage"): a tool station-assembled from the shipped {@code sponge.json} (issue #231)
     * carries vanilla Silk Touch from assembly, its attack-damage attribute is zero, and the combat
     * seam zeroes the blow itself.
     */
    @GameTest(template = "empty")
    public static void squeakyGrantsSilkTouchAndZeroesAttackDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1),
                "sponge", "sponge", "sponge");
        helper.assertTrue(!pickaxe.isEmpty(), "the station must assemble the all-squeaky pickaxe");

        ItemEnchantments enchantments = pickaxe.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        boolean silkTouch = enchantments.keySet().stream().anyMatch(holder -> holder.is(Enchantments.SILK_TOUCH));
        helper.assertTrue(silkTouch, "assembly must grant vanilla Silk Touch, got " + enchantments);

        ItemAttributeModifiers modifiers = pickaxe.getItem().getDefaultAttributeModifiers(pickaxe);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().is(Attributes.ATTACK_DAMAGE.unwrapKey().orElseThrow())) {
                helper.assertTrue(entry.modifier().amount() == 0.0,
                        "squeaky's attack-damage modifier must be 0, got " + entry.modifier().amount());
            }
        }

        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        CombatHit hit = new CombatHit(level, pickaxe, player, target, level.damageSources().playerAttack(player));
        float damage = ForgeweaveTraits.COMBAT_SEAM.preHit(hit, 5.0F, 5.0F);
        helper.assertTrue(damage == 0.0F, "squeaky must zero the blow itself, got " + damage);

        target.discard();
        helper.succeed();
    }

    /**
     * Firewood -&gt; {@code forgeweave:autosmelt} (docs/SCOPE.md M3.2 acceptance: "autosmelt drop
     * replacement"): the trait rides Searing's exact smelt path, so a mined drop becomes its furnace
     * result, count preserved -- {@code ModifierGameTests#searingSmeltsWhatItMines} with the trait in
     * place of the modifier.
     */
    @GameTest(template = "empty")
    public static void autosmeltReplacesDropsWithFurnaceResults(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("autosmelt")), 100, 1.0F, 1.0F);
        ItemEntity drop = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.IRON_ORE, 2));

        ForgeweaveModifiers.onBlockDrops(dropsEvent(helper, pickaxe, null, drop));

        helper.assertTrue(drop.getItem().is(Items.IRON_INGOT) && drop.getItem().getCount() == 2,
                "expected 2 iron ingot (the furnace result of iron ore), got " + drop.getItem());
        helper.succeed();
    }

    /** Builds a pickaxe {@code ItemStack} with the given traits/stats directly (see class javadoc). */
    private static ItemStack pickaxe(List<ResourceLocation> traits, int durability, float miningSpeed,
            float attackDamage) {
        return pickaxe(traits, durability, miningSpeed, attackDamage, "incorrect_for_stone_tool");
    }

    /** As {@link #pickaxe(List, int, float, float)}, with the head's tier tag chosen (unnatural). */
    private static ItemStack pickaxe(List<ResourceLocation> traits, int durability, float miningSpeed,
            float attackDamage, String incorrectForTool) {
        ToolItem toolItem = ForgeweaveItems.TOOL_PICKAXE.get();
        ToolStats.Stats stats = new ToolStats.Stats(durability, miningSpeed, attackDamage);
        Material head = new Material(
                new Material.Head(durability, miningSpeed, attackDamage),
                new Material.Handle(1.0F, 0),
                0,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace(incorrectForTool)),
                new Material.Traits(List.of(), List.of()),
                List.of(),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));

        ItemStack stack = new ItemStack(toolItem);
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), stats);
        stack.set(ForgeweaveDataComponents.TRAITS.get(), traits);
        stack.set(DataComponents.TOOL, toolItem.toolComponent(head, stats));
        stack.set(DataComponents.MAX_DAMAGE, durability);
        stack.set(DataComponents.DAMAGE, 0);
        return stack;
    }

    private static BlockDropsEvent dropsEvent(GameTestHelper helper, ItemStack tool, Entity breaker,
            ItemEntity... drops) {
        return new BlockDropsEvent(helper.getLevel(), BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null,
                new ArrayList<>(List.of(drops)), breaker, tool);
    }

    private static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }
}
