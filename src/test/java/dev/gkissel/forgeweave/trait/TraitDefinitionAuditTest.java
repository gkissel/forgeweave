package dev.gkissel.forgeweave.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * {@code damage_floor} is a drawback, and a shipped {@code trait_definition} may only name it on
 * purpose (issue #1091).
 *
 * <p>{@link DamageFloor} raises a blow back toward its original damage: on a worn piece it can only
 * ever undo a reduction another trait made, and on a held tool it does nothing at all. Three
 * material traits shipped in 0.6.0-beta.2 with it read backwards --
 * {@code empowered_emeradic_bulwark}, {@code naga_ward} and {@code compressed_iron_heft}, all three
 * named and described as protection -- because nothing stopped them. This test is what stops the
 * fourth: every definition naming {@code damage_floor} has to be listed in {@link #DRAWBACKS_ON_PURPOSE}
 * below, with a line saying what the trait costs its wearer and why that is the design.
 *
 * <p>The list is empty today. {@code bloodtoll}, the one honest user of the behaviour, is a Java
 * trait ({@code ForgeweaveTraits#BLOODTOLL}) rather than a definition file, so it never appears
 * here.
 */
class TraitDefinitionAuditTest {

    private static final String DAMAGE_FLOOR = "forgeweave:damage_floor";

    /**
     * Trait definitions that carry {@code damage_floor} deliberately, as a cost the material's own
     * name and lang description own up to. Add a trait here only with a comment saying what the
     * drawback buys.
     */
    private static final Set<String> DRAWBACKS_ON_PURPOSE = Set.of();

    @Test
    void onlyDeliberateDrawbacksUseDamageFloor() throws IOException {
        Set<String> definitions = new LinkedHashSet<>();
        Set<String> usingDamageFloor = new LinkedHashSet<>();
        for (Path dataDir : List.of(projectRoot().resolve("src/main/resources/data"),
                projectRoot().resolve("src/generated/resources/data"))) {
            if (!Files.isDirectory(dataDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dataDir)) {
                for (Path file : files.filter(TraitDefinitionAuditTest::isTraitDefinition).sorted().toList()) {
                    String id = file.getFileName().toString().replace(".json", "");
                    definitions.add(id);
                    if (DAMAGE_FLOOR.equals(behaviorOf(file))) {
                        usingDamageFloor.add(id);
                    }
                }
            }
        }

        assertFalse(definitions.isEmpty(), "expected to find shipped trait definitions to audit");
        assertEquals(DRAWBACKS_ON_PURPOSE, usingDamageFloor,
                "damage_floor is a drawback: it raises a blow back toward its original damage and does nothing at "
                        + "all on a held tool. A definition may only name it if it is listed in "
                        + "TraitDefinitionAuditTest.DRAWBACKS_ON_PURPOSE with a comment saying what the cost buys, "
                        + "and its lang description has to say the trait costs the wearer something. If the trait is "
                        + "meant to protect, reach for a defensive behaviour instead (stacking_resistance, "
                        + "damage_type_immunity, evasion, invulnerability_window, death_save) or, for a tool, "
                        + "knockback_resistance");
    }

    private static boolean isTraitDefinition(Path file) {
        String path = file.toString().replace('\\', '/');
        return path.contains("/forgeweave/trait_definition/") && path.endsWith(".json");
    }

    private static String behaviorOf(Path file) throws IOException {
        JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        if (!root.isJsonObject()) {
            return null;
        }
        JsonElement behavior = root.getAsJsonObject().get("behavior");
        return behavior != null && behavior.isJsonPrimitive() ? behavior.getAsString() : null;
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }
}
