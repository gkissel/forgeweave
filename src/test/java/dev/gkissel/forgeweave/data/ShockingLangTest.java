package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Issue #415: {@code trait.forgeweave.shocking.description} had drifted into a Forgeweave
 * rewording. Pins the generated value to upstream 1.12's {@code en_us.lang:697} verbatim (the
 * italic {@code §oBzzzzzt!§r} opener included) so a future edit can't silently reword it again.
 */
class ShockingLangTest {

    private static final String GENERATED_LANG = "src/generated/resources/assets/forgeweave/lang/en_us.json";

    /** Upstream {@code modifier.shocking.desc}, {@code resources/assets/tconstruct/lang/en_us.lang:697}. */
    private static final String UPSTREAM_DESCRIPTION = "§oBzzzzzt!§r\n"
            + "Running around, breaking blocks or hitting things charges your tool. "
            + "Hitting an enemy discharges it, dealing damage and providing a speed boost. "
            + "Mining a block discharges it, giving a mining speed boost.";

    @Test
    void descriptionMatchesUpstreamVerbatim() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(
                LocalizationAuditTest.projectRoot().resolve(GENERATED_LANG), StandardCharsets.UTF_8))
                .getAsJsonObject();

        assertEquals(UPSTREAM_DESCRIPTION, lang.get("trait.forgeweave.shocking.description").getAsString(),
                "trait.forgeweave.shocking.description must match upstream en_us.lang:697 verbatim");
    }
}
