package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Issue #162 (M3-13): smite, bane of arthropods, fiery, necrotic -- one clone-constant test per
 * modifier's math, verified against tinkers-1.12 @ {@code c01173c0} (see {@code NOTICE.md}), plus the
 * shipped recipe JSON for each. The behaviors themselves ride the combat-seam pipeline rather than a
 * {@link Modifier} hook, so their integration coverage is a GameTest ({@code CombatModifierGameTests},
 * which needs real entities to hit) -- these are the pure-function pieces a plain unit test can pin
 * down without a world, same split {@code ModifierBatch1Test} uses for its own modifiers.
 */
class CombatModifierBatch1Test {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    // ------------------------------------------------------------------ smite / bane of arthropods

    /** Upstream {@code ModAntiMonsterType}: {@code dmgPerItem = 7f / 24}, so a full level (24 units) is +7. */
    @Test
    void smiteBaneUnitsPerLevelMatchesUpstream() {
        assertEquals(24, ForgeweaveModifiers.SMITE.unitsPerLevel(), "upstream new ModAntiMonsterType(..., 5, 24, UNDEAD)");
        assertEquals(24, ForgeweaveModifiers.BANE_OF_ARTHROPODS.unitsPerLevel(),
                "upstream new ModAntiMonsterType(..., 5, 24, ARTHROPOD)");
    }

    @Test
    void smiteBaneBonusDamageScalesLinearlyAtSevenPerLevel() {
        assertEquals(0.0F, ForgeweaveModifiers.smiteBaneBonusDamage(0), 1.0e-5F);
        assertEquals(7.0F, ForgeweaveModifiers.smiteBaneBonusDamage(24), 1.0e-5F, "one full level (24 units)");
        assertEquals(35.0F, ForgeweaveModifiers.smiteBaneBonusDamage(120), 1.0e-5F, "the level-5 cap (120 units)");
        assertEquals(7.0F / 24.0F, ForgeweaveModifiers.smiteBaneBonusDamage(1), 1.0e-5F, "one item's worth, unlike haste/sharpness this is linear, no diminishing steps");
    }

    @Test
    void theShippedSmiteRecipeMatchesUpstreamsFiveLevelsOfTwentyFour() {
        ModifierRecipe recipe = shippedRecipe("smite.json");
        assertEquals(1, recipe.cost());
        assertEquals(120, recipe.maxLevel(), "5 levels * 24 units per level");
        assertTrue(recipe.reagent().test(new ItemStack(Items.GLOWSTONE_DUST)),
                "consecrated soil has no Forgeweave counterpart; glowstone dust is the maintainer-pick substitute");
    }

    @Test
    void theShippedBaneRecipeMatchesUpstreamsFiveLevelsOfTwentyFourAndKeepsUpstreamsReagent() {
        ModifierRecipe recipe = shippedRecipe("bane_of_arthropods.json");
        assertEquals(1, recipe.cost());
        assertEquals(120, recipe.maxLevel());
        assertTrue(recipe.reagent().test(new ItemStack(Items.FERMENTED_SPIDER_EYE)), "unchanged from upstream's own reagent");
    }

    // ------------------------------------------------------------------ fiery

    @Test
    void fieryUnitsPerLevelMatchesUpstream() {
        assertEquals(25, ForgeweaveModifiers.FIERY.unitsPerLevel(), "upstream new ModFiery() -> super(\"fiery\", ..., 5, 25)");
    }

    /** Upstream {@code ModFiery#getFireDamage}: {@code units / 15f}. */
    @Test
    void fieryDamageIsUnitsOverFifteen() {
        assertEquals(0.0F, ForgeweaveModifiers.fieryDamage(0), 1.0e-5F);
        assertEquals(1.0F, ForgeweaveModifiers.fieryDamage(15), 1.0e-5F);
        assertEquals(125.0F / 15.0F, ForgeweaveModifiers.fieryDamage(125), 1.0e-5F, "the level-5 cap (125 units)");
    }

    /** Upstream {@code ModFiery#getFireDuration}: {@code 1 + units / 8} (integer division), in seconds. */
    @Test
    void fieryDurationIsOnePlusUnitsOverEightRoundedDown() {
        assertEquals(1, ForgeweaveModifiers.fieryDurationSeconds(0));
        assertEquals(1, ForgeweaveModifiers.fieryDurationSeconds(7), "integer division: 7/8 rounds down to 0");
        assertEquals(2, ForgeweaveModifiers.fieryDurationSeconds(15));
        assertEquals(16, ForgeweaveModifiers.fieryDurationSeconds(125), "the level-5 cap (125 units)");
    }

    @Test
    void theShippedFieryRecipeMatchesUpstreamsFiveLevelsOfTwentyFiveAndKeepsUpstreamsReagent() {
        ModifierRecipe recipe = shippedRecipe("fiery.json");
        assertEquals(1, recipe.cost());
        assertEquals(125, recipe.maxLevel(), "5 levels * 25 units per level");
        assertTrue(recipe.reagent().test(new ItemStack(Items.BLAZE_POWDER)), "unchanged from upstream's own reagent");
    }

    // ------------------------------------------------------------------ necrotic

    /** Upstream {@code ModNecrotic#lifesteal}: {@code 0.10f * level}, level == raw units here (LevelAspect, one item per level). */
    @Test
    void necroticLifestealIsTenPercentPerLevel() {
        assertEquals(0.0F, ForgeweaveModifiers.necroticLifestealFraction(0), 1.0e-5F);
        assertEquals(0.10F, ForgeweaveModifiers.necroticLifestealFraction(1), 1.0e-5F);
        assertEquals(0.50F, ForgeweaveModifiers.necroticLifestealFraction(5), 1.0e-5F);
        assertEquals(1.00F, ForgeweaveModifiers.necroticLifestealFraction(10), 1.0e-5F, "the level-10 cap");
    }

    @Test
    void theShippedNecroticRecipeMatchesUpstreamsTenLevelsOfOne() {
        ModifierRecipe recipe = shippedRecipe("necrotic.json");
        assertEquals(1, recipe.cost());
        assertEquals(10, recipe.maxLevel(), "upstream's ModNecrotic(..., 10, 0): one item per level, 10 levels");
        assertTrue(recipe.reagent().test(new ItemStack(Items.WITHER_SKELETON_SKULL)),
                "upstream's boneWithered has no Forgeweave counterpart; wither skeleton skull is the maintainer-pick substitute");
    }

    // ------------------------------------------------------------------ helpers

    private static ModifierRecipe shippedRecipe(String fileName) {
        String path = "/data/forgeweave/forgeweave/modifier_recipe/" + fileName;
        JsonElement json;
        try (InputStream in = CombatModifierBatch1Test.class.getResourceAsStream(path)) {
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
