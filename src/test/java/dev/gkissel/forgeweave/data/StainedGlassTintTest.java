package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Guards issue #951, both halves of it.
 *
 * <p>The tint half: all 16 clear stained glass colors rendered as the bare greyscale sprite. The
 * block and item color handlers {@code ForgeweaveGlassColors} registers only ever run for a face
 * that declares a {@code tintindex}, and the generated models sat on vanilla's
 * {@code minecraft:block/cube_all}, whose faces declare none. So the handlers were registered,
 * correct, and never consulted. The fix is the parent every 1.12 connected-texture block model used
 * upstream: Mantle's {@code block/tinted_cube}, ported here as {@code models/block/tinted_cube.json}
 * (NOTICE.md), a plain full cube plus {@code tintindex: 0} on all six faces.
 *
 * <p>The connected-texture half: both glasses shipped only upstream's {@code normal} frame, the
 * isolated tile, on every face, so a wall of glass showed a border grid instead of one joined pane.
 * All eleven frames now ship per glass, bound by the nine models Mantle's {@code connected_*} files
 * define, one blockstate variant per combination of six neighbour flags.
 *
 * <p>Plain filesystem scan of the shipped and generated JSON, the same approach
 * {@link ChestModelTest} and {@link TextureReferenceAuditTest} take: a revert fails here rather than
 * at a playtest.
 */
class StainedGlassTintTest {

    private static final List<String> FACES = List.of("down", "up", "north", "south", "west", "east");

    /** Every dye color's block id, in {@code DyeColor} declaration order. */
    private static final List<String> COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    /** Mantle's nine connected models, by the suffix their file names carry after {@code connected_}. */
    private static final List<String> MODEL_SUFFIXES = List.of(
            "0", "1", "2", "2_angle", "3_t1", "3_t2", "3_angle", "4_angle", "4_cross");

    /** Upstream's eleven connected-texture frames per glass. */
    private static final List<String> FRAMES = List.of(
            "normal", "u", "r", "ud", "ul", "ur", "lr", "udr", "udl", "ulr", "udlr");

    /** Each glass's texture set and the render layer upstream draws it on. */
    private static final Map<String, String> GLASS_SETS = Map.of(
            "clear_glass", "minecraft:cutout",
            "clear_stained_glass", "minecraft:translucent");

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
    void bothGlassesShipAllElevenFramesOnNineTintedModels() throws IOException {
        Path root = projectRoot();
        Path sprites = root.resolve("src/main/resources/assets/forgeweave/textures/derived/block/connected");
        Path models = root.resolve("src/generated/resources/assets/forgeweave/models/block/connected");

        for (Map.Entry<String, String> glass : GLASS_SETS.entrySet()) {
            String set = glass.getKey();
            for (String frame : FRAMES) {
                assertTrue(Files.isRegularFile(sprites.resolve(set).resolve(frame + ".png")),
                        set + " is missing upstream's \"" + frame + "\" connected-texture frame; a glass "
                                + "drawn from \"normal\" alone shows a border grid, not a joined pane");
            }

            for (String suffix : MODEL_SUFFIXES) {
                JsonObject model = readJson(models.resolve(set).resolve(suffix + ".json"));
                assertNotEquals("minecraft:block/cube_all", model.get("parent").getAsString(),
                        set + "/" + suffix + " is back on vanilla's untinted cube (issue #951)");
                assertEquals("forgeweave:block/tinted_cube", model.get("parent").getAsString(),
                        set + "/" + suffix + " must inherit the tinted cube parent");
                assertEquals(glass.getValue(), model.get("render_type").getAsString(),
                        set + "/" + suffix + " must keep upstream's render layer");

                JsonObject textures = model.getAsJsonObject("textures");
                for (String face : FACES) {
                    String texture = textures.get(face).getAsString();
                    assertTrue(texture.startsWith("forgeweave:derived/block/connected/" + set + "/"),
                            set + "/" + suffix + "'s " + face + " face must bind one of that glass's own "
                                    + "connected frames, got " + texture);
                }
            }

            // The isolated frame on all six faces is what an item in a hand or a slot shows, and it is
            // what upstream's own blockstate gives its "inventory" variant.
            JsonObject isolated = readJson(models.resolve(set).resolve("0.json")).getAsJsonObject("textures");
            for (String face : FACES) {
                assertEquals("forgeweave:derived/block/connected/" + set + "/normal",
                        isolated.get(face).getAsString(),
                        set + "/0 is the no-neighbours model; every face must be the isolated frame");
            }
        }
    }

    @Test
    void everyStainedColorDrawsFromTheSharedConnectedModelSet() throws IOException {
        Path root = projectRoot();
        Path blockstates = root.resolve("src/generated/resources/assets/forgeweave/blockstates");
        Path items = root.resolve("src/generated/resources/assets/forgeweave/models/item");

        for (String color : COLORS) {
            String name = color + "_stained_clear_glass";
            JsonObject variants = readJson(blockstates.resolve(name + ".json")).getAsJsonObject("variants");

            // Six boolean neighbour flags, so 64 combinations, and upstream's own blockstate lists a
            // model for every one of them.
            assertEquals(64, variants.size(),
                    name + " must map all 64 combinations of its six connected_* flags");
            for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
                String model = variant.getValue().getAsJsonObject().get("model").getAsString();
                assertTrue(model.startsWith("forgeweave:block/connected/clear_stained_glass/"),
                        name + " variant \"" + variant.getKey() + "\" points at " + model
                                + " rather than the shared connected model set");
            }

            // Item models inherit a connected model, so the tint index reaches the inventory sprite and
            // the hand render by the same route the block does.
            assertEquals("forgeweave:block/connected/clear_stained_glass/0",
                    readJson(items.resolve(name + ".json")).get("parent").getAsString(),
                    name + "'s item model must be the isolated frame");
        }
    }
}
