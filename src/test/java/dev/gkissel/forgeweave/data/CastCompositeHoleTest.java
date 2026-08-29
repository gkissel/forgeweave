package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Guards issue #373: {@code scripts/generate_cast_textures.py} punches a hole through
 * {@code cast.png} in each part's own silhouette (see the script's module docstring), so a cast
 * composite that was committed before its part's art was re-sourced still shows the *old*
 * silhouette's hole -- exactly what happened to {@code cast_curved_blade.png} and
 * {@code cast_katana_blade.png} after issue #279 re-sourced {@code curved_blade.png} (upstream's
 * cutlass blade) and {@code katana_blade.png} (freshly authored) without anyone re-running the
 * script.
 *
 * <p>This does not replay the script's bevel-shading algorithm in Java (it exists only in
 * Python, so that would mean maintaining two implementations in lockstep for a test); instead it
 * checks two invariants that regenerating restores and staleness or a bad centering offset break
 * cheaply: (1) every opaque pixel of the part's own art, translated by the hole's own offset from
 * the part, is fully transparent in its cast composite, and (2) that hole sits centered on the
 * cast base's own opaque footprint (its cavity/usable region -- see
 * {@code scripts/sprite_sets.py}'s {@code opaque_bbox}). See {@link
 * dev.gkissel.forgeweave.client.PatternImprintCenteringTest} for the same
 * catch-drift-without-reimplementing-the-algorithm approach applied to the pattern composites.
 *
 * <p>Issue #802: before this test only checked (1) -- that a hole exists in the part's current
 * shape -- so a hole that exists but sits at the wrong place (the #802 regression: the Forged
 * {@code cast.png} draws an inset mold face the old, uncentered algorithm never accounted for)
 * passed clean. It now checks both sets this repo ships: the default Forged tree and the built-in
 * Legacy resource pack (issue #796), since #802 was specifically a Forged-only regression that a
 * Forged-only test would not have caught either.
 */
class CastCompositeHoleTest {

    private static final String DEFAULT_ITEM_DIR = "src/main/resources/assets/forgeweave/textures/derived/item/";
    private static final String LEGACY_ITEM_DIR =
            "src/main/resources/resourcepacks/legacy/assets/forgeweave/textures/derived/item/";

    enum Set_ {
        FORGED(DEFAULT_ITEM_DIR),
        LEGACY(LEGACY_ITEM_DIR);

        final String dir;

        Set_(String dir) {
            this.dir = dir;
        }
    }

    /** (part source PNG relative to the item texture dir, cast composite file name). */
    private record Part(String partFile, String castFile) {}

    /**
     * Both parts were re-sourced again by issue #375 -- {@code curved_blade} from Spartan
     * Weaponry's saber blade, {@code katana_blade} from its katana blade (NOTICE.md,
     * {@code licenses/APACHE-2.0-SpartanWeaponry.txt}) -- which is the second re-sourcing this test
     * has had to catch stale casts for. The {@code katana_blade} entry no longer needs the
     * "authored, so read it from outside the derived tree" flag it carried between #279 and #375:
     * the blade is derived art again, so both parts live under {@link #DEFAULT_ITEM_DIR}.
     */
    private static final Part[] PARTS = {
        new Part("curved_blade.png", "cast_curved_blade.png"),
        new Part("katana_blade.png", "cast_katana_blade.png"),
        // Issue #393: the bow limb is the M3.5 part that casts (the bow string does not -- no
        // BOWSTRING material melts, see BowPartTest), and its art is upstream's shortbow limb
        // sprite, drawn hard against one corner rather than centered. A cast composited before a
        // re-source of that sprite would leave the hole in the wrong corner entirely.
        new Part("bow_limb.png", "cast_bow_limb.png"),
        // Issue #626: the arrow head is the T17 part that casts (the shaft and fletching do not --
        // no SHAFT/FLETCHING material melts, the bow-string situation exactly).
        new Part("arrow_head.png", "cast_arrow_head.png"),
        // Issue #677: the armor parts, the first sprites sourced from the 1.20 clone.
        new Part("plating_helmet.png", "cast_plating_helmet.png"),
        new Part("plating_chestplate.png", "cast_plating_chestplate.png"),
        new Part("plating_leggings.png", "cast_plating_leggings.png"),
        new Part("plating_boots.png", "cast_plating_boots.png"),
        new Part("maille.png", "cast_maille.png"),
        // Issue #802's own reproduction case -- a plain head/handle/binding part with no dedicated
        // upstream offset table entry at all, so the only thing that can center it is the base's
        // own cavity.
        new Part("pickaxe_head.png", "cast_pickaxe_head.png"),
        new Part("hammer_head.png", "cast_hammer_head.png"),
        new Part("tool_binding.png", "cast_tool_binding.png"),
    };

    /**
     * Max allowed distance (in pixels) between the punched hole's bounding-box center and the cast
     * base's own opaque-footprint center. A correctly centered hole lands within ~1px of that
     * center (integer-rounded offset against an even/odd-width silhouette); the #802 regression put
     * unoffset parts like {@code pickaxe_head} and {@code hammer_head} 2-3px off, so this cleanly
     * separates "centered" from "the #802 bug" the same way {@code PatternImprintCenteringTest}'s
     * {@code TOLERANCE_PX} does for pattern imprints.
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
     * {@code legacy_input} applies when generating, since most files never need a Legacy override
     * (see that module's docstring).
     */
    private static BufferedImage image(String setDir, String filename) throws IOException {
        Path png = projectRoot().resolve(setDir + filename);
        if (!Files.exists(png)) {
            png = projectRoot().resolve(DEFAULT_ITEM_DIR + filename);
        }
        assertTrue(Files.exists(png), png + " is missing");
        BufferedImage img = ImageIO.read(png.toFile());
        assertNotNull(img, png + " could not be decoded");
        return img;
    }

    private static boolean opaque(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y) >>> 24) > 0;
    }

    /** `(minX, minY, maxX, maxY)` of every opaque pixel in `img`. */
    private static int[] opaqueBbox(BufferedImage img, String what) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (opaque(img, x, y)) {
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

    /** `(minX, minY, maxX, maxY)` of every fully transparent pixel in `img` -- the punched hole. */
    private static int[] holeBbox(BufferedImage img, String what) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (!opaque(img, x, y)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertTrue(minX <= maxX, what + " has no punched hole (fully opaque)");
        return new int[] {minX, minY, maxX, maxY};
    }

    private static double centerX(int[] bbox) {
        return (bbox[0] + bbox[2] + 1) / 2.0;
    }

    private static double centerY(int[] bbox) {
        return (bbox[1] + bbox[3] + 1) / 2.0;
    }

    @ParameterizedTest
    @EnumSource(Set_.class)
    void castHolePunchesCurrentPartAndCentersOnCastBase(Set_ set) throws IOException {
        BufferedImage cast = image(set.dir, "cast.png");
        int[] castFootprint = opaqueBbox(cast, set + " cast.png");
        double castCenterX = centerX(castFootprint);
        double castCenterY = centerY(castFootprint);

        for (Part part : PARTS) {
            BufferedImage partArt = image(set.dir, part.partFile());
            BufferedImage composite = image(set.dir, part.castFile());

            int[] partBbox = opaqueBbox(partArt, set + " " + part.partFile());
            int[] hole = holeBbox(composite, set + " " + part.castFile());

            // (1) staleness: the hole is exactly the part's own silhouette, translated by however
            // much the generator offset it to center it -- not a leftover hole from an older,
            // differently-shaped part. Derive that translation from the two bounding boxes (rather
            // than reimplementing sprite_sets.centering_offset here) and verify every opaque part
            // pixel maps to a punched-through composite pixel.
            int offsetX = hole[0] - partBbox[0];
            int offsetY = hole[1] - partBbox[1];
            for (int y = 0; y < partArt.getHeight(); y++) {
                for (int x = 0; x < partArt.getWidth(); x++) {
                    if (!opaque(partArt, x, y)) {
                        continue;
                    }
                    int cx = x + offsetX;
                    int cy = y + offsetY;
                    boolean inBounds = cx >= 0 && cx < composite.getWidth() && cy >= 0 && cy < composite.getHeight();
                    assertTrue(inBounds && !opaque(composite, cx, cy),
                            part.castFile() + " (" + set + "): pixel (" + x + ", " + y + ") is opaque in "
                                    + part.partFile() + " but not punched through (translated by " + offsetX + ", "
                                    + offsetY + ") in the cast composite -- rerun "
                                    + "scripts/generate_cast_textures.py and commit its output (issue #373)");
                }
            }

            // (2) centering (issue #802): the hole's bounding-box center must sit on the cast
            // base's own opaque footprint's center, not merely somewhere that happens to be fully
            // punched through.
            double distance = Math.hypot(centerX(hole) - castCenterX, centerY(hole) - castCenterY);
            assertTrue(distance <= TOLERANCE_PX,
                    part.castFile() + " (" + set + "): hole bbox " + java.util.Arrays.toString(hole)
                            + " centers at (" + centerX(hole) + ", " + centerY(hole) + "), " + distance
                            + "px from the cast base's own footprint center (" + castCenterX + ", " + castCenterY
                            + ") -- exceeds " + TOLERANCE_PX + "px tolerance (issue #802)");
        }
    }
}
