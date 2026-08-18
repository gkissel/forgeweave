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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.combat.KnockbackOnHitSeam;
import dev.gkissel.forgeweave.combat.PotionEffectOnHitSeam;

/**
 * Issue #163's verification for combat modifiers batch 2 (docs/SCOPE.md M3): one test per modifier,
 * pinning both the shipped recipe JSON against upstream 1.12's constants (same shape as
 * {@code ModifierRecipeTest#theShippedHasteRecipeMatchesUpstreamsNumbers}) and the pure
 * {@code Modifier#combatSeam} behavior behind it. The real-hit integration coverage (a genuine blow
 * through the shared pipeline, the slot cap) is {@code gametest.CombatModifierGameTests}.
 */
class CombatModifiersTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static ModifierRecipe shippedRecipe(String path) {
        JsonElement json;
        try (InputStream in = CombatModifiersTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped modifier recipe: " + path);
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
        return ModifierRecipe.CODEC.parse(ops, json).getOrThrow();
    }

    // ------------------------------------------------------------------ knockback

    @Test
    void knockbackMagnitudeScalesLinearlyWithRawApplicationUnits() {
        ModifierRecipe recipe = shippedRecipe("/data/forgeweave/forgeweave/modifier_recipe/knockback.json");
        assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "knockback"), recipe.modifier());
        assertEquals(1, recipe.cost());
        assertEquals(990, recipe.maxLevel(), "upstream's 99 levels of 10 pistons each");
        assertTrue(recipe.reagent().test(new ItemStack(Items.PISTON)));
        assertTrue(recipe.matches(new ItemStack(Items.STICKY_PISTON)),
                "parity audit T59 (issue #490): upstream modKnockback.addItem(Blocks.STICKY_PISTON, 1) too");
        assertEquals(1, recipe.reagentFor(new ItemStack(Items.STICKY_PISTON)).units());

        assertEquals(10, ForgeweaveModifiers.KNOCKBACK.unitsPerLevel(), "upstream's countPerLevel");

        KnockbackOnHitSeam level1 = (KnockbackOnHitSeam) ForgeweaveModifiers.KNOCKBACK.combatSeam(1).orElseThrow();
        KnockbackOnHitSeam level30 = (KnockbackOnHitSeam) ForgeweaveModifiers.KNOCKBACK.combatSeam(30).orElseThrow();
        assertEquals(0.1F, level1.magnitude(), 1.0e-6F, "upstream ModKnockback#calcKnockback: 0.1 per unit");
        assertEquals(3.0F, level30.magnitude(), 1.0e-6F);
        assertTrue(level30.magnitude() > level1.magnitude(), "more application units must push harder");
    }

    // ------------------------------------------------------------------ shulking

    @Test
    void shulkingGrantsLevitationWithDurationScalingSmoothlyByRawUnits() {
        ModifierRecipe recipe = shippedRecipe("/data/forgeweave/forgeweave/modifier_recipe/shulking.json");
        assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "shulking"), recipe.modifier());
        assertEquals(1, recipe.cost());
        assertEquals(50, recipe.maxLevel(), "upstream's countPerLevel: 50 application units make the one level");
        assertTrue(recipe.reagent().test(new ItemStack(Items.SHULKER_SHELL)),
                "issue #163's maintainer-chosen reagent (upstream used popped chorus fruit)");

        PotionEffectOnHitSeam min = (PotionEffectOnHitSeam) ForgeweaveModifiers.SHULKING.combatSeam(1).orElseThrow();
        PotionEffectOnHitSeam max = (PotionEffectOnHitSeam) ForgeweaveModifiers.SHULKING.combatSeam(50).orElseThrow();
        assertEquals(MobEffects.LEVITATION, min.effect());
        assertEquals(0, min.amplifier(), "Levitation I");
        assertEquals(10, min.durationTicks(), "upstream ModShulking#getDuration: 1/2 + 10 == 10");
        assertEquals(35, max.durationTicks(), "50/2 + 10 == 35");
    }

    // ------------------------------------------------------------------ webbed

    @Test
    void webbedGrantsSlownessForOneSecondPerLevel() {
        ModifierRecipe recipe = shippedRecipe("/data/forgeweave/forgeweave/modifier_recipe/webbed.json");
        assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "webbed"), recipe.modifier());
        assertEquals(1, recipe.cost());
        assertEquals(3, recipe.maxLevel(), "upstream ModWebbed's own maxLevel");
        assertTrue(recipe.reagent().test(new ItemStack(Items.COBWEB)));

        PotionEffectOnHitSeam oneLevel = (PotionEffectOnHitSeam) ForgeweaveModifiers.WEBBED.combatSeam(1).orElseThrow();
        PotionEffectOnHitSeam threeLevels = (PotionEffectOnHitSeam) ForgeweaveModifiers.WEBBED.combatSeam(3).orElseThrow();
        assertEquals(MobEffects.MOVEMENT_SLOWDOWN, oneLevel.effect());
        assertEquals(1, oneLevel.amplifier(), "Slowness II, upstream's hardcoded amplifier");
        assertEquals(20, oneLevel.durationTicks(), "upstream ModWebbed#onHit: level * 20");
        assertEquals(60, threeLevels.durationTicks());
    }

    /** A modifier with no combat behavior (haste) must not manufacture a seam. */
    @Test
    void aModifierWithNoCombatBehaviorHandsBackNoSeam() {
        assertTrue(ForgeweaveModifiers.HASTE.combatSeam(250).isEmpty());
    }
}
