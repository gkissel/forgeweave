package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

/**
 * Issue #994 (M8-10): {@code rayward}'s shielding curve, the one number the modifier exists to
 * produce. Four levels of it come to exactly 1.0 -- a fully shielded piece -- and no amount of
 * stacking ever goes past that, which is the "not more" half of the issue's own wording.
 *
 * <p>Config is never loaded in a plain unit JVM, so every expectation is computed from
 * {@link ForgeweaveModifiers#RAYWARD_SHIELDING_PER_LEVEL_DEFAULT} rather than typed as a second
 * copy of the same number -- the same discipline {@code SurgeboundTest} uses. The toggle-off and
 * Tool Station paths need a loaded spec and a world, and live in {@code RaywardGameTests}.
 */
class RaywardTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final double PER_LEVEL = ForgeweaveModifiers.RAYWARD_SHIELDING_PER_LEVEL_DEFAULT;

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    @Test
    void anUnappliedModifierShieldsNothing() {
        assertEquals(0.0D, ForgeweaveModifiers.raywardShielding(0));
        assertEquals(0.0D, ForgeweaveModifiers.raywardShielding(-1));
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "3", "4"})
    void eachLevelAddsOneMoreQuarter(int level) {
        assertEquals(level * PER_LEVEL, ForgeweaveModifiers.raywardShielding(level), 1.0e-9D);
    }

    @Test
    void fourLevelsShieldExactlyEverythingAndNotMore() {
        assertEquals(1.0D, ForgeweaveModifiers.raywardShielding(ForgeweaveModifiers.RAYWARD_MAX_LEVEL), 1.0e-9D);
        assertEquals(1.0D, ForgeweaveModifiers.raywardShielding(40), "clamped, never past complete");
    }

    @Test
    void theModifierHookAnswersTheSameCurve() {
        for (int level = 0; level <= ForgeweaveModifiers.RAYWARD_MAX_LEVEL; level++) {
            assertEquals(ForgeweaveModifiers.raywardShielding(level),
                    ForgeweaveModifiers.RAYWARD.radiationShielding(level), 1.0e-9D);
        }
    }

    @Test
    void everyOtherModifierShieldsNothing() {
        for (ResourceLocation id : ForgeweaveModifiers.ids()) {
            if (ForgeweaveModifiers.RAYWARD_ID.equals(id)) {
                continue;
            }
            Modifier modifier = ForgeweaveModifiers.get(id);
            assertEquals(0.0D, modifier.radiationShielding(4),
                    id + " should not contribute radiation shielding");
        }
    }

    @Test
    void itFitsAnyArmorPieceAndNoTool() {
        assertTrue(ForgeweaveModifiers.RAYWARD.armorOnly(), "rayward is armour only");
        assertFalse(ForgeweaveModifiers.RAYWARD.chestplateOnly(), "radiation is not a chestplate problem");
        assertFalse(ForgeweaveModifiers.RAYWARD.helmetOnly(), "nor a helmet one");
    }

    @Test
    void eachLevelCostsOneModifierSlot() {
        for (int level = 1; level <= ForgeweaveModifiers.RAYWARD_MAX_LEVEL; level++) {
            assertEquals(level, ForgeweaveModifiers.RAYWARD.occupiedSlots(level));
        }
    }

    /**
     * The shipped recipe is one file with all four levels on it, because every level takes the same
     * reagent. Read as text rather than through the datapack registry, which a plain unit JVM has
     * no loader for.
     */
    @Test
    void theShippedRecipeSpendsLeadAndCapsAtFourLevels() throws IOException {
        String recipe = Files.readString(projectRoot().resolve(
                "src/main/resources/data/forgeweave/forgeweave/modifier_recipe/rayward_lead.json"));
        assertTrue(recipe.contains("\"forgeweave:rayward\""), "names the modifier");
        assertTrue(recipe.contains("\"c:ingots/lead\""), "spends c:ingots/lead, not one mod's own ingot");
        assertTrue(recipe.contains("\"max_level\": " + ForgeweaveModifiers.RAYWARD_MAX_LEVEL),
                "caps at the level the shielding curve reaches 100% on");
    }
}
