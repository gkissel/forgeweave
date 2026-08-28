package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * ADR-0004's decision 1 from the data side: what a modifier costs and how far it levels lives in
 * datapack JSON, so retuning the JSON changes the game with no code change. The retune tests below
 * are that claim made executable -- the same {@link ModifierApplication#apply} call, driven by two
 * different cost tables, produces two different results.
 */
class ModifierRecipeTest {

    private static RegistryOps<JsonElement> ops;

    private static final ResourceLocation HASTE = ResourceLocation.fromNamespaceAndPath("forgeweave", "haste");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static ModifierRecipe parse(String json) {
        return ModifierRecipe.CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow();
    }

    private static ItemStack pickaxe() {
        return new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
    }

    // ------------------------------------------------------------------ the shipped recipe

    /**
     * The shipped haste recipe carries upstream 1.12's own numbers: one redstone is one application
     * unit, a redstone block is nine (issue #259, {@code TinkerModifiers}'
     * {@code modHaste.addItem("blockRedstone", 1, 9)}), and the cap is {@code new ModHaste(50)} at
     * 5 levels, i.e. 250.
     */
    @Test
    void theShippedHasteRecipeMatchesUpstreamsNumbers() {
        ModifierRecipe recipe = ModifierRecipe.CODEC.parse(ops, shippedJson()).getOrThrow();

        assertEquals(HASTE, recipe.modifier());
        assertEquals(1, recipe.cost());
        assertEquals(250, recipe.maxLevel(), "5 levels of 50 redstone");
        assertTrue(recipe.reagent().test(new ItemStack(Items.REDSTONE)));
        assertTrue(!recipe.reagent().test(new ItemStack(Items.LAPIS_LAZULI)));
        assertEquals(1, recipe.reagentFor(new ItemStack(Items.REDSTONE)).units());
        assertEquals(9, recipe.reagentFor(new ItemStack(Items.REDSTONE_BLOCK)).units(),
                "upstream's addItem(\"blockRedstone\", 1, 9)");
        assertNull(recipe.reagentFor(new ItemStack(Items.LAPIS_LAZULI)));
    }

    private static JsonElement shippedJson() {
        String path = "/data/forgeweave/forgeweave/modifier_recipe/haste.json";
        try (InputStream in = ModifierRecipeTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing shipped modifier recipe: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    // ------------------------------------------------------------------ #259: the two reagent shapes

    /** The pre-#259 single-{@code reagent} shape still decodes, as one reagent worth 1 unit. */
    @Test
    void theLegacySingleReagentShapeStillDecodes() {
        ModifierRecipe recipe = shipped(); // parsed from the legacy shape below

        assertEquals(1, recipe.reagents().size());
        assertEquals(1, recipe.reagents().get(0).units());
        assertTrue(recipe.matches(new ItemStack(Items.REDSTONE)));
        assertTrue(!recipe.matches(new ItemStack(Items.REDSTONE_BLOCK)),
                "the legacy shape names dust only; the block is the shipped JSON's addition");
    }

    /** {@code units} is optional in the new shape, defaulting to 1 like the legacy shape. */
    @Test
    void reagentUnitsDefaultToOne() {
        ModifierRecipe recipe = parse("""
                {"modifier": "forgeweave:haste",
                 "reagents": [{"ingredient": {"item": "minecraft:redstone"}}],
                 "max_level": 250}
                """);

        assertEquals(1, recipe.reagentFor(new ItemStack(Items.REDSTONE)).units());
    }

    /** A recipe whose reagent list is empty must not parse -- it could never be applied. */
    @Test
    void rejectsAnEmptyReagentList() {
        DataResult<ModifierRecipe> result = ModifierRecipe.CODEC.parse(ops, JsonParser.parseString("""
                {"modifier": "forgeweave:haste", "reagents": [], "max_level": 250}
                """));

        assertTrue(result.isError(), "an empty reagents list must not parse");
    }

    /**
     * Both shapes re-encode stably: encode always writes the {@code reagents} list (the
     * accept-old-write-new posture of {@code Material#TRAITS_CODEC}), and what that encode produces
     * decodes back to the same recipe and encodes identically again. The network codec is this same
     * codec (synced datapack registry), so this is also the sync-payload round trip.
     */
    @Test
    void bothReagentShapesReencodeStably() {
        for (ModifierRecipe recipe : List.of(shipped(), ModifierRecipe.CODEC.parse(ops, shippedJson()).getOrThrow())) {
            JsonElement encoded = ModifierRecipe.CODEC.encodeStart(ops, recipe).getOrThrow();
            assertTrue(encoded.getAsJsonObject().has("reagents"), "encode always writes the new shape");
            assertFalse(encoded.getAsJsonObject().has("reagent"), "and never the legacy field");

            ModifierRecipe reDecoded = ModifierRecipe.CODEC.parse(ops, encoded).getOrThrow();
            assertEquals(encoded, ModifierRecipe.CODEC.encodeStart(ops, reDecoded).getOrThrow(),
                    "decode -> encode must be a fixed point");
        }
    }

    @Test
    void costDefaultsToOneReagentPerUnit() {
        assertEquals(1, parse("""
                {"modifier": "forgeweave:haste", "reagent": {"item": "minecraft:redstone"}, "max_level": 250}
                """).cost());
    }

    @Test
    void rejectsARecipeWithoutACap() {
        DataResult<ModifierRecipe> result = ModifierRecipe.CODEC.parse(ops, JsonParser.parseString("""
                {"modifier": "forgeweave:haste", "reagent": {"item": "minecraft:redstone"}}
                """));

        assertTrue(result.isError(), "a modifier recipe without max_level must not parse");
    }

    // ------------------------------------------------------------------ retuning, no code change

    /**
     * The same ten redstone, two cost tables: at one redstone per unit they buy ten units, at five
     * they buy two. Nothing but the JSON differs between the two halves of this test.
     */
    @Test
    void retuningTheCostChangesWhatTenReagentsBuy() {
        String template = """
                {"modifier": "forgeweave:haste", "reagent": {"item": "minecraft:redstone"},
                 "cost": %d, "max_level": 250}
                """;

        ModifierApplication.Outcome cheap = ModifierApplication.apply(parse(template.formatted(1)), pickaxe(), 10, 0);
        ModifierApplication.Outcome dear = ModifierApplication.apply(parse(template.formatted(5)), pickaxe(), 10, 0);

        assertEquals(10, levelOf(cheap), "1 redstone per unit: ten redstone are ten units");
        assertEquals(2, levelOf(dear), "5 redstone per unit: the same ten are two units");
        assertEquals(10, cheap.firstUsed());
        assertEquals(10, dear.firstUsed());
    }

    /** And the cap: a retuned {@code max_level} stops the application where the JSON says it does. */
    @Test
    void retuningTheCapChangesWhereApplicationStops() {
        String template = """
                {"modifier": "forgeweave:haste", "reagent": {"item": "minecraft:redstone"},
                 "max_level": %d}
                """;

        ModifierApplication.Outcome deep = ModifierApplication.apply(parse(template.formatted(250)), pickaxe(), 64, 0);
        ModifierApplication.Outcome shallow = ModifierApplication.apply(parse(template.formatted(3)), pickaxe(), 64, 0);

        assertEquals(64, levelOf(deep));
        assertEquals(64, deep.firstUsed());
        assertEquals(3, levelOf(shallow), "the cap stops it at three units");
        assertEquals(3, shallow.firstUsed(), "and only three reagents are spent");
    }

    // ------------------------------------------------------------------ the slot rules

    @Test
    void reagentsAreSpentFromTheFirstSlotBeforeTheSecond() {
        ModifierApplication.Outcome outcome = ModifierApplication.apply(shipped(), pickaxe(), 4, 10);

        assertEquals(14, levelOf(outcome));
        assertEquals(4, outcome.firstUsed());
        assertEquals(10, outcome.secondUsed());
    }

    /**
     * Parity audit T2 (issue #434): the station has five free slots, not two, and one recipe's
     * reagent pools across every one of them (upstream {@code RecipeMatch.Item#matches} sums the
     * count over all input stacks), spent slot-first. Differently-valued forms (dust vs. block) still
     * step whole cost-steps at a time in first-appearance order, as the two-slot #259 rule did.
     */
    @Test
    void reagentsPoolAcrossAllFiveFreeSlots() {
        ModifierApplication.Outcome spread = ModifierApplication.apply(shipped(), pickaxe(),
                new int[] {1, 0, 2, 0, 3}, new int[] {1, 1, 1, 1, 1});
        assertEquals(6, levelOf(spread));
        assertEquals(List.of(1, 0, 2, 0, 3), spread.used());

        ModifierApplication.Outcome mixed = ModifierApplication.apply(shipped(), pickaxe(),
                new int[] {0, 1, 0, 2, 0}, new int[] {1, 9, 1, 1, 1});
        assertEquals(11, levelOf(mixed), "one block in slot 2 + two dust in slot 4 = 11 units");
        assertEquals(List.of(0, 1, 0, 2, 0), mixed.used());
    }

    /** Issue #344: crossing the 50-unit boundary starts a second level, which charges a second slot. */
    @Test
    void crossingALevelBoundaryChargesAFreshSlot() {
        ItemStack tool = pickaxe();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(HASTE, 49)));

        ModifierApplication.Outcome outcome = ModifierApplication.apply(shipped(), tool, 5, 0);

        assertEquals(54, levelOf(outcome));
        assertEquals(1, ForgeweaveModifiers.of(outcome.output()).size(), "no second entry");
        assertEquals(ForgeweaveModifiers.DEFAULT_SLOTS - 2, ForgeweaveModifiers.freeSlots(outcome.output()),
                "levelling past 50 must charge a second slot (upstream MultiAspect's per-level spend)");
    }

    /**
     * Issue #344's budget cap: reagents whose units would start a level the budget can't afford are
     * left unconsumed (upstream's per-match rollback), and a unit that can't land at all is refused
     * with upstream's not-enough-modifiers reason.
     */
    @Test
    void unitsPastTheAffordableLevelStayUnconsumed() {
        ModifierApplication.Outcome outcome = ModifierApplication.apply(shipped(), pickaxe(), 250, 0);

        assertEquals(150, levelOf(outcome), "three free slots afford exactly three 50-unit levels");
        assertEquals(150, outcome.firstUsed(), "the 100 redstone past the budget stay unconsumed");

        ModifierApplication.Outcome refused = ModifierApplication.apply(shipped(), outcome.output(), 1, 0);
        assertTrue(refused.output().isEmpty(), "a level past the budget must not start");
        assertEquals("gui.forgeweave.modifier.no_slots", translationKey(refused));
    }

    @Test
    void aFourthDistinctModifierIsRejected() {
        ItemStack tool = pickaxe();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", "one"), 1),
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", "two"), 1),
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", "three"), 1)));

        ModifierApplication.Outcome outcome = ModifierApplication.apply(shipped(), tool, 1, 0);

        assertTrue(outcome.output().isEmpty(), "no output when the slots are full");
        assertNotNull(outcome.rejection(), "and a reason the station can show");
        assertEquals("gui.forgeweave.modifier.no_slots", translationKey(outcome));
    }

    @Test
    void aCappedModifierIsRejected() {
        ItemStack tool = pickaxe();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(HASTE, 250)));

        ModifierApplication.Outcome outcome = ModifierApplication.apply(shipped(), tool, 8, 0);

        assertTrue(outcome.output().isEmpty());
        assertEquals("gui.forgeweave.modifier.max_level", translationKey(outcome));
    }

    @Test
    void tooFewReagentsForOneUnitIsRejectedRatherThanRoundedDown() {
        ModifierRecipe expensive = parse("""
                {"modifier": "forgeweave:haste", "reagent": {"item": "minecraft:redstone"},
                 "cost": 5, "max_level": 250}
                """);

        ModifierApplication.Outcome outcome = ModifierApplication.apply(expensive, pickaxe(), 3, 0);

        assertTrue(outcome.output().isEmpty());
        assertEquals("gui.forgeweave.modifier.not_enough_reagents", translationKey(outcome));
    }

    // ------------------------------------------------------------------ #259: multi-unit reagents

    /** One 9-unit block in the first slot advances haste by 9; dust next to it still adds 1 each. */
    @Test
    void aRedstoneBlockIsWorthNineDust() {
        ModifierRecipe recipe = ModifierRecipe.CODEC.parse(ops, shippedJson()).getOrThrow();

        ModifierApplication.Outcome oneBlock = ModifierApplication.apply(recipe, pickaxe(), 1, 9, 0, 1);
        assertEquals(9, levelOf(oneBlock));
        assertEquals(1, oneBlock.firstUsed(), "one block consumed");

        ModifierApplication.Outcome dustAndBlocks = ModifierApplication.apply(recipe, pickaxe(), 3, 1, 2, 9);
        assertEquals(21, levelOf(dustAndBlocks), "3 dust + 2 blocks = 21 units");
        assertEquals(3, dustAndBlocks.firstUsed());
        assertEquals(2, dustAndBlocks.secondUsed());
    }

    /**
     * The cap, in whole blocks: a block whose full 9 units no longer fit is refused and left
     * unconsumed, mirroring upstream's all-or-nothing rollback of a partially applicable
     * {@code RecipeMatch} ({@code ToolBuilder#tryModifyTool}) -- while an exactly-fitting block, and
     * dust filling the same gap one unit at a time, both still land.
     */
    @Test
    void aBlockThatOvershootsTheCapIsRefusedWhole() {
        ModifierRecipe recipe = ModifierRecipe.CODEC.parse(ops, shippedJson()).getOrThrow();

        ItemStack nearCap = pickaxe();
        nearCap.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(HASTE, 245)));
        ModifierApplication.Outcome overshoot = ModifierApplication.apply(recipe, nearCap, 1, 9, 0, 1);
        assertTrue(overshoot.output().isEmpty(), "5 units of room cannot take a 9-unit block");
        assertEquals("gui.forgeweave.modifier.reagent_overshoot", translationKey(overshoot));

        ModifierApplication.Outcome dust = ModifierApplication.apply(recipe, nearCap.copy(), 8, 1, 0, 1);
        assertEquals(250, levelOf(dust), "dust still partial-fills to the cap");
        assertEquals(5, dust.firstUsed(), "and only the 5 that fit are spent");

        ItemStack exactFit = pickaxe();
        exactFit.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(HASTE, 241)));
        ModifierApplication.Outcome exact = ModifierApplication.apply(recipe, exactFit, 1, 9, 0, 1);
        assertEquals(250, levelOf(exact), "241 + one block is exactly the cap");
        assertEquals(1, exact.firstUsed());
    }

    // ------------------------------------------------------------------ the shipped behavior

    /**
     * Upstream {@code ModHaste#applyHarvestBoost}: a diminishing step per redstone plus a flat
     * {@code +0.5} per completed level. Below speed 15 the step is {@code 0.15 - 0.05 * speed / 15},
     * so the first redstone on a speed-4 head adds {@code 0.15 - 0.0133 = 0.1367}.
     */
    @Test
    void hasteRaisesMiningSpeedTheWayUpstreamDoes() {
        assertEquals(4.0F + 0.15F - 0.05F * 4.0F / 15.0F, ForgeweaveModifiers.HASTE.miningSpeed(1, 4.0F), 1.0e-5F);
        assertTrue(ForgeweaveModifiers.HASTE.miningSpeed(50, 4.0F) > ForgeweaveModifiers.HASTE.miningSpeed(49, 4.0F),
                "every redstone is worth something");
        assertEquals(50, ForgeweaveModifiers.HASTE.unitsPerLevel(), "upstream's new ModHaste(50)");
        // Upstream ModHaste#getSpeedBonus is 0.2f * current / max in float arithmetic, so a level is
        // worth +0.2 and each individual redstone below it is worth 0.004 -- its own comment says so.
        assertEquals(1.004F, ForgeweaveModifiers.HASTE.attackSpeedMultiplier(1), 1.0e-5F);
        assertEquals(1.2F, ForgeweaveModifiers.HASTE.attackSpeedMultiplier(50), 1.0e-5F);
        assertEquals(0, ForgeweaveModifiers.HASTE.bonusSlots(250), "haste grants no extra slots");
    }

    // ------------------------------------------------------------------ #108 batch: modern-vanilla modifiers

    /**
     * Every issue #108 recipe ships with the numbers this PR records as its own decisions (1 reagent
     * per unit; single-level modifiers cap at 1, Resonant at 3, Far Reach at 2).
     */
    @Test
    void theShippedModernVanillaRecipesMatchThisPrsNumbers() {
        assertShippedRecipe("searing.json", "searing", Items.MAGMA_CREAM, 1);
        assertShippedRecipe("magnetic_pull.json", "magnetic_pull", Items.ENDER_PEARL, 1);
        assertShippedRecipe("aquadynamic.json", "aquadynamic", Items.TURTLE_SCUTE, 1);
        assertShippedRecipe("resonant.json", "resonant", Items.ECHO_SHARD, 3);
        assertShippedRecipe("far_reach.json", "far_reach", Items.AMETHYST_SHARD, 2);
    }

    private static void assertShippedRecipe(String fileName, String modifierPath, Item reagent, int expectedMaxLevel) {
        String path = "/data/forgeweave/forgeweave/modifier_recipe/" + fileName;
        JsonElement json;
        try (InputStream in = ModifierRecipeTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing shipped modifier recipe: " + path);
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }

        ModifierRecipe recipe = ModifierRecipe.CODEC.parse(ops, json).getOrThrow();

        assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", modifierPath), recipe.modifier());
        assertEquals(1, recipe.cost());
        assertEquals(expectedMaxLevel, recipe.maxLevel());
        assertTrue(recipe.reagent().test(new ItemStack(reagent)));
    }

    /** Searing and Magnetic Pull are simple on/off switches, and each answers only its own hook. */
    @Test
    void searingAndMagneticPullAreSingleLevelSwitches() {
        assertTrue(ForgeweaveModifiers.SEARING.autoSmelt(1));
        assertFalse(ForgeweaveModifiers.SEARING.magnetic(1), "one modifier's hooks must not leak into another's");
        assertTrue(ForgeweaveModifiers.MAGNETIC_PULL.magnetic(1));
        assertFalse(ForgeweaveModifiers.MAGNETIC_PULL.autoSmelt(1));
    }

    /** Resonant: our chosen +50% bonus experience per level, capped at 3 in the shipped recipe. */
    @Test
    void resonantAddsFiftyPercentBonusExperiencePerLevel() {
        assertEquals(0.5F, ForgeweaveModifiers.RESONANT.bonusExperienceFraction(1), 1.0e-5F);
        assertEquals(1.5F, ForgeweaveModifiers.RESONANT.bonusExperienceFraction(3), 1.0e-5F);
    }

    /**
     * Aquadynamic: our chosen +0.8 bonus to {@code player.submerged_mining_speed}, which defaults to
     * 0.2 (vanilla's submerged mining penalty) -- together they restore the unpenalized 1.0x.
     */
    @Test
    void aquadynamicCancelsTheSubmergedMiningPenalty() {
        assertEquals(0.8F, ForgeweaveModifiers.AQUADYNAMIC.submergedMiningSpeedBonus(1), 1.0e-5F);
    }

    /** Far Reach: our chosen +1 block interaction range per level, aggregated across a tool's entries. */
    @Test
    void farReachAddsOneBlockOfRangePerLevel() {
        assertEquals(1.0F, ForgeweaveModifiers.FAR_REACH.blockInteractionRangeBonus(1), 1.0e-5F);
        assertEquals(2.0F, ForgeweaveModifiers.FAR_REACH.blockInteractionRangeBonus(2), 1.0e-5F);

        ItemStack tool = pickaxe();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", "far_reach"), 2)));
        assertEquals(2.0F, ForgeweaveModifiers.blockInteractionRangeBonus(tool), 1.0e-5F);
    }

    /** An id with no Java behind it applies and serializes; it simply does nothing (ADR-0004). */
    @Test
    void aRecipeMayNameAModifierThisVersionDoesNotImplement() {
        ModifierRecipe future = parse("""
                {"modifier": "forgeweave:not_implemented_here", "reagent": {"item": "minecraft:redstone"},
                 "max_level": 4}
                """);

        ModifierApplication.Outcome outcome = ModifierApplication.apply(future, pickaxe(), 2, 0);

        assertNull(outcome.rejection());
        assertEquals(2, levelOf(outcome));
        assertNull(ForgeweaveModifiers.get(future.modifier()), "and no behavior was invented for it");
    }

    // ------------------------------------------------------------------ #776: AND-type combo recipes

    private static ModifierRecipe combo() {
        return parse("""
                {"modifier": "forgeweave:creative_flight",
                 "reagents": [{"ingredient": {"item": "minecraft:end_crystal"}},
                              {"ingredient": {"item": "minecraft:nether_star"}}],
                 "require_all_reagents": true, "max_level": 1}
                """);
    }

    /** A pre-#776 recipe with no {@code require_all_reagents} field defaults to the legacy OR reading. */
    @Test
    void requireAllReagentsDefaultsToFalse() {
        assertFalse(shipped().requireAllReagents());
        assertTrue(combo().requireAllReagents());
    }

    /**
     * {@link ModifierRecipe#isSatisfiedBy}: the OR reading (every recipe before this ticket) is
     * satisfied by any one slot holding any one reagent form, same as {@link ModifierRecipe#matches}
     * always was. The AND reading (issue #776) needs every declared reagent present at once -- a lone
     * end crystal is not creative flight, and neither is a lone nether star.
     */
    @Test
    void isSatisfiedByDistinguishesOrFromAndReadings() {
        ModifierRecipe haste = ModifierRecipe.CODEC.parse(ops, shippedJson()).getOrThrow(); // dust or block, either form
        assertTrue(haste.isSatisfiedBy(List.of(new ItemStack(Items.REDSTONE))));
        assertTrue(haste.isSatisfiedBy(List.of(new ItemStack(Items.REDSTONE_BLOCK))),
                "either OR form alone satisfies a legacy recipe");
        assertFalse(haste.isSatisfiedBy(List.of(new ItemStack(Items.DIRT))));

        ModifierRecipe combo = combo();
        assertFalse(combo.isSatisfiedBy(List.of(new ItemStack(Items.END_CRYSTAL))),
                "the end crystal alone is not enough for an AND recipe");
        assertFalse(combo.isSatisfiedBy(List.of(new ItemStack(Items.NETHER_STAR))),
                "nor is the nether star alone");
        assertTrue(combo.isSatisfiedBy(List.of(new ItemStack(Items.END_CRYSTAL), new ItemStack(Items.NETHER_STAR))),
                "both together satisfy it");
    }

    /**
     * {@link ModifierApplication#mostSpecific}: issue #776's actual collision -- a nether star is both
     * soulbound's whole reagent and half of creative flight's combo. With only the nether star present,
     * soulbound is the only satisfied recipe. With the end crystal alongside it, creative flight is
     * satisfied too and, being more specific (it consumes two matching slots to soulbound's one), wins
     * the shared nether star; soulbound is dropped rather than applied alongside it.
     */
    @Test
    void mostSpecificPrefersTheLargerReagentSetOverAnOverlappingSmallerOne() {
        ModifierRecipe soulbound = parse("""
                {"modifier": "forgeweave:soulbound", "reagent": {"item": "minecraft:nether_star"}, "max_level": 1}
                """);
        ModifierRecipe creativeFlight = combo();
        List<ModifierRecipe> recipes = List.of(soulbound, creativeFlight);

        List<ModifierRecipe> loneStar = ModifierApplication.mostSpecific(recipes, List.of(new ItemStack(Items.NETHER_STAR)));
        assertEquals(List.of(soulbound), loneStar, "no end crystal: only soulbound is satisfied");

        List<ModifierRecipe> both = ModifierApplication.mostSpecific(recipes,
                List.of(new ItemStack(Items.END_CRYSTAL), new ItemStack(Items.NETHER_STAR)));
        assertEquals(List.of(creativeFlight), both,
                "creative flight's two-reagent match beats soulbound's one-reagent match on the shared star");
    }

    /** Two satisfied recipes that share no item are both kept -- specificity only breaks a real tie. */
    @Test
    void mostSpecificKeepsSatisfiedRecipesThatDoNotOverlap() {
        ModifierRecipe haste = shipped();
        ModifierRecipe soulbound = parse("""
                {"modifier": "forgeweave:soulbound", "reagent": {"item": "minecraft:nether_star"}, "max_level": 1}
                """);

        List<ModifierRecipe> recipes = ModifierApplication.mostSpecific(List.of(haste, soulbound),
                List.of(new ItemStack(Items.REDSTONE), new ItemStack(Items.NETHER_STAR)));

        assertEquals(2, recipes.size(), "unrelated reagents in different slots both still apply");
        assertTrue(recipes.containsAll(List.of(haste, soulbound)));
    }

    private static ModifierRecipe shipped() {
        return parse("""
                {"modifier": "forgeweave:haste", "reagent": {"item": "minecraft:redstone"},
                 "cost": 1, "max_level": 250}
                """);
    }

    private static int levelOf(ModifierApplication.Outcome outcome) {
        List<ModifierEntry> entries = ForgeweaveModifiers.of(outcome.output());
        return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).level();
    }

    private static String translationKey(ModifierApplication.Outcome outcome) {
        return ((net.minecraft.network.chat.contents.TranslatableContents)
                outcome.rejection().getContents()).getKey();
    }
}
