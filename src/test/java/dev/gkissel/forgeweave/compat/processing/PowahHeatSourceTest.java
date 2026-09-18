package dev.gkissel.forgeweave.compat.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

/**
 * Issue #995 (D-M8-13): every {@code powah:heat_source} entry {@code
 * scripts/generate_compat_processing.py} writes must carry the fuel ladder's own temperature for that
 * fluid -- {@link ForgeweaveFluids}' own registered numbers, not a second table retyped by hand. "Take
 * the numbers from the fuel ladder, not from a fresh table" is the issue's own instruction; this test
 * is what enforces it.
 */
class PowahHeatSourceTest {

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
    void everyEntryMatchesTheFuelLaddersOwnTemperature() throws IOException {
        Path file = projectRoot().resolve("src/main/resources/data/powah/data_maps/fluid/heat_source.json");
        JsonObject values = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("values");

        Map<String, Integer> expected = Map.of(
                "forgeweave:blazing_blood", ForgeweaveFluids.BLAZING_BLOOD.temperature(),
                "forgeweave:molten_magma", ForgeweaveFluids.MOLTEN_MAGMA.temperature(),
                "forgeweave:molten_brimspar", ForgeweaveFluids.BRIMSPAR.temperature(),
                "forgeweave:molten_pyrealloy", ForgeweaveFluids.PYREALLOY.temperature());

        assertEquals(expected.keySet(), values.keySet(), "the data map's fluid set");
        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            int written = values.getAsJsonObject(entry.getKey()).get("temperature").getAsInt();
            assertEquals(entry.getValue(), written, entry.getKey() + "'s heat_source temperature");
        }
    }
}
