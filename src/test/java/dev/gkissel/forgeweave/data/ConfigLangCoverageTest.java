package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.neoforged.neoforge.common.ModConfigSpec;

import dev.gkissel.forgeweave.config.ForgeweaveClientConfig;
import dev.gkissel.forgeweave.config.ForgeweaveConfigMigration;

/**
 * Every config entry, section and file has a display name in en_us.json. NeoForge's config screen
 * shows the raw {@code forgeweave.configuration.<key>} for a missing one, and the same "Server
 * Settings" label on every server file without its {@code section.<file>} key.
 */
class ConfigLangCoverageTest {

    @Test
    void everyConfigKeyAndFileHasADisplayName() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(
                LocalizationAuditTest.projectRoot().resolve("src/generated/resources/assets/forgeweave/lang/en_us.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        List<String> wanted = new ArrayList<>();
        ForgeweaveConfigMigration.FILES.forEach((fileName, spec) -> {
            wanted.add(fileKey(fileName));
            collect(spec.getSpec(), wanted);
        });
        wanted.add(fileKey("forgeweave-client.toml"));
        collect(ForgeweaveClientConfig.SPEC.getSpec(), wanted);

        List<String> missing = wanted.stream().filter(key -> !lang.has(key)).toList();
        assertEquals(List.of(), missing, "config keys with no display name");
    }

    /** {@code ConfigurationScreen#translatableConfig}'s own mangling of a file name. */
    private static String fileKey(String fileName) {
        return "forgeweave.configuration.section." + fileName.replaceAll("[^a-zA-Z0-9]+", ".")
                .replaceFirst("^\\.", "").replaceFirst("\\.$", "").toLowerCase(Locale.ENGLISH);
    }

    private static void collect(UnmodifiableConfig level, List<String> out) {
        for (UnmodifiableConfig.Entry entry : level.entrySet()) {
            out.add("forgeweave.configuration." + entry.getKey());
            if (entry.getRawValue() instanceof UnmodifiableConfig branch) {
                collect(branch, out);
            } else if (!(entry.getRawValue() instanceof ModConfigSpec.ValueSpec)) {
                throw new IllegalStateException("unexpected config entry " + entry.getKey());
            }
        }
    }
}
