package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;

import dev.gkissel.forgeweave.client.book.BookContent;

/**
 * Guards the guide book's content tree (issue #273): every fixed lang key the book structure
 * references ({@link BookContent#staticLangKeys()}) and every listed tool's name/description pair
 * must exist in the generated {@code en_us.json}, or a page ships showing a raw translation key.
 * The registry-driven material and modifier pages are already covered by
 * {@link MaterialLangCoverageTest} and {@link ModifierLangCoverageTest} -- the book builds its keys
 * with the exact same {@code material.<ns>.<path>} / {@code modifier.<ns>.<path>.name|.description}
 * shapes those tests walk.
 */
class BookLangCoverageTest {

    private static final String GENERATED_LANG = "src/generated/resources/assets/forgeweave/lang/en_us.json";

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject lang() throws IOException {
        return JsonParser.parseString(Files.readString(
                LocalizationAuditTest.projectRoot().resolve(GENERATED_LANG), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    void everyStaticBookKeyHasALangEntry() throws IOException {
        JsonObject lang = lang();
        List<String> missing = BookContent.staticLangKeys().stream()
                .filter(key -> !lang.has(key))
                .toList();

        // Non-vacuity: cover/index chrome, five section titles, and eight static page pairs.
        assertTrue(BookContent.staticLangKeys().size() >= 24,
                "expected the book's full static key manifest, saw only " + BookContent.staticLangKeys().size());
        assertTrue(missing.isEmpty(),
                "guide book pages reference lang keys with no entry -- add them to "
                        + "ForgeweaveLanguageProvider and re-run data generation:\n" + String.join("\n", missing));
    }

    @Test
    void everyListedToolHasNameAndDescriptionKeys() throws IOException {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (Supplier<? extends Item> tool : BookContent.TOOLS) {
            String base = tool.get().getDescriptionId();
            for (String key : List.of(base, base + ".description")) {
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
        }

        // Non-vacuity: the M1 harvest trio through the M3 weapons -- at least 21 tools listed.
        assertTrue(BookContent.TOOLS.size() >= 21,
                "expected every assembled tool in the book's tools section, saw " + BookContent.TOOLS.size());
        assertTrue(missing.isEmpty(),
                "guide book tool pages reference lang keys with no entry:\n" + String.join("\n", missing));
    }
}
