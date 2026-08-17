package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolArt;

/**
 * M3.5 issue #400, the datagen half: a bow's generated item model must branch on the draw the same
 * way upstream 1.12's {@code models/item/tools/<bow>.tcon.json} does -- three
 * {@code {"pulling": 1, "pull": <threshold>}} overrides in ascending order plus, for the crossbow, a
 * fourth on {@code {"loaded": 1}} -- and each branch's model must be the bow's own layer stack with
 * only the staged textures swapped.
 *
 * <p>Shaped after {@code BrokenToolModelTest}, whose regression this is the sibling of: both are
 * "the model does not actually branch, so the state renders as the default one", and both turn on
 * the swapped model keeping the layer count and order identical so
 * {@code ForgeweaveItemColors#toolMaterialTint}'s tintIndex-to-part mapping survives the swap.
 */
class BowDrawModelTest {

    private static final String PULLING_PREDICATE = "minecraft:pulling";
    private static final String PULL_PREDICATE = "minecraft:pull";
    private static final String LOADED_PREDICATE = "forgeweave:loaded";
    private static final String BROKEN_PREDICATE = "forgeweave:broken";

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static Path models() {
        return projectRoot().resolve("src/generated/resources/assets/forgeweave/models/item");
    }

    private static JsonObject readJson(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "expected model JSON at " + path);
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** The bows, walked off the station's own list so a fourth one cannot be left out here. */
    private static List<ToolAssemblyRecipes.Entry> bows() {
        List<ToolAssemblyRecipes.Entry> bows = ToolAssemblyRecipes.ENTRIES.stream()
                .filter(entry -> ToolArt.drawThresholds(entry.constants().id()) != null)
                .toList();
        assertEquals(3, bows.size(), "M3.5 ships three bows; " + bows.size() + " declare draw stages");
        return bows;
    }

