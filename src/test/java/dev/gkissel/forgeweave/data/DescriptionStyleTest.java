package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.TraitFamilies;

/**
 * The description style guard (issue #1118): every trait and modifier description says what the
 * thing does <em>and by how much</em>, in seconds rather than ticks. {@code
 * docs/agents/description-style.md} is the rule set; this is what stops it rotting.
 *
 * <p>Two checks, both over the generated {@code en_us.json} rather than over
 * {@link ForgeweaveLanguageProvider}'s source, so a key assembled from several concatenated string
 * literals is judged as the player reads it:
 *
 * <ul>
 *   <li>the description states a number -- a digit, or a {@code %s} the renderer fills from a
 *       family rung's or a {@code trait_definition}'s {@code description_args}, which is the
 *       preferred form because it cannot drift from the constant it quotes;
 *   <li>and it never says "tick". 100 ticks is five seconds, and a player counts seconds.
 * </ul>
 *
 * <p>{@link #NUMBERLESS_ON_PURPOSE} is the escape hatch, and it is an exact-match assertion rather
 * than a floor: an effect with no magnitude at all (an immunity, a boolean opt-in, a tier marker)
 * belongs on it with a line saying why, and an id that later gains a number has to come back off,
 * which is what keeps the list honest.
 */
class DescriptionStyleTest {

    private static final String GENERATED_LANG = "src/generated/resources/assets/forgeweave/lang/en_us.json";

    /** "tick" as a whole word, so "sticks" and "ticket" are not false positives. */
    private static final Pattern TICKS = Pattern.compile("\\btick", Pattern.CASE_INSENSITIVE);

    private static final Pattern DIGIT = Pattern.compile("\\d");

    /**
     * Traits whose effect has no magnitude to state, each with the reason it has none. Nothing here
     * is a description that is merely unfinished -- an unfinished one is a failure, not an entry.
     */
    private static final Map<String, String> NUMBERLESS_TRAITS = Map.of(
            // Whole damage types turned off outright; there is no partial figure to quote.
            "fireward", "complete immunity to fire, lava and blaze damage",
            "stormward", "complete immunity to lightning damage",
            // A boolean opt-in on the drop table: the block drops its smelted form or it does not.
            "autosmelt", "mined blocks drop their smelted result, with no rate or amount",
            "searing", "mined blocks drop their smelted result, with no rate or amount",
            // The four Draconic tier markers have no hook at all -- they only say which tier of
            // fusion metal a tool is made of, so a fusion recipe can refuse the wrong catalyst.
            "evolving", "a tier marker with no effect; the numbers are in its state line",
            "evolved", "a tier marker with no effect; the numbers are in its state line",
            "evolved2", "a tier marker with no effect; the numbers are in its state line",
            "evolved3", "a tier marker with no effect; the numbers are in its state line",
            // Pinned verbatim to upstream 1.12's own en_us.lang:697 by ShockingLangTest (issue #415).
            "shocking", "pinned to upstream's wording by ShockingLangTest, so it cannot be reworded");

    /** The same escape hatch for modifiers; see {@link #NUMBERLESS_TRAITS}. */
    private static final Map<String, String> NUMBERLESS_MODIFIERS = Map.of(
            "searing", "mined blocks drop their smelted result, with no rate or amount",
            "magnetic_pull", "drops route to the inventory or they do not; there is no range or rate",
            "soulbound", "the tool survives death or it does not",
            "elytra_flight", "the chestplate glides or it does not; gliding has no magnitude here",
            "goggles", "mounts Create's own goggles, whose overlays are Create's to size");
    // Not listed: fortification, whose shipped ids are per-material (forgeweave:fortification.cobalt)
    // and so never reach ForgeweaveModifiers.ids(). Its own description names a material, not a
    // magnitude, and ModifierApplication#description resolves it through material_description.

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyTraitDescriptionStatesItsNumber() throws Exception {
        JsonObject lang = generatedLang();
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (Map.Entry<String, String> trait : liveTraitLangBases().entrySet()) {
            String key = trait.getValue() + ".description";
            assertTrue(lang.has(key), trait.getKey() + " ships without " + key);
            descriptions.put(trait.getKey(), lang.get(key).getAsString());
        }

        assertTrue(descriptions.size() > 150,
                "expected the whole trait roster, walked only " + descriptions.size());
        assertEquals(new TreeSet<>(NUMBERLESS_TRAITS.keySet()), numberless(descriptions),
                "a trait description has to say by how much (docs/agents/description-style.md). Add the "
                        + "number, or -- only if the effect genuinely has none -- list the id in "
                        + "DescriptionStyleTest.NUMBERLESS_TRAITS with the reason. An id that gained a number "
                        + "comes back off that list");
        assertEquals(List.of(), mentioningTicks(descriptions),
                "player-facing text has no such word as \"tick\": 100 ticks is 5 seconds");
    }

