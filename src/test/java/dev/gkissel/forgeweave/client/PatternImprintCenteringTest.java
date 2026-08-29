package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Guards issue #337: {@code scripts/generate_pattern_textures.py} composites each part's
 * silhouette onto the blank pattern texture, and without a per-part offset a part drawn
 * off-center in its own 16x16 canvas (kama_head, hammer_head, pickaxe_head, shovel_head, among
 * others) produces an off-center imprint on the pattern item. The script now ports upstream
 * 1.12's per-part offsets (its {@code .tmat.json} "offset" fields) before compositing; this test
 * reads the committed {@code pattern_<part>.png} PNGs and checks the imprint -- the pixels that
 * differ from the shared blank {@code pattern.png} base -- sits centered on the pattern base's own
 * opaque footprint within a small tolerance, so a future regeneration that drops or mis-applies an
 * offset, or a new pattern base with a different usable area, fails the build instead of shipping
 * an off-center icon unnoticed.
 *
 * <p>Reads the real PNGs with {@link ImageIO}, same approach as {@link StationSocketAlignmentTest}.
 *
 * <p>Issue #802: two changes from the version of this test that shipped through that regression.
 * First, the expected center used to be hardcoded as {@code canvasWidth / 2.0} -- an assumption
 * about the base's layout, not something read off the base itself; it is now the pattern base's own
 * opaque bounding-box center (see {@code scripts/sprite_sets.py}'s {@code opaque_bbox}), which
 * happens to equal the same (8, 8) today but would move with a differently-shaped future base.
 * Second, this test only ever read {@code src/main/resources/assets/forgeweave/textures/derived/item/}
 * -- the Forged/default tree -- and never the built-in Legacy resource pack (issue #796), so a
 * Legacy-only regression (or, as it turned out, the mirror-image case: a Forged-only one) could ship
 * unnoticed by this test either way. It now checks both sets.
 */
class PatternImprintCenteringTest {

    private static final String DEFAULT_TEXTURE_DIR = "src/main/resources/assets/forgeweave/textures/derived/item/";
    private static final String LEGACY_TEXTURE_DIR =
            "src/main/resources/resourcepacks/legacy/assets/forgeweave/textures/derived/item/";

    enum Set_ {
        FORGED(DEFAULT_TEXTURE_DIR),
        LEGACY(LEGACY_TEXTURE_DIR);

        final String dir;

        Set_(String dir) {
            this.dir = dir;
        }
    }

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
     * <li>Issue #626: {@code arrow_shaft} is upstream's {@code arrow/shaft.png} -- the assembled
     * arrow's own shaft layer, the same tool-layer reuse {@code bow_limb} is -- and
     * {@code arrow_head}/{@code fletching} are {@code parts/} sprites; none of their tmat files
     * carries an offset and all three center within tolerance at (0, 0).
     * </ul>
     */
    private static final String[] CENTERED_PARTS =
            {"knife_blade", "curved_blade", "katana_blade", "bow_limb", "bow_string",
                    "arrow_head", "arrow_shaft", "fletching",
                    // Issue #677: the 1.20 clone's plating/maille sprites carry no tmat offsets at
                    // all, so every offset in the table is hand-chosen -- exactly what this guards.
                    "plating_helmet", "plating_chestplate", "plating_leggings", "plating_boots", "maille"};

    /**
     * Max allowed distance (in pixels) between the imprint's bounding-box center and the pattern
     * base's own opaque-footprint center. Correctly offset imprints land within ~1.1px of center
     * (rounding an odd-width silhouette to the nearest integer offset); a dropped or reverted offset
     * put these parts 2.2-4.5px off, so 1.5px cleanly separates "centered" from "the #337 bug".
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

    /**
     * Loads {@code filename} from {@code setDir}, falling back to the Forged/default tree if the
     * Legacy pack ships no override for it -- the same fallback {@code scripts/sprite_sets.py}'s
     * {@code legacy_input} applies when generating (see that module's docstring).
     */
    private static BufferedImage image(String setDir, String filename) throws IOException {
        Path png = projectRoot().resolve(setDir + filename);
        if (!Files.exists(png)) {
            png = projectRoot().resolve(DEFAULT_TEXTURE_DIR + filename);
        }
        assertTrue(Files.exists(png), png + " is missing");
        BufferedImage img = ImageIO.read(png.toFile());
        assertNotNull(img, png + " could not be decoded");
        return img;
    }

    /** `(minX, minY, maxX, maxY)` of every non-transparent pixel in `img`. */
    private static int[] opaqueBbox(BufferedImage img, String what) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertTrue(minX <= maxX, what + " has no opaque pixels");
        return new int[] {minX, minY, maxX, maxY};
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

    private static void assertImprintCentered(Set_ set, BufferedImage base, double baseCenterX, double baseCenterY,
            String part) throws IOException {
        BufferedImage png = image(set.dir, "pattern_" + part + ".png");
        int[] bbox = imprintBoundingBox(base, png, part);
        double centerX = (bbox[0] + bbox[2] + 1) / 2.0;
        double centerY = (bbox[1] + bbox[3] + 1) / 2.0;
        double distance = Math.hypot(centerX - baseCenterX, centerY - baseCenterY);
        assertTrue(distance <= TOLERANCE_PX,
                "pattern_" + part + ".png (" + set + "): imprint bbox " + java.util.Arrays.toString(bbox)
                        + " centers at (" + centerX + ", " + centerY + "), " + distance
                        + "px from the pattern base's own footprint center (" + baseCenterX + ", " + baseCenterY
                        + ") -- exceeds " + TOLERANCE_PX + "px tolerance");
    }

    @ParameterizedTest
    @EnumSource(Set_.class)
    void offenderPartsImprintCenteredOnPatternBase(Set_ set) throws IOException {
        BufferedImage base = image(set.dir, "pattern.png");
        int[] footprint = opaqueBbox(base, set + " pattern.png");
        double baseCenterX = (footprint[0] + footprint[2] + 1) / 2.0;
        double baseCenterY = (footprint[1] + footprint[3] + 1) / 2.0;
        for (String part : OFFENDER_PARTS) {
            assertImprintCentered(set, base, baseCenterX, baseCenterY, part);
        }
    }

    @ParameterizedTest
    @EnumSource(Set_.class)
    void centeredPartsImprintCenteredOnPatternBase(Set_ set) throws IOException {
        BufferedImage base = image(set.dir, "pattern.png");
        int[] footprint = opaqueBbox(base, set + " pattern.png");
        double baseCenterX = (footprint[0] + footprint[2] + 1) / 2.0;
        double baseCenterY = (footprint[1] + footprint[3] + 1) / 2.0;
        for (String part : CENTERED_PARTS) {
            assertImprintCentered(set, base, baseCenterX, baseCenterY, part);
        }
    }
}
