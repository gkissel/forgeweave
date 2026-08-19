package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * Guards issue #628: upstream 1.12 ships DEDICATED hand-drawn art for the large plate's pattern
 * and cast -- {@code pattern_large_plate.png} and {@code cast_large_plate.png} -- and both carry a
 * creeper face. {@code scripts/generate_pattern_textures.py} already special-cased the pattern (a
 * byte-for-byte copy, issue #337), but {@code scripts/generate_cast_textures.py} still ran the
 * large plate part through the generic composite algorithm (hole punched in the plain plate's
 * silhouette), clobbering the face. This pins a handful of pixels from the creeper face's eyes and
 * the plate's bevel so a future regeneration that reverts to compositing fails the build instead of
 * shipping a faceless cast silhouette unnoticed.
 *
 * <p>Reads the real PNGs with {@link ImageIO}, same approach as {@link
 * dev.gkissel.forgeweave.client.PatternImprintCenteringTest}.
 */
class LargePlateCreeperFaceTest {

    private static final String TEXTURE_DIR = "src/main/resources/assets/forgeweave/textures/derived/item/";

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
        assertTrue(Files.exists(png), png + " is missing");
        BufferedImage img = ImageIO.read(png.toFile());
        assertNotNull(img, png + " could not be decoded");
        return img;
    }

    private static void assertPixel(BufferedImage img, int x, int y, int expectedArgb, String what) {
        int actual = img.getRGB(x, y);
        assertEquals(expectedArgb, actual,
                what + ": pixel (" + x + ", " + y + ") was " + Integer.toHexString(actual) + ", expected "
                        + Integer.toHexString(expectedArgb) + " -- rerun the art scripts and commit their output "
                        + "(issue #628)");
    }

    @Test
    void patternLargePlateKeepsTheDedicatedCreeperFaceArt() throws IOException {
        BufferedImage pattern = image(TEXTURE_DIR + "pattern_large_plate.png");
        // (1, 1): a border pixel of the dedicated hand-drawn pattern art (0x73, 0x5e, 0x39, opaque).
        assertPixel(pattern, 1, 1, 0xFF73_5e39, "pattern_large_plate.png");
        // (13, 14): the darkest pixel of the creeper mouth (0x69, 0x54, 0x33, opaque).
        assertPixel(pattern, 13, 14, 0xFF69_5433, "pattern_large_plate.png");
    }

    @Test
    void castLargePlateKeepsTheDedicatedCreeperFaceArt() throws IOException {
        BufferedImage cast = image(TEXTURE_DIR + "cast_large_plate.png");
        // (2, 2): a bevel pixel of the dedicated cast art -- opaque gold, not punched through by the
        // full plate silhouette a composited cast would leave here.
        assertPixel(cast, 2, 2, 0xFFEB_CC58, "cast_large_plate.png");
        // (5, 5): inside the creeper's left eye -- fully transparent in the dedicated mold cavity.
        assertPixel(cast, 5, 5, 0x0000_0000, "cast_large_plate.png");
    }
}
