package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Guards the M3.2 roster's localization (issue #227): every shipped material JSON under {@code
 * data/forgeweave/forgeweave/material/} must have its {@code material.forgeweave.<name>} key in the
 * generated {@code en_us.json}. Nothing else enforces this -- a material added without its
 * {@link ForgeweaveLanguageProvider} line ships a raw translation key silently, so this test walks
 * the actual source tree (not the classpath) and fails the build instead.
 */
class MaterialLangCoverageTest {

    private static final String MATERIAL_DIR = "src/main/resources/data/forgeweave/forgeweave/material";
    private static final String GENERATED_LANG = "src/generated/resources/assets/forgeweave/lang/en_us.json";

    @Test
    void everyShippedMaterialHasItsLangKey() throws IOException {
        Path root = LocalizationAuditTest.projectRoot();
        JsonObject lang = JsonParser.parseString(
                Files.readString(root.resolve(GENERATED_LANG), StandardCharsets.UTF_8)).getAsJsonObject();

        List<String> missing = new ArrayList<>();
        int materials = 0;
        try (Stream<Path> files = Files.list(root.resolve(MATERIAL_DIR))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                materials++;
                String name = file.getFileName().toString().replace(".json", "");
                String key = "material.forgeweave." + name;
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
        }

        // Non-vacuity: the M1 four plus the M3 metals; an empty walk would prove nothing.
        assertTrue(materials >= 11, "expected at least the 11 pre-M3.2 materials, walked only " + materials);
        assertTrue(missing.isEmpty(),
                "materials shipped without a lang line -- add them to ForgeweaveLanguageProvider "
                        + "and re-run data generation:\n" + String.join("\n", missing));
    }
}
