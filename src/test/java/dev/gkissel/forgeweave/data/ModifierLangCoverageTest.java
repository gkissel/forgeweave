package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;

/**
 * Guards the modifier hover tooltips' localization (issue #258): every id in
 * {@code ForgeweaveModifiers}' registry must have both its {@code modifier.forgeweave.<id>.name} and
 * {@code .description} keys in the generated {@code en_us.json} -- the Tool Station's modifier rows
 * hover-show both, so a modifier added without either ships a raw translation key silently. Same
 * shape as {@link MaterialLangCoverageTest}, reading the registry instead of a datapack tree.
 */
class ModifierLangCoverageTest {

    private static final String GENERATED_LANG = "src/generated/resources/assets/forgeweave/lang/en_us.json";

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyRegisteredModifierHasNameAndDescriptionKeys() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(
                LocalizationAuditTest.projectRoot().resolve(GENERATED_LANG), StandardCharsets.UTF_8))
                .getAsJsonObject();

        List<String> missing = new ArrayList<>();
        for (ResourceLocation id : ForgeweaveModifiers.ids()) {
            for (String suffix : List.of(".name", ".description")) {
                String key = "modifier." + id.getNamespace() + "." + id.getPath() + suffix;
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
        }

        // Non-vacuity: the M2 fifteen plus the M3 combat eight plus wind burst; an empty registry
        // walk would prove nothing.
        assertTrue(ForgeweaveModifiers.ids().size() >= 24,
                "expected at least the 24 pre-M3.2 modifiers, walked only " + ForgeweaveModifiers.ids().size());
        assertTrue(missing.isEmpty(),
                "modifiers shipped without lang lines -- add them to ForgeweaveLanguageProvider "
                        + "and re-run data generation:\n" + String.join("\n", missing));
    }

    /** Embossing's generated ids resolve through one shared key pair; guard those two as well. */
    @Test
    void embossmentSharedKeysExist() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(
                LocalizationAuditTest.projectRoot().resolve(GENERATED_LANG), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertTrue(lang.has("modifier.forgeweave.embossment.name"), "modifier.forgeweave.embossment.name missing");
        assertTrue(lang.has("modifier.forgeweave.embossment.description"),
                "modifier.forgeweave.embossment.description missing");
    }
}
