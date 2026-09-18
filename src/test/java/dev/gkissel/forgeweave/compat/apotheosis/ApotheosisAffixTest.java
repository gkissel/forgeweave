package dev.gkissel.forgeweave.compat.apotheosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * The shipped Apotheosis loot-category overrides (issue #970, docs/SCOPE.md M8, D-M8-1).
 *
 * <p>Affixability is Apotheosis' own predicate walk over the item, so almost every Forgeweave shape
 * lands in the right category with nothing registered -- see {@link ApotheosisAffixes} for the
 * per-shape reading and where it came from. The ranged family is the exception, because Apotheosis'
 * {@code bow} category tests {@code instanceof} two vanilla classes that Forgeweave's bow items
 * deliberately do not extend. This pins the three-row correction that fixes it, and pins that it
 * stays three rows: every other override is a silent deviation from the vanilla tool of the same
 * kind, which is the thing a reviewer would have to be told about.
 */
class ApotheosisAffixTest {

    private static final String OVERRIDES =
            "src/main/resources/data/apotheosis/data_maps/item/loot_category_overrides.json";

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static JsonObject overrides() throws IOException {
        Path file = projectRoot().resolve(OVERRIDES);
        assertTrue(Files.exists(file), "missing " + file);
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void exactlyTheThreeRangedItemsAreOverriddenIntoTheBowCategory() throws IOException {
        JsonObject values = overrides().getAsJsonObject("values");

        Map<String, String> actual = new TreeMap<>();
        values.entrySet().forEach(entry -> actual.put(entry.getKey(), entry.getValue().getAsString()));

        assertEquals(
                Map.of("forgeweave:shortbow", "apotheosis:bow",
                        "forgeweave:longbow", "apotheosis:bow",
                        "forgeweave:crossbow", "apotheosis:bow"),
                actual,
                "only the three ranged items need an override: their item classes are not vanilla's "
                        + "BowItem or CrossbowItem, so Apotheosis' bow predicate never matches them. Every "
                        + "other shape is left to the predicate walk on purpose, because that puts it where "
                        + "the vanilla tool of the same kind goes");
    }

    @Test
    void theOverridesMergeRatherThanReplaceAndOnlyLoadWithApotheosis() throws IOException {
        JsonObject root = overrides();

        assertFalse(root.get("replace").getAsBoolean(),
                "the file must merge into Apotheosis' own data map rather than replace it, or shipping "
                        + "three Forgeweave rows would drop every row Apotheosis and every other pack wrote");

        JsonObject condition = root.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject();
        assertEquals("neoforge:mod_loaded", condition.get("type").getAsString());
        assertEquals(ApotheosisSockets.MODID, condition.get("modid").getAsString(),
                "gated on Apotheosis so a Forgeweave-only datapack drops the file instead of naming "
                        + "category ids nothing defines, the same condition the socket recipes carry");
    }

    @Test
    void theRangedItemsTheOverrideNamesAreTheRangedItemsForgeweaveShips() throws IOException {
        // A cheap guard against the pair drifting: a fourth bow, or a rename, has to show up in both
        // places or this fails. Reading ForgeweaveItems here would need a booted registry; the
        // registered-name strings are what the data map keys on anyway.
        String items = Files.readString(
                projectRoot().resolve("src/main/java/dev/gkissel/forgeweave/item/ForgeweaveItems.java"),
                StandardCharsets.UTF_8);

        for (String name : List.of("shortbow", "longbow", "crossbow")) {
            assertTrue(items.contains("\"" + name + "\""),
                    "the override names forgeweave:" + name + ", which ForgeweaveItems no longer registers");
        }
    }
}
