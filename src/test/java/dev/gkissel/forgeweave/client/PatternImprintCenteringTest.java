package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * Guards issue #337: {@code scripts/generate_pattern_textures.py} composites each part's
 * silhouette onto the blank pattern texture, and without a per-part offset a part drawn
 * off-center in its own 16x16 canvas (kama_head, hammer_head, pickaxe_head, shovel_head, among
 * others) produces an off-center imprint on the pattern item. The script now ports upstream
 * 1.12's per-part offsets (its {@code .tmat.json} "offset" fields) before compositing; this test
 * reads the committed {@code pattern_<part>.png} PNGs and checks the imprint -- the pixels that
 * differ from the shared blank {@code pattern.png} base -- sits centered on the 16x16 canvas
 * within a small tolerance, so a future regeneration that drops or mis-applies an offset fails
 * the build instead of shipping an off-center icon unnoticed.
 *
 * <p>Reads the real PNGs with {@link ImageIO}, same approach as {@link StationSocketAlignmentTest}.
 */
class PatternImprintCenteringTest {

    private static final String TEXTURE_DIR = "src/main/resources/assets/forgeweave/textures/derived/item/";

    /** Issue #337's four named offenders -- parts whose upstream offset is furthest from (0, 0). */
    private static final String[] OFFENDER_PARTS = {"kama_head", "hammer_head", "pickaxe_head", "shovel_head"};

    /**
     * Parts whose base art was re-sourced after #337 fixed the offsets, so their silhouettes moved
     * under a table that had already been tuned. None is one of the #337 offenders above, but each
     * is a re-derivation away from an off-center imprint, which is exactly what this guards.
     *
     * <ul>
     * <li>Issue #278: {@code knife_blade}'s art was swapped from 1.12's (mismatched) knife blade to
     * the 1.20 clone's {@code small_blade.png}, a differently-shaped silhouette; its bounding box
     * still centers at (8, 8), so its offset stays (0, 0).
     * <li>Issue #279: {@code curved_blade} became 1.12's cutlass blade and {@code katana_blade} was
     * freshly authored, both drawn in the tool position (top-right of the canvas) rather than
     * centered like the art they replaced -- so both needed a real hand-chosen offset where (0, 0)
     * had been correct before.
     * <li>Issue #393: {@code bow_limb} is upstream's {@code shortbow/limb_bottom} sprite, drawn in
     * the bow's own bottom-left corner, and carries the largest offset in the table (4, -2) --
     * exactly the #337 failure mode. {@code bow_string} is {@code parts/bowstring}, already
     * centered, so its offset stays (0, 0); it is here to keep the pair guarded together.
     * </ul>
     */
    private static final String[] CENTERED_PARTS =
            {"knife_blade", "curved_blade", "katana_blade", "bow_limb", "bow_string"};

    /**
     * Max allowed distance (in pixels) between the imprint's bounding-box center and the 16x16
     * canvas center (8.0, 8.0). Correctly offset imprints land within ~1.1px of center (rounding
     * an odd-width silhouette to the nearest integer offset); a dropped or reverted offset put
     * these parts 2.2-4.5px off, so 1.5px cleanly separates "centered" from "the #337 bug".
     */
    private static final double TOLERANCE_PX = 1.5;

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

    /**
     * The imprint is every pixel where the part's pattern PNG differs from the shared blank
     * pattern base -- both are otherwise pixel-identical outside the darkened silhouette.
     */
    private static int[] imprintBoundingBox(BufferedImage base, BufferedImage part, String what) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                if (base.getRGB(x, y) != part.getRGB(x, y)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertTrue(minX <= maxX, what + ": pattern_" + what + ".png has no imprint pixels (identical to pattern.png)");
        return new int[] {minX, minY, maxX, maxY};
    }

    private static void assertImprintCentered(BufferedImage base, String part) throws IOException {
        BufferedImage png = image(TEXTURE_DIR + "pattern_" + part + ".png");
        int[] bbox = imprintBoundingBox(base, png, part);
        double centerX = (bbox[0] + bbox[2] + 1) / 2.0;
        double centerY = (bbox[1] + bbox[3] + 1) / 2.0;
        double canvasCenter = png.getWidth() / 2.0;
        double distance = Math.hypot(centerX - canvasCenter, centerY - canvasCenter);
        assertTrue(distance <= TOLERANCE_PX,
                "pattern_" + part + ".png: imprint bbox " + java.util.Arrays.toString(bbox) + " centers at ("
                        + centerX + ", " + centerY + "), " + distance + "px from canvas center (" + canvasCenter
                        + ", " + canvasCenter + ") -- exceeds " + TOLERANCE_PX + "px tolerance");
    }

    @Test
    void offenderPartsImprintCenteredOnCanvas() throws IOException {
        BufferedImage base = image(TEXTURE_DIR + "pattern.png");
        for (String part : OFFENDER_PARTS) {
            assertImprintCentered(base, part);
        }
    }

    @Test
    void centeredPartsImprintCenteredOnCanvas() throws IOException {
        BufferedImage base = image(TEXTURE_DIR + "pattern.png");
        for (String part : CENTERED_PARTS) {
            assertImprintCentered(base, part);
        }
    }
}
