package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Issue #106 (M2-15 batch 1): luck, sharpness, diamond, emerald, one effect-exercising test each
 * (haste already shipped with #105 -- see {@code ModifierRecipeTest}). Every clone constant asserted
 * here is verified against tinkers-1.12 @ {@code c01173c0} (see {@code NOTICE.md}) before and after
 * implementation, same discipline as {@code ModifierRecipeTest}'s haste tests.
 */
class ModifierBatch1Test {

    private static final ToolStats.Stats PICKAXE_STATS = new ToolStats.Stats(160, 4.0F, 3.0F);
    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    // ------------------------------------------------------------------ sharpness (quartz)

    /**
     * Upstream {@code ModSharpness#applyEffect}: {@code +0.05 - 0.025 * attack / 10} below 10 damage,
     * plus a flat {@code +0.25} per completed level (72 quartz -- {@code TinkerModifiers}'
     * {@code new ModSharpness(72)}), undiminished.
     */
    @Test
    void sharpnessRaisesAttackDamageTheWayUpstreamDoes() {
        assertEquals(72, ForgeweaveModifiers.SHARPNESS.unitsPerLevel(), "upstream new ModSharpness(72)");
        assertEquals(3.0F + 0.05F - 0.025F * 3.0F / 10.0F, ForgeweaveModifiers.SHARPNESS.attackDamage(1, 3.0F, 3.0F), 1.0e-5F);
        assertTrue(ForgeweaveModifiers.SHARPNESS.attackDamage(72, 3.0F, 3.0F) > ForgeweaveModifiers.SHARPNESS.attackDamage(71, 3.0F, 3.0F),
                "every quartz is worth something, and the 72nd also completes a level");
        // A completed level adds a flat 0.25 on top of the per-quartz diminishing curve.
        float justBelowLevel = ForgeweaveModifiers.SHARPNESS.attackDamage(71, 3.0F, 3.0F);
        float atLevel = ForgeweaveModifiers.SHARPNESS.attackDamage(72, 3.0F, 3.0F);
        assertTrue(atLevel - justBelowLevel > 0.25F, "the 72nd quartz's own step plus the flat level bonus");
    }

    /**
     * Issue #295: upstream {@code ModSharpness#applyEffect} seeds its diminishing-returns curve from
     * {@code getOriginalToolStats().attack} -- the tool's untouched materials-derived stat -- never from
     * the running total other modifiers may already have folded in, then adds only the curve's own
     * delta on top of that running total:
     *
     * <pre>
     * ToolNBT toolData = TagUtil.getOriginalToolStats(rootCompound);
     * float attack = toolData.attack;
     * // ...simulate the curve `data.current` times, starting from toolData.attack...
     * attack -= toolData.attack;
     * attack += tag.getFloat(Tags.ATTACK);
     * tag.setFloat(Tags.ATTACK, attack);
     * </pre>
     *
     * <p>Base 8.0 attack (below the 10-damage threshold), one prior modifier already folded to a
     * running 9.0 (e.g. diamond's {@code +1}), one quartz applied: the correct seed is the untouched
     * 8.0, giving a per-quartz step of {@code 0.05 - 0.025*8.0/10 = 0.03}, landing at
     * {@code 9.0 + 0.03 = 9.03}. Seeding from the already-folded 9.0 instead (the bug) would give
     * {@code 0.05 - 0.025*9.0/10 = 0.0275}, landing at {@code 9.0275} -- a different, wrong number this
     * test would catch.
     */
    @Test
    void sharpnessSeedsItsCurveFromTheOriginalBaseNotTheRunningTotal() {
        float base = 8.0F;
        float runningAfterEarlierModifiers = 9.0F; // e.g. diamond's flat +1 already folded in
        assertEquals(9.03F, ForgeweaveModifiers.SHARPNESS.attackDamage(1, runningAfterEarlierModifiers, base), 1.0e-5F,
                "sharpness must seed its curve from the untouched base (8.0), not the running total (9.0)");
    }

    /**
     * End to end, through {@link ForgeweaveModifiers#effectiveStats}: diamond folds first, then
     * sharpness -- the same 8.0-base / 9.03-expected scenario as the unit test above, but exercised
     * through the real modifier list and {@code Tool Station}-shaped fixture rather than calling the
     * hook directly.
     */
    @Test
    void sharpnessAfterDiamondStillSeedsFromTheOriginalBase() {
        ToolStats.Stats base = new ToolStats.Stats(160, 4.0F, 8.0F);
        ItemStack tool = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        tool.set(ForgeweaveDataComponents.TOOL_STATS.get(), base);
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", "diamond"), 1),
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", "sharpness"), 1)));

        ToolStats.Stats effective = ForgeweaveModifiers.effectiveStats(tool);

        assertEquals(9.03F, effective.attackDamage(), 1.0e-5F,
                "diamond's +1 (8.0 -> 9.0) then one quartz seeded from the original 8.0 base -> 9.03");
    }

    /**
     * Parity audit T59 (issue #490): upstream {@code TinkerModifiers} registers sharpness on both
     * forms of quartz, {@code modSharpness.addItem("gemQuartz")} (1 unit) and
     * {@code modSharpness.addItem("blockQuartz", 1, 4)} (a block is worth the 4 gems it's crafted
     * from) -- Forgeweave shipped only the gem until now.
     */
    @Test
    void theShippedSharpnessRecipeAlsoAcceptsAQuartzBlockAtFourUnits() {
        ModifierRecipe recipe = sharpnessRecipe();

        assertEquals(1, recipe.reagentFor(new ItemStack(Items.QUARTZ)).units());
        assertEquals(4, recipe.reagentFor(new ItemStack(Items.QUARTZ_BLOCK)).units(),
                "upstream's addItem(\"blockQuartz\", 1, 4)");
    }

    /** End to end: applying quartz through the shipped recipe raises the tool's effective attack damage. */
    @Test
    void applyingQuartzRaisesEffectiveAttackDamage() {
        ItemStack tool = assembledPickaxe();

        ModifierApplication.Outcome outcome = ModifierApplication.apply(sharpnessRecipe(), tool, 72, 0);

        assertTrue(!outcome.output().isEmpty());
        ToolStats.Stats effective = ForgeweaveModifiers.effectiveStats(outcome.output());
        assertTrue(effective.attackDamage() > PICKAXE_STATS.attackDamage(),
                "72 quartz (one full level) must raise attack damage above the base 3.0");
        assertEquals(PICKAXE_STATS.durability(), effective.durability(), "sharpness must not touch durability");
    }

    // ------------------------------------------------------------------ luck (lapis lazuli)

    /**
     * Upstream {@code ModLuck}: {@code baseCount = 60}, 3 levels, granting Fortune to every harvest
     * tool and Looting to weapon tools up to the display level -- {@link Modifier#fortuneLevel}'s
     * contract is that it receives that already-resolved level, so LUCK's own hooks are a pass-through
     * and the real threshold logic is {@link ModifierRecipe#levelsReached}'s (tested below).
     */
    @Test
    void luckGrantsFortuneAndLootingAtWhateverLevelItIsGiven() {
        assertEquals(2, ForgeweaveModifiers.LUCK.fortuneLevel(2));
        assertEquals(ForgeweaveModifiers.LUCK.fortuneLevel(3), ForgeweaveModifiers.LUCK.lootingLevel(3),
                "upstream grants the same level of both enchantments");
    }

    /**
     * The shipped recipe matches {@code ModLuck}'s triangular schedule 1:1 (issue #106 review):
     * {@code LuckAspect#getMaxForLevel(level) = 60 * level * (level + 1) / 2} gives cumulative
     * thresholds 60/180/360 for levels 1/2/3, which {@code cost_per_level: [60, 120, 180]} reproduces.
     */
    @Test
    void theShippedLuckRecipeMatchesUpstreamsTriangularSchedule() {
        ModifierRecipe recipe = shippedRecipe("luck.json");

        assertEquals(1, recipe.cost());
        assertEquals(List.of(60, 120, 180), recipe.costPerLevel());
        assertEquals(360, recipe.maxLevel(), "upstream's cumulative total for level 3");
        assertTrue(recipe.reagent().test(new ItemStack(Items.LAPIS_LAZULI)));
    }

    /**
     * Parity audit T59 (issue #490): upstream {@code TinkerModifiers} registers luck on both forms of
     * lapis, {@code modLuck.addItem("gemLapis")} (1 unit) and
     * {@code modLuck.addItem("blockLapis", 1, 9)} (a block is worth the 9 gems it's crafted from) --
     * Forgeweave shipped only the gem until now.
     */
    @Test
    void theShippedLuckRecipeAlsoAcceptsALapisBlockAtNineUnits() {
        ModifierRecipe recipe = shippedRecipe("luck.json");

        assertEquals(1, recipe.reagentFor(new ItemStack(Items.LAPIS_LAZULI)).units());
        assertEquals(9, recipe.reagentFor(new ItemStack(Items.LAPIS_BLOCK)).units(),
                "upstream's addItem(\"blockLapis\", 1, 9)");
    }

    /**
     * The exact level-up boundaries {@code LuckAspect#getLevel} crosses at, mirrored by
     * {@link ModifierRecipe#levelsReached}: 0 below 60, 1 from 60 to 179, 2 from 180 to 359, 3 at 360+.
     */
    @Test
    void luckRecipeLevelsReachedMatchesUpstreamsThresholdsExactly() {
        ModifierRecipe recipe = shippedRecipe("luck.json");

        assertEquals(0, recipe.levelsReached(59), "one short of the first threshold");
        assertEquals(1, recipe.levelsReached(60), "the first threshold itself");
        assertEquals(1, recipe.levelsReached(179));
        assertEquals(2, recipe.levelsReached(180), "the second threshold");
        assertEquals(2, recipe.levelsReached(359));
        assertEquals(3, recipe.levelsReached(360), "the third threshold, upstream's maxLevel");
    }

    /** End to end: applying lapis through the shipped recipe grants Fortune at the right lapis counts. */
    @Test
    void applyingLapisGrantsFortuneAtTheTriangularThresholds() {
        ModifierRecipe recipe = shippedRecipe("luck.json");
        ItemStack tool = assembledPickaxe();

        ModifierApplication.Outcome fiftyNine = ModifierApplication.apply(recipe, tool, 59, 0);
        assertEquals(0, recipe.levelsReached(entryLevel(fiftyNine.output())), "not enough for Fortune I yet");

        ModifierApplication.Outcome sixty = ModifierApplication.apply(recipe, tool, 60, 0);
        assertEquals(1, recipe.levelsReached(entryLevel(sixty.output())));

        ModifierApplication.Outcome oneEighty = ModifierApplication.apply(recipe, tool, 180, 0);
        assertEquals(2, recipe.levelsReached(entryLevel(oneEighty.output())));
    }

    // ------------------------------------------------------------------ luck's growth-on-use (issue #296)

    private static final ResourceLocation LUCK_ID = ResourceLocation.fromNamespaceAndPath("forgeweave", "luck");

    /**
     * The seed for a roll that must land -- found by brute-force search rather than hand-computed, so
     * this test is correct regardless of {@link RandomSource#create}'s exact algorithm (same
     * deterministic-seed idiom as {@code combat.BeheadingGameTests#higherLevelRaisesDropChance}, which
     * uses a fixed seed rather than mocking {@link RandomSource}).
     */
    private static long seedThatRolls(boolean wantsSuccess) {
        for (long seed = 0; seed < 1_000_000; seed++) {
            if (ForgeweaveModifiers.rollsLuckGrowth(RandomSource.create(seed)) == wantsSuccess) {
                return seed;
            }
        }
        throw new AssertionError("no seed found rolling " + wantsSuccess + " in range");
    }

    /**
     * Upstream {@code ModLuck#rewardProgress}: {@code random.nextFloat() > 0.03f} returns early, i.e.
     * progress only on the complementary ~3% of rolls -- both outcomes must be reachable.
     */
    @Test
    void luckGrowthRollLandsAboutThreePercentOfTheTime() {
        assertTrue(ForgeweaveModifiers.rollsLuckGrowth(RandomSource.create(seedThatRolls(true))));
        assertFalse(ForgeweaveModifiers.rollsLuckGrowth(RandomSource.create(seedThatRolls(false))));
    }

    /** A successful roll adds exactly one raw application unit -- upstream's {@code data.current++}. */
    @Test
    void aSuccessfulRollAddsOneRawApplicationUnit() {
        ItemStack tool = bareLuckPickaxe();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(LUCK_ID, 5)));

        boolean grew = ForgeweaveModifiers.growLuckByOneUnit(tool, RandomSource.create(seedThatRolls(true)), 360);

        assertTrue(grew);
        assertEquals(6, ForgeweaveModifiers.entry(tool, LUCK_ID).level());
    }

    /** A missed roll must not touch the tool at all. */
    @Test
    void aMissedRollGrantsNoProgress() {
        ItemStack tool = bareLuckPickaxe();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(LUCK_ID, 5)));

        boolean grew = ForgeweaveModifiers.growLuckByOneUnit(tool, RandomSource.create(seedThatRolls(false)), 360);

        assertFalse(grew);
        assertEquals(5, ForgeweaveModifiers.entry(tool, LUCK_ID).level());
    }

    /**
     * Deliberate deviation from upstream (recorded in {@code ForgeweaveModifiers#growLuckByOneUnit}'s
     * javadoc): a tool that never had lapis applied must not spontaneously gain luck from a lucky roll.
     */
    @Test
    void aToolWithNoLuckEntryNeverGrowsOne() {
        ItemStack tool = bareLuckPickaxe();

        boolean grew = ForgeweaveModifiers.growLuckByOneUnit(tool, RandomSource.create(seedThatRolls(true)), 360);

        assertFalse(grew);
        assertTrue(ForgeweaveModifiers.of(tool).isEmpty());
    }

    /**
     * The 360 cap the shipped {@code luck.json} recipe encodes: autonomous growth must never push a
     * tool past what applying lapis by hand could, even on a landed roll.
     */
    @Test
    void growthNeverPushesPastTheRecipesCap() {
        ItemStack tool = bareLuckPickaxe();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(LUCK_ID, 360)));

        boolean grew = ForgeweaveModifiers.growLuckByOneUnit(tool, RandomSource.create(seedThatRolls(true)), 360);

        assertFalse(grew, "already at the cap; a landed roll must still be a no-op");
        assertEquals(360, ForgeweaveModifiers.entry(tool, LUCK_ID).level());
    }

    /**
     * Upstream {@code ModLuck#afterHit}: {@code (int) (damageDealt / 2f)} rolls per hit -- one per full
     * heart, none for a graze under 2 damage, and a heavy blow grants several in one swing.
     */
    @Test
    void luckGrowthRollsOncePerFullHeartOfDamageDealt() {
        assertEquals(0, ForgeweaveModifiers.luckGrowthRolls(1.9F), "a graze under 2 damage grants no roll");
        assertEquals(1, ForgeweaveModifiers.luckGrowthRolls(2.0F));
        assertEquals(1, ForgeweaveModifiers.luckGrowthRolls(3.5F), "a partial third heart still only counts two");
        assertEquals(5, ForgeweaveModifiers.luckGrowthRolls(10.0F), "a heavy blow grants several rolls in one swing");
    }

    // ------------------------------------------------------------------ diamond (1 diamond)

    /**
     * Upstream {@code ModDiamond}, whole: {@code +500} durability, a one-tier bump, {@code +1}
     * attack and {@code +0.5} mining speed (the stat half is issue #265's maintainer call).
     */
    @Test
    void diamondAddsFiveHundredDurabilityAndBumpsToolTier() {
        ItemStack tool = assembledPickaxe();

        ModifierApplication.Outcome outcome = ModifierApplication.apply(diamondRecipe(), tool, 1, 0);

        assertTrue(!outcome.output().isEmpty());
        ToolStats.Stats effective = ForgeweaveModifiers.effectiveStats(outcome.output());
        assertEquals(PICKAXE_STATS.durability() + 500, effective.durability());
        assertEquals(PICKAXE_STATS.durability() + 500, outcome.output().getMaxDamage(), "max_damage must be retuned too");
        assertEquals(PICKAXE_STATS.attackDamage() + 1.0F, effective.attackDamage(), 1e-6F,
                "upstream ModDiamond: data.attack += 1f (#265)");
        assertEquals(PICKAXE_STATS.miningSpeed() + 0.5F, effective.miningSpeed(), 1e-6F,
                "upstream ModDiamond: data.speed += 0.5f (#265)");
        assertEquals(1, ForgeweaveModifiers.tierIndexOf(denyRule(outcome.output()).blocks()),
                "the deny-drops tag must move one rung up the ladder");

        // A second application is rejected -- diamond's recipe max_level is 1 (one-shot, like upstream's SingleAspect).
        ModifierApplication.Outcome again = ModifierApplication.apply(diamondRecipe(), outcome.output(), 1, 0);
        assertTrue(again.output().isEmpty());
    }

    // ------------------------------------------------------------------ emerald (1 emerald)

    /** Upstream {@code ModEmerald}: {@code +50%} of the tool's untouched base durability, plus a one-tier bump capped below diamond's. */
    @Test
    void emeraldAddsHalfBaseDurabilityAndBumpsToolTierOneRungBelowDiamond() {
        ItemStack tool = assembledPickaxe();

        ModifierApplication.Outcome outcome = ModifierApplication.apply(emeraldRecipe(), tool, 1, 0);

        assertTrue(!outcome.output().isEmpty());
        ToolStats.Stats effective = ForgeweaveModifiers.effectiveStats(outcome.output());
        assertEquals(PICKAXE_STATS.durability() + PICKAXE_STATS.durability() / 2, effective.durability());
        assertEquals(1, ForgeweaveModifiers.tierIndexOf(denyRule(outcome.output()).blocks()));
    }

    /**
     * Diamond and emerald together: durability bonuses stack (each references the untouched base
     * independently -- {@link Modifier#durability}'s javadoc), and the tier ladder takes one bump per
     * modifier applied, same as upstream's two separate, sequential {@code harvestLevel++} calls.
     */
    @Test
    void diamondAndEmeraldStackDurabilityAndEachBumpsTheTierOnce() {
        ItemStack tool = assembledPickaxe();

        ModifierApplication.Outcome afterDiamond = ModifierApplication.apply(diamondRecipe(), tool, 1, 0);
        ModifierApplication.Outcome afterBoth = ModifierApplication.apply(emeraldRecipe(), afterDiamond.output(), 1, 0);

        ToolStats.Stats effective = ForgeweaveModifiers.effectiveStats(afterBoth.output());
        int expectedDurability = PICKAXE_STATS.durability() + 500 + PICKAXE_STATS.durability() / 2;
        assertEquals(expectedDurability, effective.durability(), "both bonuses reference the untouched base, so they simply add");
        // Wood tier (index 0) -> diamond bumps to 1 -> emerald bumps to 2 (its own cap), one rung each.
        assertEquals(2, ForgeweaveModifiers.tierIndexOf(denyRule(afterBoth.output()).blocks()));
    }

    // ------------------------------------------------------------------ helpers

    /** A bare pickaxe with no stats -- all the growth tests above need, same as {@code withModifier}. */
    private static ItemStack bareLuckPickaxe() {
        return new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
    }

    private static ItemStack assembledPickaxe() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), PICKAXE_STATS);
        stack.set(DataComponents.MAX_DAMAGE, PICKAXE_STATS.durability());
        stack.set(DataComponents.DAMAGE, 0);
        stack.set(DataComponents.TOOL, new Tool(
                List.of(Tool.Rule.deniesDrops(BlockTags.INCORRECT_FOR_WOODEN_TOOL),
                        Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, PICKAXE_STATS.miningSpeed())),
                1.0F, 1));
        return stack;
    }

    private static int entryLevel(ItemStack stack) {
        List<ModifierEntry> entries = ForgeweaveModifiers.of(stack);
        return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).level();
    }

    private static Tool.Rule denyRule(ItemStack stack) {
        Tool component = stack.get(DataComponents.TOOL);
        return component.rules().stream().filter(rule -> rule.speed().isEmpty()).findFirst()
                .orElseThrow(() -> new AssertionError("no deny-drops rule on " + stack));
    }

    private static ModifierRecipe sharpnessRecipe() {
        return shippedRecipe("sharpness.json");
    }

    private static ModifierRecipe diamondRecipe() {
        return shippedRecipe("diamond.json");
    }

    private static ModifierRecipe emeraldRecipe() {
        return shippedRecipe("emerald.json");
    }

    private static ModifierRecipe shippedRecipe(String fileName) {
        String path = "/data/forgeweave/forgeweave/modifier_recipe/" + fileName;
        JsonElement json;
        try (InputStream in = ModifierBatch1Test.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new AssertionError("missing shipped modifier recipe: " + path);
            }
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
        return ModifierRecipe.CODEC.parse(ops, json).getOrThrow();
    }
}
