package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.data.sprite.GreyToColorMapping;
import dev.gkissel.forgeweave.data.sprite.MaterialPartSprites;

/**
 * Guards issue #280's greyscale texture pipeline: {@code MaterialPartTextureProvider} recolors the
 * greyscale base part sprites through per-material {@link GreyToColorMapping} palettes during
 * datagen and its output is committed under {@code src/generated/resources}. This test replays the
 * proof slice -- same bases, same palettes, same recolor code -- and pixel-compares against the
 * committed PNGs, so the pipeline is proven deterministic and any drift (an edited base sprite, a
 * tweaked palette, a behavior change in the recolor) fails the build until {@code runData} is
 * rerun and the diff is deliberate.
 *
 * <p>Reads the real PNGs with {@link ImageIO}, same approach as {@link
 * dev.gkissel.forgeweave.client.PatternImprintCenteringTest}.
 */
class GreyscalePartTextureTest {

    private static final String BASE_DIR = "src/main/resources/assets/forgeweave/textures/derived/item/";
    private static final String GENERATED_DIR = "src/generated/resources/assets/forgeweave/textures/";

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static BufferedImage image(String relativePath) throws IOException {
        Path png = projectRoot().resolve(relativePath);
        assertTrue(Files.exists(png), png + " is missing -- run ./gradlew runData and commit the output");
        BufferedImage img = ImageIO.read(png.toFile());
        assertNotNull(img, png + " could not be decoded");
        return img;
    }

    @Test
    void committedStagingSpritesMatchPipelineOutput() throws IOException {
        for (String part : MaterialPartSprites.PARTS) {
            BufferedImage base = image(BASE_DIR + part + ".png");
            for (Map.Entry<String, GreyToColorMapping> palette : MaterialPartSprites.PALETTES.entrySet()) {
                BufferedImage expected = palette.getValue().recolor(base);
                String outputPath = GENERATED_DIR + MaterialPartSprites.outputName(part, palette.getKey()) + ".png";
                BufferedImage committed = image(outputPath);
                assertPixelsEqual(expected, committed, outputPath);
            }
        }
    }

    private static void assertPixelsEqual(BufferedImage expected, BufferedImage committed, String what) {
        assertEquals(expected.getWidth(), committed.getWidth(), what + ": width");
        assertEquals(expected.getHeight(), committed.getHeight(), what + ": height");
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), committed.getRGB(x, y),
                        what + ": pixel (" + x + ", " + y + ") differs from the pipeline's output -- "
                                + "rerun ./gradlew runData if the base sprite or palette changed on purpose");
            }
        }
    }

    /** Pins the port's palette semantics against upstream's algorithm, independent of any file. */
    @Test
    void paletteInterpolationMatchesUpstreamSemantics() {
        GreyToColorMapping palette = GreyToColorMapping.builderFromBlack()
                .addARGB(128, 0xFF204060)
                .addARGB(255, 0xFF80C0FF)
                .build();
        // Fully transparent pixels stay fully transparent, whatever their RGB.
        assertEquals(0x00000000, palette.mapColor(0x00FFFFFF));
        // Exact stops map to the stop color.
        assertEquals(0xFF000000, palette.mapColor(0xFF000000));
        assertEquals(0xFF204060, palette.mapColor(0xFF808080));
        assertEquals(0xFF80C0FF, palette.mapColor(0xFFFFFFFF));
        // Between stops each channel interpolates with upstream's truncating integer math:
        // grey 64 between (0, black) and (128, 0xFF204060) is exactly half of each channel.
        assertEquals(0xFF102030, palette.mapColor(0xFF404040));
        // A non-grey pixel maps by its largest channel, then scales lesser channels back down:
        // grey = 0x80 -> 0xFF204060; red channel 0x40 < 0x80 scales 0x20 down to 0x10.
        assertEquals(0xFF104060, palette.mapColor(0xFF408080));
        // Partial alpha multiplies through.
        assertEquals(0x7F204060, palette.mapColor(0x7F808080));
    }
}