    /** Every draw-stage texture the models will name has to exist under the derived-tools atlas source. */
    @Test
    void everyDrawStageLayerHasArt() {
        Path textures = projectRoot().resolve("src/main/resources/assets/forgeweave/textures");
        List<String> missing = new ArrayList<>();
        for (ToolAssemblyRecipes.Entry entry : bows()) {
            String tool = entry.constants().id();
            for (String layer : ToolArt.layers(entry.constants().parts())) {
                for (int stage = 1; stage <= ToolArt.DRAW_STAGES; stage++) {
                    Path png = textures.resolve(ToolArt.drawLayer(tool, layer, stage) + ".png");
                    if (!Files.isRegularFile(png)) {
                        missing.add(png.toString());
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "draw-stage art missing (run scripts/derive_bow_draw_art.py):\n"
                + String.join("\n", missing));
    }

    /**
     * The regression: without these overrides a drawn bow renders exactly like an undrawn one, which
     * is what the shortbow and both Tool Forge bows shipped as through #394/#395.
     */
    @Test
    void everyBowModelBranchesOnItsThreePullStages() throws IOException {
        for (ToolAssemblyRecipes.Entry entry : bows()) {
            String tool = entry.constants().id();
            float[] thresholds = ToolArt.drawThresholds(tool);
            JsonArray overrides = readJson(models().resolve(tool + ".json")).getAsJsonArray("overrides");
            assertNotNull(overrides, tool + " has no overrides block, so a drawn bow still renders undrawn");

            for (int stage = 1; stage <= ToolArt.DRAW_STAGES; stage++) {
                JsonObject predicate = predicateOf(overrides, "forgeweave:item/" + tool + "_pulling_" + stage);
                assertNotNull(predicate, tool + " has no override pointing at its stage-" + stage + " model");
                assertEquals(1.0f, predicate.get(PULLING_PREDICATE).getAsFloat(), 0.0f,
                        tool + " stage " + stage + " must only apply while the bow is being drawn");
                assertEquals(thresholds[stage - 1], predicate.get(PULL_PREDICATE).getAsFloat(), 0.0f,
                        tool + " stage " + stage + " must test upstream's own pull threshold");
            }
        }
    }

    /** Ascending thresholds, and Broken last -- vanilla resolves overrides by "last one that matches". */
    @Test
    void theBrokenOverrideComesAfterEveryDrawStage() throws IOException {
        for (ToolAssemblyRecipes.Entry entry : bows()) {
            String tool = entry.constants().id();
            JsonArray overrides = readJson(models().resolve(tool + ".json")).getAsJsonArray("overrides");

            int lastDraw = -1;
            int broken = -1;
            float previousPull = -1.0f;
            for (int i = 0; i < overrides.size(); i++) {
                JsonObject predicate = overrides.get(i).getAsJsonObject().getAsJsonObject("predicate");
                if (predicate.has(PULL_PREDICATE)) {
                    float pull = predicate.get(PULL_PREDICATE).getAsFloat();
                    assertTrue(pull > previousPull,
                            tool + "'s pull overrides must ascend; " + pull + " follows " + previousPull);
                    previousPull = pull;
                    lastDraw = i;
                } else if (predicate.has(LOADED_PREDICATE)) {
                    lastDraw = i;
                } else if (predicate.has(BROKEN_PREDICATE)) {
                    broken = i;
                }
            }
            assertTrue(broken > lastDraw,
                    tool + "'s forgeweave:broken override must come last, or a Broken bow can render intact");
        }
    }

    /** Only the crossbow has a loaded state, and it draws the full-draw art under its own pose. */
    @Test
    void onlyTheCrossbowBranchesOnLoaded() throws IOException {
        for (ToolAssemblyRecipes.Entry entry : bows()) {
            String tool = entry.constants().id();
            JsonArray overrides = readJson(models().resolve(tool + ".json")).getAsJsonArray("overrides");
            JsonObject predicate = predicateOf(overrides, "forgeweave:item/" + tool + "_loaded");
            if (!ToolArt.hasLoadedState(tool)) {
                assertEquals(null, predicate, tool + " has no loaded state and must not branch on one");
                continue;
            }
            assertNotNull(predicate, tool + " must branch on forgeweave:loaded");
            assertEquals(1.0f, predicate.get(LOADED_PREDICATE).getAsFloat(), 0.0f);

            JsonObject loaded = readJson(models().resolve(tool + "_loaded.json"));
            JsonObject fullDraw = readJson(models().resolve(tool + "_pulling_" + ToolArt.DRAW_STAGES + ".json"));
            assertEquals(fullDraw.get("textures"), loaded.get("textures"),
                    tool + "_loaded draws the stage-" + ToolArt.DRAW_STAGES + " art, upstream's own choice");
            assertTrue(loaded.has("display"), tool + "_loaded carries its own first-person pose");
        }
    }

    /**
     * Each stage model is the bow's own layer stack, in the same order, with exactly the layers
     * upstream re-points swapped -- the string from stage 1, the limbs from stage 2, nothing else
     * ever.
     */
    @Test
    void everyStageModelSwapsExactlyTheLayersUpstreamDoes() throws IOException {
        for (ToolAssemblyRecipes.Entry entry : bows()) {
            String tool = entry.constants().id();
            List<String> layers = ToolArt.layers(entry.constants().parts());
            JsonObject intact = readJson(models().resolve(tool + ".json"));

            for (int stage = 1; stage <= ToolArt.DRAW_STAGES; stage++) {
                JsonObject model = readJson(models().resolve(tool + "_pulling_" + stage + ".json"));
                assertEquals(intact.get("parent"), model.get("parent"),
                        tool + " stage " + stage + " must inherit the same held-render transforms");

                List<String> expected = new ArrayList<>(layers.size());
                List<String> actual = new ArrayList<>(layers.size());
                JsonObject textures = model.getAsJsonObject("textures");
                for (int layer = 0; layer < layers.size(); layer++) {
                    expected.add("forgeweave:" + ToolArt.drawLayer(tool, layers.get(layer), stage));
                    JsonElement texture = textures.get("layer" + layer);
                    assertNotNull(texture, tool + " stage " + stage + " is missing layer" + layer);
                    actual.add(texture.getAsString());
                }
                assertEquals(expected, actual, tool + " stage " + stage + " draws the wrong layers");
                assertEquals(layers.size(), textures.size(),
                        tool + " stage " + stage + " must have exactly the tool's own layer count");
            }
        }
    }

    /** @return the {@code predicate} of the override pointing at {@code model}, or null if there is none. */
    private static JsonObject predicateOf(JsonArray overrides, String model) {
        for (JsonElement element : overrides) {
            JsonObject override = element.getAsJsonObject();
            if (model.equals(override.get("model").getAsString())) {
                return override.getAsJsonObject("predicate");
            }
        }
        return null;
    }
}
