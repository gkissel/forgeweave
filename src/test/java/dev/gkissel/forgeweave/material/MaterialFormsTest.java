package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * Issue #992 (M8-8, docs/SCOPE.md D-M8-6 and D-M8-7): walks {@link MaterialForms#ALL} and pins that
 * every material gets every form it is owed, all the way out to the shipped files -- registration,
 * lang line, item model, sprite, {@code c:} leaf tag, the family parent that names the leaf, and the
 * tag-keyed melting row. Walking the roster rather than a hand list is the point: a material added to
 * {@link TrackBOre} or {@link TrackBAlloy} without its forms fails here.
 *
 * <p>Generated output is committed (docs/adr/0002), so this reads the real source tree the way
 * {@code ConventionTagsTest} and {@code MaterialLangCoverageTest} already do, rather than re-running
 * datagen.
 */
class MaterialFormsTest {

    private static final String LANG = "src/generated/resources/assets/forgeweave/lang/en_us.json";
    private static final String MODEL_DIR = "src/generated/resources/assets/forgeweave/models/item";
    private static final String SPRITE_DIR = "src/main/resources/assets/forgeweave/textures/item";
    private static final String ITEM_TAG_DIR = "src/generated/resources/data/c/tags/item";
    private static final String FLUID_TAG_DIR = "src/generated/resources/data/c/tags/fluid";
    private static final String MELTING_DIR = "src/main/resources/data/forgeweave/forgeweave/melting_recipe";

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static Set<String> tagValues(String relativePath) throws IOException {
        Path json = projectRoot().resolve(relativePath);
        assertTrue(Files.isRegularFile(json), "expected a generated tag file at " + json);
        JsonObject root = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
        return StreamSupport.stream(root.getAsJsonArray("values").spliterator(), false)
                .map(JsonElement::getAsString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static JsonObject lang() throws IOException {
        return JsonParser.parseString(
                Files.readString(projectRoot().resolve(LANG), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void theRosterCoversEveryTrackBMaterialPlusTheTenOwnItemMetalsAndTheTwoGems() {
        assertEquals(11, TrackBOre.ALL.size(), "Track B's ore roster");
        assertEquals(26, TrackBAlloy.ALL.size(),
                "Track B's alloy roster, the welds and atomic matter alloy included");
        assertEquals(10, MaterialForms.OWN_ITEM_METALS.size(), "D-M8-7's own-item metals");
        assertEquals(TrackBOre.ALL.size() + TrackBAlloy.ALL.size() + 10 + 1, MaterialForms.ALL.size(),
                "the roster is Track B's 37, the ten own-item metals and brimspar");

        // 37 Track B materials, 36 of them with an ingot: fulmenite's ore drops a crystal (#929).
        long trackBWithIngot = MaterialForms.ALL.stream()
                .filter(material -> TrackBOre.ALL.stream().anyMatch(ore -> ore.id().equals(material.id()))
                        || TrackBAlloy.ALL.stream().anyMatch(alloy -> alloy.id().equals(material.id())))
                .filter(MaterialForms.FormedMaterial::hasIngot)
                .count();
        assertEquals(36, trackBWithIngot);

        Set<String> ids = MaterialForms.ALL.stream().map(MaterialForms.FormedMaterial::id)
                .collect(Collectors.toSet());
        assertEquals(MaterialForms.ALL.size(), ids.size(), "no material appears twice: " + ids);
    }

    @Test
    void gemTypeMaterialsGetTheThreeDustsAndNoPlateFamily() {
        for (String id : List.of("brimspar", "fulmenite")) {
            MaterialForms.FormedMaterial material = material(id);
            assertEquals(MaterialForm.DUSTS, material.forms(), id + " should get dusts only");
            for (MaterialForm form : MaterialForm.PLATE_FAMILY) {
                assertNull(ForgeweaveItems.materialForm(id, form),
                        id + " must not register a " + form.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    @Test
    void fulmenitesShippedIngotNuggetAndStorageBlockIdsAreUntouched() {
        assertEquals("forgeweave:fulmenite_ingot", ForgeweaveItems.trackBIngot("fulmenite").getId().toString());
        assertEquals("forgeweave:fulmenite_nugget", ForgeweaveItems.trackBNugget("fulmenite").getId().toString());
        assertEquals("forgeweave:fulmenite_block",
                ForgeweaveItems.trackBStorageBlockItem("fulmenite").getId().toString());
        assertEquals("forgeweave:fulmenite_crystal", ForgeweaveItems.trackBCrystal("fulmenite").getId().toString());
    }

    @Test
    void trackAMaterialsGetNoForgeweaveSideForms() {
        // D-M8-6: another mod's metal keeps its own item forms. A Track A id in the roster would mean
        // Forgeweave minting a second copy of, say, Mekanism's steel plate.
        for (String id : List.of("bronze", "lead", "osmium", "electrum", "invar", "tin", "uranium")) {
            assertNotNull(ForgeweaveFluids.compatMetalFluid(id), id + " should be a Track A metal");
            assertTrue(MaterialForms.ALL.stream().noneMatch(material -> material.id().equals(id)),
                    "Track A metal " + id + " must not get Forgeweave-side forms");
            for (MaterialForm form : MaterialForm.ALL) {
                assertNull(ForgeweaveItems.materialForm(id, form), id + " " + form + " must not be registered");
            }
        }
    }

    @Test
    void everyMaterialFormIsRegisteredAndHasItsLangLineModelAndSprite() throws IOException {
        Path root = projectRoot();
        JsonObject lang = lang();
        List<String> problems = new ArrayList<>();
        int forms = 0;

        for (MaterialForms.FormedMaterial material : MaterialForms.ALL) {
            for (MaterialForm form : material.forms()) {
                forms++;
                String id = form.itemId(material.id());
                var item = ForgeweaveItems.materialForm(material.id(), form);
                if (item == null) {
                    problems.add(id + ": not registered in ForgeweaveItems");
                    continue;
                }
                assertEquals("forgeweave:" + id, item.getId().toString());

                String key = "item.forgeweave." + id;
                if (!lang.has(key)) {
                    problems.add(id + ": missing lang key " + key);
                } else {
                    assertEquals(form.displayName(material.displayName()), lang.get(key).getAsString(), key);
                }
                if (!Files.isRegularFile(root.resolve(MODEL_DIR).resolve(id + ".json"))) {
                    problems.add(id + ": missing item model");
                }
                if (!Files.isRegularFile(root.resolve(SPRITE_DIR).resolve(id + ".png"))) {
                    problems.add(id + ": missing sprite (run scripts/generate_material_forms.py)");
                }
            }
        }

        assertEquals(374, forms, "46 materials with an ingot x 8 forms, plus two gem materials x 3 dusts");
        assertTrue(problems.isEmpty(), "material forms missing their wiring:\n" + String.join("\n", problems));
    }

    @Test
    void everyMaterialFormIsInItsConventionTagAndTheFamilyParentNamesIt() throws IOException {
        List<String> problems = new ArrayList<>();
        for (MaterialForm form : MaterialForm.ALL) {
            Set<String> parent = tagValues(ITEM_TAG_DIR + "/" + form.tagFamily() + ".json");
            for (MaterialForms.FormedMaterial material : MaterialForms.ALL) {
                if (!material.forms().contains(form)) {
                    continue;
                }
                String leafPath = form.tagPath(material.id());
                Set<String> leaf = tagValues(ITEM_TAG_DIR + "/" + leafPath + ".json");
                String id = "forgeweave:" + form.itemId(material.id());
                if (!leaf.contains(id)) {
                    problems.add("c:" + leafPath + " does not name " + id + ": " + leaf);
                }
                if (!parent.contains("#c:" + leafPath)) {
                    problems.add("c:" + form.tagFamily() + " does not name #c:" + leafPath);
                }
            }
        }
        assertTrue(problems.isEmpty(), "convention tag gaps:\n" + String.join("\n", problems));
    }

    @Test
    void dustMeltingIsTagKeyedAtTheLadderAmountsAndThePlateFamilyNeverMelts() throws IOException {
        Path meltingDir = projectRoot().resolve(MELTING_DIR);
        List<String> problems = new ArrayList<>();

        for (MaterialForms.FormedMaterial material : MaterialForms.ALL) {
            for (MaterialForm form : material.forms()) {
                String id = form.itemId(material.id());
                Path row = meltingDir.resolve(id + ".json");
                if (form.meltAmount() == 0) {
                    // D-M8-6: the plate family is output, not currency -- nothing spends one.
                    if (Files.exists(row)) {
                        problems.add(id + ": has a melting row, but the plate family must not melt");
                    }
                    continue;
                }
                if (!Files.isRegularFile(row)) {
                    problems.add(id + ": missing melting row (run scripts/generate_material_forms.py)");
                    continue;
                }
                JsonObject json = JsonParser.parseString(
                        Files.readString(row, StandardCharsets.UTF_8)).getAsJsonObject();
                String tag = json.getAsJsonObject("input").get("tag").getAsString();
                assertEquals("c:" + form.tagPath(material.id()), tag, id + " melts off the tag, not the item");
                assertEquals("forgeweave:molten_" + material.id(), json.get("fluid").getAsString(), id);
                assertEquals(form.meltAmount(), json.get("amount").getAsInt(), id);
            }
        }
        assertTrue(problems.isEmpty(), "dust melting gaps:\n" + String.join("\n", problems));
    }

    @Test
    void theFluidTagProviderEmitsAnEntryForEveryMoltenForgeweaveFluid() throws IOException {
        Path root = projectRoot();
        List<String> missing = new ArrayList<>();
        for (ForgeweaveFluids.MoltenMetal fluid : ForgeweaveFluids.all()) {
            Path tag = root.resolve(FLUID_TAG_DIR).resolve(fluid.name() + ".json");
            if (!Files.isRegularFile(tag)) {
                missing.add("c:" + fluid.name());
                continue;
            }
            Set<String> values = tagValues(FLUID_TAG_DIR + "/" + fluid.name() + ".json");
            assertTrue(values.contains("forgeweave:" + fluid.name()), "c:" + fluid.name() + " -> " + values);
            assertTrue(values.contains("forgeweave:flowing_" + fluid.name()), "c:" + fluid.name() + " -> " + values);
        }
        assertTrue(ForgeweaveFluids.all().size() > 100,
                "non-vacuity: expected the real fluid roster, saw " + ForgeweaveFluids.all().size());
        assertTrue(missing.isEmpty(), "fluids with no c: convention tag:\n" + String.join("\n", missing));
    }

    private static MaterialForms.FormedMaterial material(String id) {
        return MaterialForms.ALL.stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no material " + id + " in the roster"));
    }
}
