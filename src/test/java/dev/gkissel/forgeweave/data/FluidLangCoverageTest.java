package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

/**
 * Every registered fluid has its {@code fluid_type.forgeweave.<name>} key in the generated {@code
 * en_us.json}. Molten basalt shipped without one, and nobody saw it until the guide book started
 * listing an alloy's inputs by fluid name: quakestone's page read {@code
 * fluid_type.forgeweave.molten_basalt} where "Molten Basalt" belonged.
 */
class FluidLangCoverageTest {

    private static final String GENERATED_LANG = "src/generated/resources/assets/forgeweave/lang/en_us.json";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyRegisteredFluidHasItsLangKey() throws IOException {
        Path root = LocalizationAuditTest.projectRoot();
        JsonObject lang = JsonParser.parseString(
                Files.readString(root.resolve(GENERATED_LANG), StandardCharsets.UTF_8)).getAsJsonObject();

        List<String> missing = ForgeweaveFluids.all().stream()
                .map(fluid -> "fluid_type.forgeweave." + fluid.name())
                .filter(key -> !lang.has(key))
                .toList();

        // Non-vacuity: an empty roster would prove nothing.
        assertTrue(ForgeweaveFluids.all().size() >= 100, "walked only " + ForgeweaveFluids.all().size() + " fluids");
        assertTrue(missing.isEmpty(),
                "fluids shipped without a name -- add an addFluid line to ForgeweaveLanguageProvider "
                        + "and re-run data generation:\n" + String.join("\n", missing));
    }
}
