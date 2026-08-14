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
 * Guards issue #284: a Broken tool rendered identically to an intact one. Upstream 1.12 swaps the
 * broken art for exactly one layer of the tool -- its {@code models/item/tools/*.tcon.json} declare a
 * {@code broken<N>} texture beside their {@code layer<N>} keys and {@code BakedToolModel#
 * getOverrides} substitutes it on {@code ToolHelper#isBroken} -- so every Forgeweave tool model must
 * carry a {@code forgeweave:broken} override pointing at a sibling model that differs from it in
 * exactly that one layer.
 *
 * <p>Plain filesystem scan of the generated JSON walked off {@code ToolAssemblyRecipes.ENTRIES},
 * same approach as {@link TextureReferenceAuditTest} and {@link ChestModelTest}: a tool added later
 * without broken art fails here rather than at a playtest.
 */
class BrokenToolModelTest {

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

    private static JsonObject readJson(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "expected model JSON at " + path);
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Every tool has a declared broken layer, and it is one of the layers that tool actually draws. */
    @Test
    void everyToolDeclaresABrokenLayerItActuallyDraws() {
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            String tool = entry.constants().id();
            String broken = ToolArt.brokenLayer(tool);
            assertNotNull(broken, "forgeweave:" + tool + " declares no broken layer (issue #284)");
            assertTrue(ToolArt.layers(entry.constants().parts()).contains(broken),
                    "forgeweave:" + tool + " breaks layer '" + broken + "', which it does not draw");
        }
    }

    /** The broken art exists on disk under the derived-tools atlas source, like every other layer. */
    @Test
    void everyBrokenLayerHasArt() {
        Path textures = projectRoot().resolve("src/main/resources/assets/forgeweave/textures");
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            String tool = entry.constants().id();
            Path png = textures.resolve(ToolArt.brokenLayerTexture(tool, ToolArt.brokenLayer(tool)) + ".png");
            assertTrue(Files.isRegularFile(png),
                    "broken layer art for forgeweave:" + tool + " is missing: " + png
                            + " (run scripts/derive_broken_art.py)");
        }
    }

    /**
     * The regression itself: each tool's model must branch on the Broken component, and the model it
     * branches to must be the same layer stack with only the broken layer's texture swapped -- which
     * is what keeps {@code ForgeweaveItemColors#toolMaterialTint}'s tintIndex-to-part mapping valid
     * across the swap.
     */
    @Test
    void everyToolModelSwapsExactlyItsBrokenLayerWhenBroken() throws IOException {
        Path models = projectRoot().resolve("src/generated/resources/assets/forgeweave/models/item");

        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            String tool = entry.constants().id();
            List<String> layers = ToolArt.layers(entry.constants().parts());
            String brokenLayer = ToolArt.brokenLayer(tool);

            JsonObject intact = readJson(models.resolve(tool + ".json"));
            JsonArray overrides = intact.getAsJsonArray("overrides");
            assertNotNull(overrides, tool + " has no overrides block, so a Broken tool still renders intact"
                    + " (issue #284)");

            String brokenModel = null;
            for (JsonElement element : overrides) {
                JsonObject override = element.getAsJsonObject();
                JsonObject predicate = override.getAsJsonObject("predicate");
                if (predicate != null && predicate.has(BROKEN_PREDICATE)
                        && predicate.get(BROKEN_PREDICATE).getAsInt() == 1) {
                    brokenModel = override.get("model").getAsString();
                }
            }
            assertEquals("forgeweave:item/" + tool + "_broken", brokenModel,
                    tool + " must override on \"" + BROKEN_PREDICATE + "\": 1 (issue #284)");

            JsonObject broken = readJson(models.resolve(tool + "_broken.json"));
            assertEquals(intact.get("parent"), broken.get("parent"),
                    tool + "_broken must inherit the same held-render transforms as the intact model");
            assertEquals(intact.get("display"), broken.get("display"),
                    tool + "_broken must keep the intact model's display overrides");

            // Layer for layer: identical everywhere except the one broken layer.
            List<String> expected = new ArrayList<>(layers.size());
            for (String layer : layers) {
                expected.add("forgeweave:" + (layer.equals(brokenLayer)
                        ? ToolArt.brokenLayerTexture(tool, layer)
                        : ToolArt.layer(tool, layer)));
            }
            List<String> actual = new ArrayList<>(layers.size());
            JsonObject textures = broken.getAsJsonObject("textures");
            for (int layer = 0; layer < layers.size(); layer++) {
                JsonElement texture = textures.get("layer" + layer);
                assertNotNull(texture, tool + "_broken is missing layer" + layer);
                actual.add(texture.getAsString());
            }
            assertEquals(expected, actual, tool + "_broken must swap only its '" + brokenLayer + "' layer");
        }
    }
}
