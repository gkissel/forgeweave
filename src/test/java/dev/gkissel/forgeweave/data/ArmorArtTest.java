package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Guards {@code scripts/derive_armor_art.py} (issue #679, M4-4; SCOPE.md D18): the per-material worn
 * armor layers it generates are the clone's grayscale base times the material's datapack colour,
 * and every material that can be a plating or a maille has its two layers on disk -- including the
 * three the clone ships no PNG for (ardite, netherite, nahuatl), which is the point of generating.
 *
 * <p>Reads the real PNGs with {@link ImageIO}, like {@link LargePlateCreeperFaceTest}.
 */
class ArmorArtTest {

    private static final String LAYERS = "src/main/resources/assets/forgeweave/textures/models/armor/derived/";
    private static final String MATERIALS = "src/main/resources/data/forgeweave/forgeweave/material/";

    private static Path projectRoot() {
        for (Path candidate = Path.of("").toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found)");
    }

    private static BufferedImage image(String relativePath) throws IOException {
        Path png = projectRoot().resolve(relativePath);
        assertTrue(Files.exists(png), png + " is missing -- run scripts/derive_armor_art.py");
        BufferedImage img = ImageIO.read(png.toFile());
        assertNotNull(img, png + " could not be decoded");
        return img;
    }

    private static JsonObject material(String id) throws IOException {
        return JsonParser.parseString(
                Files.readString(projectRoot().resolve(MATERIALS + id + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** The script's {@code tint_image()} ({@code ImageChops.multiply}): each channel of the gray base times the colour's, floored; alpha untouched. */
    private static int tint(int baseArgb, int color) {
        int r = ((baseArgb >> 16) & 0xFF) * ((color >> 16) & 0xFF) / 255;
        int g = ((baseArgb >> 8) & 0xFF) * ((color >> 8) & 0xFF) / 255;
        int b = (baseArgb & 0xFF) * (color & 0xFF) / 255;
        return (baseArgb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    @Test
    void ironPlatingLayerIsTheGrayBaseTimesIronsColor() throws IOException {
        BufferedImage base = image(LAYERS + "plating_layer_1.png");
        BufferedImage iron = image(LAYERS + "plating_iron_layer_1.png");
        int color = Integer.parseInt(material("iron").get("color").getAsString().substring(1), 16);
        assertEquals(base.getWidth(), iron.getWidth());
        assertEquals(base.getHeight(), iron.getHeight());
        // The chest front, the brightest run of the plating; a fixture pixel that is opaque and non-black.
        int x = 20;
        int y = 22;
        int basePixel = base.getRGB(x, y);
        assertTrue((basePixel >>> 24) == 0xFF && (basePixel & 0xFFFFFF) != 0, "fixture pixel must be an opaque gray");
        assertEquals(Integer.toHexString(tint(basePixel, color)), Integer.toHexString(iron.getRGB(x, y)),
                "plating_iron_layer_1.png (" + x + ", " + y + ") is not base * #CACACA -- rerun scripts/derive_armor_art.py");
        // And a transparent one stays transparent.
        assertEquals(0, iron.getRGB(0, 0) >>> 24);
    }

    @Test
    void everyPlatingOrMailleMaterialHasBothWornLayers() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.list(projectRoot().resolve(MATERIALS))) {
            for (Path json : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = json.getFileName().toString().replace(".json", "");
                JsonObject data = material(id);
                boolean plating = data.has("plating");
                boolean maille = data.has("maille") && data.get("maille").getAsBoolean();
                for (int layer = 1; layer <= 2; layer++) {
                    if (plating && !Files.exists(projectRoot().resolve(LAYERS + "plating_" + id + "_layer_" + layer + ".png"))) {
                        missing.add("plating_" + id + "_layer_" + layer);
                    }
                    if (maille && !Files.exists(projectRoot().resolve(LAYERS + "maille_" + id + "_layer_" + layer + ".png"))) {
                        missing.add("maille_" + id + "_layer_" + layer);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "worn armor layers not generated (scripts/derive_armor_art.py): " + missing);
    }
}
