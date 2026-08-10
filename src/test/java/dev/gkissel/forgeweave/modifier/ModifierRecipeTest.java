package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
     * unit, and the cap is {@code TinkerModifiers}' {@code new ModHaste(50)} at 5 levels, i.e. 250.
     */
    @Test
    void theShippedHasteRecipeMatchesUpstreamsNumbers() {
        String path = "/data/forgeweave/forgeweave/modifier_recipe/haste.json";
        JsonElement json;
        try (InputStream in = ModifierRecipeTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing shipped modifier recipe: " + path);
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }

        ModifierRecipe recipe = ModifierRecipe.CODEC.parse(ops, json).getOrThrow();

        assertEquals(HASTE, recipe.modifier());
        assertEquals(1, recipe.cost());
        assertEquals(250, recipe.maxLevel(), "5 levels of 50 redstone");
        assertTrue(recipe.reagent().test(new ItemStack(Items.REDSTONE)));
        assertTrue(!recipe.reagent().test(new ItemStack(Items.LAPIS_LAZULI)));
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

    @Test
    void levellingAnExistingModifierStaysInsideItsSlot() {
        ItemStack tool = pickaxe();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(HASTE, 49)));

        ModifierApplication.Outcome outcome = ModifierApplication.apply(shipped(), tool, 5, 0);

        assertEquals(54, levelOf(outcome));
        assertEquals(1, ForgeweaveModifiers.of(outcome.output()).size(), "no second entry");
        assertEquals(ForgeweaveModifiers.DEFAULT_SLOTS - 1, ForgeweaveModifiers.freeSlots(outcome.output()),
                "levelling past 50 must not cost a second slot");
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