    @Test
    void everyModifierDescriptionStatesItsNumber() throws IOException {
        JsonObject lang = generatedLang();
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (ResourceLocation id : ForgeweaveModifiers.ids()) {
            String key = "modifier." + id.getNamespace() + "." + id.getPath() + ".description";
            assertTrue(lang.has(key), id + " ships without " + key);
            descriptions.put(id.getPath(), lang.get(key).getAsString());
        }

        assertTrue(descriptions.size() >= 24,
                "expected the whole modifier roster, walked only " + descriptions.size());
        assertEquals(new TreeSet<>(NUMBERLESS_MODIFIERS.keySet()), numberless(descriptions),
                "a modifier description has to say what one level is worth "
                        + "(docs/agents/description-style.md). Add the number, or -- only if the effect "
                        + "genuinely has none -- list the id in DescriptionStyleTest.NUMBERLESS_MODIFIERS "
                        + "with the reason");
        assertEquals(List.of(), mentioningTicks(descriptions),
                "player-facing text has no such word as \"tick\": 100 ticks is 5 seconds");
    }

    /** A modifier's book bullets say what it costs, so the one fact the description leaves out is there. */
    @Test
    void everyModifierWithBulletsSaysWhatItCosts() throws IOException {
        JsonObject lang = generatedLang();
        List<String> silent = new ArrayList<>();
        for (ResourceLocation id : ForgeweaveModifiers.ids()) {
            String base = "modifier." + id.getNamespace() + "." + id.getPath() + ".effect.";
            if (!lang.has(base + "0")) {
                continue; // no bullet list at all; JEI and the description carry it instead
            }
            boolean saysCost = false;
            for (int i = 0; lang.has(base + i); i++) {
                if (lang.get(base + i).getAsString().toLowerCase().contains("modifier slot")) {
                    saysCost = true;
                }
            }
            if (!saysCost) {
                silent.add(id.getPath());
            }
        }
        assertEquals(List.of(), silent, "these modifiers list their effects in the book without saying what "
                + "they cost in modifier slots; add a \"Costs ...\" bullet to modifierEffects");
    }

    private static TreeSet<String> numberless(Map<String, String> descriptions) {
        TreeSet<String> out = new TreeSet<>();
        descriptions.forEach((id, text) -> {
            if (!text.contains("%s") && !DIGIT.matcher(text).find()) {
                out.add(id);
            }
        });
        return out;
    }

    private static List<String> mentioningTicks(Map<String, String> descriptions) {
        List<String> out = new ArrayList<>();
        descriptions.forEach((id, text) -> {
            if (TICKS.matcher(text).find()) {
                out.add(id);
            }
        });
        return out;
    }

    /**
     * Every registered trait id mapped to the lang path its name and description live under: the
     * family's where the id is a rung, its own otherwise.
     *
     * <p>The Java roster reads {@link TraitFamilies#langBase}, which already knows its own families.
     * A {@code trait_definition}'s family is read straight off the file instead, because
     * {@code TraitFamilies}' datapack snapshot is filled by a data load and a unit test never runs
     * one -- the same reason {@code TraitDefinitionAuditTest} walks the files itself.
     */
    private static Map<String, String> liveTraitLangBases() throws Exception {
        Map<String, String> bases = new LinkedHashMap<>();
        for (ResourceLocation id : javaTraitIds()) {
            bases.put(id.getPath(), TraitFamilies.langBase(id));
        }
        for (Path file : traitDefinitionFiles()) {
            String id = file.getFileName().toString().replace(".json", "");
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            JsonElement family = root.getAsJsonObject().get("family");
            String path = family == null || family.getAsString().isEmpty() ? id : family.getAsString();
            bases.put(id, "trait.forgeweave." + path);
        }
        return bases;
    }

    /** {@code ForgeweaveTraits.REGISTRY}, by reflection, the way {@code TraitDefinitionAuditTest} reads it. */
    @SuppressWarnings("unchecked")
    private static Set<ResourceLocation> javaTraitIds() throws Exception {
        var field = ForgeweaveTraits.class.getDeclaredField("REGISTRY");
        field.setAccessible(true);
        return new LinkedHashSet<>(((Map<ResourceLocation, Trait>) field.get(null)).keySet());
    }

    private static List<Path> traitDefinitionFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path dataDir : List.of(LocalizationAuditTest.projectRoot().resolve("src/main/resources/data"),
                LocalizationAuditTest.projectRoot().resolve("src/generated/resources/data"))) {
            if (!Files.isDirectory(dataDir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dataDir)) {
                walk.filter(DescriptionStyleTest::isTraitDefinition).sorted().forEach(files::add);
            }
        }
        return files;
    }

    private static boolean isTraitDefinition(Path file) {
        String path = file.toString().replace('\\', '/');
        return path.contains("/forgeweave/trait_definition/") && path.endsWith(".json");
    }

    private static JsonObject generatedLang() throws IOException {
        return JsonParser.parseString(Files.readString(
                LocalizationAuditTest.projectRoot().resolve(GENERATED_LANG), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
