package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

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
 * checks the one invariant that regenerating restores and staleness breaks cheaply: every opaque
 * pixel of the part's own art must be fully transparent in its cast composite. A stale cast --
 * built from a differently-shaped part -- fails this on at least one pixel where the current part
 * is opaque but the old hole wasn't. See {@link
 * dev.gkissel.forgeweave.client.PatternImprintCenteringTest} for the same
 * catch-drift-without-reimplementing-the-algorithm approach applied to the pattern composites.
 */
class CastCompositeHoleTest {

    private static final String DERIVED_ITEM_DIR = "src/main/resources/assets/forgeweave/textures/derived/item/";

    /** (part source PNG relative to {@link #DERIVED_ITEM_DIR}, cast composite file name). */
    private record Part(String partFile, String castFile) {}

    /**
     * Both parts were re-sourced again by issue #375 -- {@code curved_blade} from Spartan
     * Weaponry's saber blade, {@code katana_blade} from its katana blade (NOTICE.md,
     * {@code licenses/APACHE-2.0-SpartanWeaponry.txt}) -- which is the second re-sourcing this test
     * has had to catch stale casts for. The {@code katana_blade} entry no longer needs the
     * "authored, so read it from outside the derived tree" flag it carried between #279 and #375:
     * the blade is derived art again, so both parts live under {@link #DERIVED_ITEM_DIR}.
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
    };

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

    @Test
    void castHoleCoversTheCurrentPartSilhouette() throws IOException {
        for (Part part : PARTS) {
            BufferedImage partArt = image(DERIVED_ITEM_DIR + part.partFile());
            BufferedImage cast = image(DERIVED_ITEM_DIR + part.castFile());

            for (int y = 0; y < partArt.getHeight(); y++) {
                for (int x = 0; x < partArt.getWidth(); x++) {
                    boolean partOpaque = (partArt.getRGB(x, y) >>> 24) > 0;
                    if (!partOpaque) {
                        continue;
                    }
                    int castAlpha = cast.getRGB(x, y) >>> 24;
                    assertTrue(castAlpha == 0,
                            part.castFile() + ": pixel (" + x + ", " + y + ") is opaque in " + part.partFile()
                                    + " but not punched through in the cast composite -- rerun "
                                    + "scripts/generate_cast_textures.py and commit its output (issue #373)");
                }
            }
        }
    }
}
