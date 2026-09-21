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
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;

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

    /** {@code TranslatableContents#FORMAT_PATTERN}, verbatim, so this test cannot disagree with the game. */
    private static final Pattern VANILLA_FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    /** One argument slot: {@code %s} or its indexed form {@code %1$s}. */
    private static final Pattern FORMAT_ARGUMENT = Pattern.compile("%(?:\\d+\\$)?s");

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

    /**
     * A lang string that takes arguments has to survive vanilla's own decomposition, or the player
     * reads the template instead of the sentence.
     *
     * <p>{@code TranslatableContents#decomposeTemplate} walks {@link #VANILLA_FORMAT}'s matches and
     * throws on any literal {@code %} between or after them, and on any format letter but {@code s};
     * {@code TranslatableContents#decompose} catches that and renders the raw string. So
     * {@code "has a %s% chance"} shows a player the literal {@code "%s% chance"} rather than
     * {@code "has a 20% chance"}. A literal percent inside a template is written {@code %%}.
     *
     * <p>A string with no arguments and a bare {@code 10%} takes the same exception path, and comes
     * out right only because the raw string is the sentence. Those are left alone -- which is safe
     * exactly as long as nothing passes them arguments, and
     * {@link #everyRungSuppliesAsManyArgumentsAsItsSentenceTakes} is what checks that for the one
     * mechanism that could.
     */
    @Test
    void everyArgumentBearingStringSurvivesVanillasFormatter() throws IOException {
        JsonObject lang = generatedLang();
        List<String> broken = new ArrayList<>();
        for (String key : lang.keySet()) {
            String value = lang.get(key).getAsString();
            if (!FORMAT_ARGUMENT.matcher(value).find()) {
                continue;
            }
            String problem = decomposeFailure(value);
            if (problem != null) {
                broken.add(key + ": " + problem + " in \"" + value + "\"");
            }
        }
        assertEquals(List.of(), broken, "these lang strings take arguments and would throw in "
                + "TranslatableContents#decomposeTemplate, so the game renders the raw template "
                + "instead of the sentence. Write a literal percent as %% and use no format letter "
                + "but s");
    }

    /**
     * A family rung's sentence takes exactly as many {@code %s} as the rung supplies arguments.
     * One too few and the extra argument is dropped silently; one too many and the player reads a
     * missing-argument error component in the middle of the line.
     */
    @Test
    void everyRungSuppliesAsManyArgumentsAsItsSentenceTakes() throws Exception {
        JsonObject lang = generatedLang();
        List<String> mismatched = new ArrayList<>();
        Map<String, List<String>> rungs = new LinkedHashMap<>();
        for (ResourceLocation id : javaTraitIds()) {
            TraitFamilies.Rung rung = TraitFamilies.of(id);
            if (rung != null) {
                rungs.put(id.getPath(), rung.descriptionArgs());
            }
        }
        for (Path file : traitDefinitionFiles()) {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (!root.has("family") || root.get("family").getAsString().isEmpty()) {
                continue;
            }
            List<String> args = new ArrayList<>();
            if (root.has("description_args")) {
                root.getAsJsonArray("description_args").forEach(arg -> args.add(arg.getAsString()));
            }
            rungs.put(file.getFileName().toString().replace(".json", ""), args);
        }

        Map<String, String> bases = liveTraitLangBases();
        rungs.forEach((id, args) -> {
            String key = bases.get(id) + ".description";
            if (!lang.has(key)) {
                mismatched.add(id + " has no " + key);
                return;
            }
            int placeholders = 0;
            var matcher = FORMAT_ARGUMENT.matcher(lang.get(key).getAsString());
            while (matcher.find()) {
                placeholders++;
            }
            if (placeholders != args.size()) {
                mismatched.add(id + ": " + key + " takes " + placeholders + " arguments, the rung supplies "
                        + args.size());
            }
        });

        assertTrue(rungs.size() > 20, "expected the family rungs, walked only " + rungs.size());
        assertEquals(List.of(), mismatched, "a rung's numbers and its family's sentence have to line up");
    }

    /**
     * The one end-to-end check: resolve a family's sentence through the real
     * {@code TranslatableContents}, against the shipped {@code en_us.json}, and read the line a
     * player would. {@code crude}'s two rungs quote one number each out of one lang entry, and a
     * literal percent sign has to survive next to them.
     *
     * <p>Injecting a {@link Language} is global, so the previous one goes back in a finally: the
     * whole module's tests share a JVM.
     */
    @Test
    void aFamilySentenceRendersWithItsRungsNumbersInIt() throws IOException {
        JsonObject lang = generatedLang();
        Language previous = Language.getInstance();
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return lang.has(key) ? lang.get(key).getAsString() : fallback;
            }

            @Override
            public boolean has(String key) {
                return lang.has(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.EMPTY;
            }
        });
        try {
            assertEquals("Deals 15% more damage to unarmored targets.",
                    TraitFamilies.description(ResourceLocation.fromNamespaceAndPath("forgeweave", "crude"))
                            .getString());
            assertEquals("Deals 30% more damage to unarmored targets.",
                    TraitFamilies.description(ResourceLocation.fromNamespaceAndPath("forgeweave", "crude2"))
                            .getString());
        } finally {
            Language.inject(previous);
        }
    }

    /**
     * Vanilla's own {@code TranslatableContents#decomposeTemplate}, reduced to the reason it would
     * throw, or {@code null} when the string parses. Mirrored rather than called because the real
     * one needs a {@code Language} to resolve against.
     */
    private static String decomposeFailure(String template) {
        var matcher = VANILLA_FORMAT.matcher(template);
        int from = 0;
        while (matcher.find(from)) {
            if (matcher.start() > from && template.substring(from, matcher.start()).indexOf('%') != -1) {
                return "stray percent in \"" + template.substring(from, matcher.start()) + "\"";
            }
            String letter = matcher.group(2);
            String whole = template.substring(matcher.start(), matcher.end());
            if (!("%".equals(letter) && "%%".equals(whole)) && !"s".equals(letter)) {
                return "unsupported format \"" + whole + "\"";
            }
            from = matcher.end();
        }
        if (from < template.length() && template.substring(from).indexOf('%') != -1) {
            return "stray percent in \"" + template.substring(from) + "\"";
        }
        return null;
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
