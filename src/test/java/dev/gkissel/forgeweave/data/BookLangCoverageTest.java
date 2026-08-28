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
import dev.gkissel.forgeweave.client.book.BookStructure;

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

    /**
     * Issue #651: the tool pages' "Properties:" bullets (upstream {@code ContentTool#properties},
     * ported per tool from {@code book/en_us/tools/*.json}) and the modifier pages' "Effects:"
     * bullets ({@code ContentModifier#effects}, {@code book/en_us/modifiers/*.json}). The screen
     * collects {@code <base>.property.N} / {@code <base>.effect.N} while the key exists, so a gap in
     * the run silently truncates the list -- this walks every run for contiguity, and pins that the
     * port actually happened for the tools and modifiers upstream's book covers.
     */
    @Test
    void theToolPropertyAndModifierEffectRunsAreContiguousAndPorted() throws IOException {
        JsonObject lang = lang();
        List<String> problems = new ArrayList<>();

        int toolsWithProperties = 0;
        for (Supplier<? extends Item> tool : BookContent.TOOLS) {
            if (checkRun(lang, tool.get().getDescriptionId() + ".property.", problems)) {
                toolsWithProperties++;
            }
        }
        int modifiersWithEffects = 0;
        for (net.minecraft.resources.ResourceLocation id : dev.gkissel.forgeweave.modifier.ForgeweaveModifiers.ids()) {
            if (checkRun(lang, "modifier." + id.getNamespace() + "." + id.getPath() + ".effect.", problems)) {
                modifiersWithEffects++;
            }
        }

        // The #651 content tail closed the gaps #658 left: every listed tool and every registered
        // modifier now carries a bullet run -- the 1.12 port where upstream wrote one, Forgeweave's
        // own bullets for the tools/modifiers upstream doesn't have.
        org.junit.jupiter.api.Assertions.assertEquals(BookContent.TOOLS.size(), toolsWithProperties,
                "every tool in the book's roster must have a Properties bullet run");
        org.junit.jupiter.api.Assertions.assertEquals(
                dev.gkissel.forgeweave.modifier.ForgeweaveModifiers.ids().size(), modifiersWithEffects,
                "every registered modifier must have an Effects bullet run");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * M4-7 (issue #682, extended by #735/epic #730 to the four heavy pieces): the armor section's
     * {@code tool} pages sit outside {@link BookContent#TOOLS} (the tools section's roster), so their
     * name/description pair and Properties bullet run are walked here.
     */
    @Test
    void theArmorPiecePagesHaveNameDescriptionAndProperties() throws IOException {
        JsonObject lang = lang();
        List<String> problems = new ArrayList<>();
        List<BookStructure.PageDef> pieces = BookStructure.load().sections().stream()
                .filter(section -> section.name().equals("armor"))
                .flatMap(section -> section.pages().stream())
                .filter(page -> page.type().equals("tool"))
                .toList();
        // #735 (epic #730) added the four heavy pieces alongside the original four plate ones.
        assertTrue(pieces.size() == 8, "the armor section lists the eight pieces, saw " + pieces.size());
        for (BookStructure.PageDef piece : pieces) {
            String base = "item.forgeweave." + piece.name();
            for (String key : List.of(base, base + ".description")) {
                if (!lang.has(key)) {
                    problems.add("missing " + key);
                }
            }
            if (!checkRun(lang, base + ".property.", problems)) {
                problems.add("no Properties bullet run for " + base);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** Whether {@code prefix + 0} exists; records a problem if the run has a gap after a hole. */
    private static boolean checkRun(JsonObject lang, String prefix, List<String> problems) {
        int n = 0;
        while (lang.has(prefix + n)) {
            n++;
        }
        // Anything past the first hole is unreachable by the screen's collect-while-exists loop.
        for (int probe = n + 1; probe <= n + 10; probe++) {
            if (lang.has(prefix + probe)) {
                problems.add("gap in bullet run: " + prefix + n + " is missing but " + prefix + probe + " exists");
            }
        }
        return n > 0;
    }
}
