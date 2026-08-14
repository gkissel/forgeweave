package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Guards issue #342: both chests shipped as full cubes on vanilla's {@code minecraft:block/orientable}
 * parent, where upstream 1.12 gives them a cabinet -- a 14x12x14 body on four legs under a full-width
 * top slab, with two drawer fronts standing proud of the front face
 * ({@code resources/assets/tconstruct/models/block/patternchest.json}, NOTICE.md). That geometry is
 * pure model JSON upstream (its {@code blockstates/tooltables.json} points both chest variants at the
 * one model and overrides only textures; there is no block-entity renderer involved), so it ports as
 * {@code models/block/chest.json} plus per-block texture overrides from
 * {@link ForgeweaveBlockStateProvider#chestBlock}.
 *
 * <p>Plain filesystem scan of the shipped/generated JSON, same approach as
 * {@link TextureReferenceAuditTest} -- a revert to {@code orientable} fails here rather than at a
 * playtest.
 */
class ChestModelTest {

    private static final List<String> CHESTS = List.of("part_chest", "pattern_chest");

    /** The five slots {@code chest.json} leaves unbound for each chest to fill. */
    private static final List<String> TEXTURE_SLOTS = List.of("side", "front", "top", "drawer_front", "drawer_side");

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

    @Test
    void bothChestsInheritTheSharedCabinetGeometryRatherThanACube() throws IOException {
        Path generated = projectRoot().resolve("src/generated/resources/assets/forgeweave/models/block");
        for (String chest : CHESTS) {
            JsonObject model = readJson(generated.resolve(chest + ".json"));
            String parent = model.get("parent").getAsString();

            assertNotEquals("minecraft:block/orientable", parent,
                    chest + " is back on the vanilla cube model; upstream 1.12's shape is a cabinet (issue #342)");
            assertEquals("forgeweave:block/chest", parent,
                    chest + " must inherit the shared chest geometry");

            JsonObject textures = model.getAsJsonObject("textures");
            for (String slot : TEXTURE_SLOTS) {
                assertTrue(textures.has(slot), chest + " leaves the \"" + slot + "\" texture slot of block/chest unbound");
                assertTrue(Files.isRegularFile(projectRoot().resolve("src/main/resources/assets/forgeweave/textures")
                                .resolve(textures.get(slot).getAsString().replace("forgeweave:", "") + ".png")),
                        chest + " texture slot \"" + slot + "\" points at a file that does not exist");
            }
        }
    }

    @Test
    void theSharedGeometryIsUpstreamsCabinetBoxes() throws IOException {
        JsonObject chest = readJson(projectRoot()
                .resolve("src/main/resources/assets/forgeweave/models/block/chest.json"));
        JsonArray elements = chest.getAsJsonArray("elements");

        // Upstream's own element list: body, top slab, 4 legs, and 10 drawer boxes.
        assertEquals(16, elements.size(), "block/chest.json must carry upstream patternchest.json's whole element list");

        JsonObject body = elements.get(0).getAsJsonObject();
        assertEquals(List.of(1.0, 3.0, 1.0), corner(body, "from"), "body inset (upstream patternchest.json \"Body\")");
        assertEquals(List.of(15.0, 15.0, 15.0), corner(body, "to"), "body inset (upstream patternchest.json \"Body\")");

        JsonObject top = elements.get(1).getAsJsonObject();
        assertEquals(List.of(0.0, 15.0, 0.0), corner(top, "from"), "top slab (upstream patternchest.json \"Top\")");
        assertEquals(List.of(16.0, 16.0, 16.0), corner(top, "to"), "top slab (upstream patternchest.json \"Top\")");

        // A single 0..16 cube element would mean the cabinet silhouette is gone again.
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            boolean fullCube = corner(box, "from").equals(List.of(0.0, 0.0, 0.0))
                    && corner(box, "to").equals(List.of(16.0, 16.0, 16.0));
            assertTrue(!fullCube, "block/chest.json must not contain a full-block element (issue #342)");
        }
    }

    @Test
    void bothChestItemModelsShowTheChestShape() throws IOException {
        Path generated = projectRoot().resolve("src/generated/resources/assets/forgeweave/models/item");
        for (String chest : CHESTS) {
            assertEquals("forgeweave:block/" + chest, readJson(generated.resolve(chest + ".json")).get("parent").getAsString(),
                    "the in-inventory " + chest + " must render the block's cabinet model, not a flat icon");
        }
    }

    private static List<Double> corner(JsonObject element, String key) {
        JsonArray array = element.getAsJsonArray(key);
        return List.of(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }
}
