package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Guards issue #951: all 16 clear stained glass colors rendered as the bare greyscale sprite in game.
 * The block and item color handlers {@code ForgeweaveGlassColors} registers only ever run for a face
 * that declares a {@code tintindex}, and the generated models sat on vanilla's
 * {@code minecraft:block/cube_all}, whose faces declare none. So the handlers were registered,
 * correct, and never consulted.
 *
 * <p>The fix is the parent every 1.12 connected-texture block model already used upstream: Mantle's
 * {@code block/tinted_cube}, ported here as {@code models/block/tinted_cube.json} (NOTICE.md), which
 * is a plain full cube plus {@code tintindex: 0} on all six faces.
 *
 * <p>Plain filesystem scan of the shipped and generated JSON, the same approach
 * {@link ChestModelTest} and {@link TextureReferenceAuditTest} take: a revert to {@code cube_all}
 * fails here rather than at a playtest.
 */
class StainedGlassTintTest {

    private static final List<String> FACES = List.of("down", "up", "north", "south", "west", "east");

    /** Every dye color's block id, in {@code DyeColor} declaration order. */
    private static final List<String> COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

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
    void theSharedParentPutsATintIndexOnEveryFace() throws IOException {
        JsonObject model = readJson(projectRoot()
                .resolve("src/main/resources/assets/forgeweave/models/block/tinted_cube.json"));
        JsonObject faces = model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");

        for (String face : FACES) {
            assertTrue(faces.has(face), "block/tinted_cube is missing its " + face + " face");
            JsonObject definition = faces.getAsJsonObject(face);
            assertTrue(definition.has("tintindex"),
                    "block/tinted_cube's " + face + " face carries no tintindex, so no color handler can "
                            + "reach it (issue #951)");
            assertEquals(0, definition.get("tintindex").getAsInt(),
                    "block/tinted_cube's " + face + " face must use tint index 0, the index "
                            + "ForgeweaveGlassColors answers");
            assertEquals("#" + face, definition.get("texture").getAsString(),
                    "block/tinted_cube's " + face + " face must leave its texture unbound, one key per face, "
                            + "so a connected-texture model can bind a different frame to each");
        }
    }

    @Test
    void everyStainedColorsBlockAndItemModelInheritsThatParent() throws IOException {
        Path blocks = projectRoot().resolve("src/generated/resources/assets/forgeweave/models/block");
        Path items = projectRoot().resolve("src/generated/resources/assets/forgeweave/models/item");

        for (String color : COLORS) {
            String name = color + "_stained_clear_glass";
            JsonObject block = readJson(blocks.resolve(name + ".json"));

            assertNotEquals("minecraft:block/cube_all", block.get("parent").getAsString(),
                    name + " is back on vanilla's untinted cube; its color handler would never run (issue #951)");
            assertEquals("forgeweave:block/tinted_cube", block.get("parent").getAsString(),
                    name + " must inherit the tinted cube parent");
            assertEquals("minecraft:translucent", block.get("render_type").getAsString(),
                    name + " must keep upstream's translucent render layer");

            JsonObject textures = block.getAsJsonObject("textures");
            for (String face : FACES) {
                assertEquals("forgeweave:derived/block/clear_stained_glass", textures.get(face).getAsString(),
                        name + " leaves the " + face + " face of block/tinted_cube unbound");
            }

            // The item model inherits the block model, so the tint index reaches the inventory sprite
            // and the hand render by the same route.
            assertEquals("forgeweave:block/" + name,
                    readJson(items.resolve(name + ".json")).get("parent").getAsString(),
                    name + "'s item model must inherit its block model");
        }
    }
}
