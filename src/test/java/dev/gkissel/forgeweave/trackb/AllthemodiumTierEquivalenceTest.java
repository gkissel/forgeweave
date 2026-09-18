package dev.gkissel.forgeweave.trackb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * D-M8-19 (issue #998): the Allthemodium tier equivalence tags {@code ForgeweaveBlockTagsProvider}
 * emits, read back from {@code src/generated/resources} (the {@code ./gradlew runData} output these
 * assertions depend on being current). Two directions, three rungs each -- allthemodium ~
 * hardcinder, vibranium ~ warspar, unobtainium ~ resonite -- checked for totality (every rung's tag
 * carries every rung strictly above it) and monotonicity (never a rung below).
 */
class AllthemodiumTierEquivalenceTest {

    private static final List<String> DIRECTION_1_ORDER =
            List.of("c:ores/allthemodium", "c:ores/vibranium", "c:ores/unobtainium");

    @Test
    void direction1TagsAreOptionalAndTotal() throws IOException {
        // Rung index: allthemodium=0 (~hardcinder), vibranium=1 (~warspar), unobtainium=2 (~resonite).
        // Each forgeweave incorrect_for_*_tool file below is one rung of Forgeweave's own ladder,
        // ascending; a metal must appear in every file at or below its own rung's floor and in none
        // of the files at or above its own equivalent rung.
        JsonObject needsDiamond = readTag("data/minecraft/tags/block/needs_diamond_tool.json");
        JsonObject incorrectForDiamond = readTag("data/minecraft/tags/block/incorrect_for_diamond_tool.json");
        JsonObject incorrectForNetherite = readTag("data/minecraft/tags/block/incorrect_for_netherite_tool.json");
        JsonObject incorrectForHardcinder = readTag("data/forgeweave/tags/block/incorrect_for_hardcinder_tool.json");
        JsonObject incorrectForWarspar = readTag("data/forgeweave/tags/block/incorrect_for_warspar_tool.json");

        // Every one of the three metals sits above netherite: needsDiamond/incorrectForDiamond/
        // incorrectForNetherite must all carry all three, unconditionally.
        for (String metal : DIRECTION_1_ORDER) {
            assertOptionalEntry(needsDiamond, metal);
            assertOptionalEntry(incorrectForDiamond, metal);
            assertOptionalEntry(incorrectForNetherite, metal);
        }

        // allthemodium (~hardcinder): open at hardcinder and above -- refused below it only.
        assertNoEntry(incorrectForHardcinder, "c:ores/allthemodium");
        assertNoEntry(incorrectForWarspar, "c:ores/allthemodium");

        // vibranium (~warspar): refused to a hardcinder-tier tool (the "a rung below the equivalent
        // one is refused" pin), open at warspar and above.
        assertOptionalEntry(incorrectForHardcinder, "c:ores/vibranium");
        assertNoEntry(incorrectForWarspar, "c:ores/vibranium");

        // unobtainium (~resonite): refused to both a hardcinder-tier and a warspar-tier tool -- the
        // strongest "a rung below is refused" pin, two rungs down instead of one.
        assertOptionalEntry(incorrectForHardcinder, "c:ores/unobtainium");
        assertOptionalEntry(incorrectForWarspar, "c:ores/unobtainium");
        // resonite has no incorrect_for_resonite_tool ladder rung above it to appear in (it is
        // Forgeweave's own top rung), matching TrackBOre's own RESONITE case.
    }

    @Test
    void direction2TagsJoinTheEquivalentAllthemodiumRungOnly() throws IOException {
        JsonObject needsVibranium = readTag("data/c/tags/block/needs_vibranium_tool.json");
        JsonObject needsUnobtainium = readTag("data/c/tags/block/needs_unobtainium_tool.json");

        // warspar (~vibranium) joins needs_vibranium_tool -- ore, storage block and raw block, all
        // Forgeweave's own ids so no optional marker is needed on this half.
        assertRequiredEntry(needsVibranium, "forgeweave:warspar_ore");
        assertRequiredEntry(needsVibranium, "forgeweave:warspar_block");
        assertRequiredEntry(needsVibranium, "forgeweave:raw_warspar_block");
        assertNoEntry(needsVibranium, "forgeweave:resonite_ore");

        // resonite (~unobtainium) joins needs_unobtainium_tool only -- not needs_vibranium_tool too,
        // since incorrect_for_vibranium_tool already unions needs_unobtainium_tool in, and a second
        // entry there would be redundant.
        assertRequiredEntry(needsUnobtainium, "forgeweave:resonite_ore");
        assertRequiredEntry(needsUnobtainium, "forgeweave:resonite_block");
        assertRequiredEntry(needsUnobtainium, "forgeweave:raw_resonite_block");
        assertNoEntry(needsUnobtainium, "forgeweave:warspar_ore");

        // hardcinder (~allthemodium, the floor rung) joins neither -- its floor is already carried by
        // the vanilla needsDiamond/incorrectForNetherite membership TrackBOre.ALL's own HARDCINDER
        // case gives it, and nothing in Allthemodium's own family sits below allthemodium tier.
        assertNoEntry(needsVibranium, "forgeweave:hardcinder_ore");
        assertNoEntry(needsUnobtainium, "forgeweave:hardcinder_ore");
    }

    /** Every value entry is either a plain string (Forgeweave's own ids) or an optional tag object. */
    private static void assertOptionalEntry(JsonObject tag, String id) {
        for (JsonElement value : tag.getAsJsonArray("values")) {
            if (value.isJsonObject()) {
                JsonObject entry = value.getAsJsonObject();
                if (entry.get("id").getAsString().equals("#" + id)) {
                    assertFalse(entry.get("required").getAsBoolean(),
                            id + " must be an optional tag reference (required: false), got " + entry);
                    return;
                }
            }
        }
        throw new AssertionError(id + " expected as an optional entry, found none in " + tag);
    }

    private static void assertRequiredEntry(JsonObject tag, String id) {
        for (JsonElement value : tag.getAsJsonArray("values")) {
            if (value.isJsonPrimitive() && value.getAsString().equals(id)) {
                return;
            }
        }
        throw new AssertionError(id + " expected as a plain entry, found none in " + tag);
    }

    private static void assertNoEntry(JsonObject tag, String id) {
        JsonArray values = tag.getAsJsonArray("values");
        for (JsonElement value : values) {
            String candidate = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
            assertTrue(!candidate.equals(id) && !candidate.equals("#" + id),
                    id + " must not appear in " + tag + ", found it anyway");
        }
    }

    private static JsonObject readTag(String relativePath) throws IOException {
        Path path = projectRoot().resolve("src/generated/resources").resolve(relativePath);
        assertTrue(Files.exists(path), "missing generated tag file (run ./gradlew runData): " + path);
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
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
