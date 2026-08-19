package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Issue #615, a follow-up to #601/#616: {@code ForgeweaveItemModelProvider} transcribed five tools'
 * {@code *_lefthand} display entries verbatim from upstream 1.12's {@code .tcon.json} files, where the
 * left-hand entry is the right-hand one already mirrored by hand. Vanilla's {@code ItemTransform#apply}
 * mirrors {@code rotation.y}, {@code rotation.z} and {@code translation.x} again for every left-hand
 * context whatever the source of the stored transform, and {@code ItemTransforms.Deserializer} already
 * falls a missing {@code *_lefthand} back to its {@code *_righthand} sibling -- vanilla's own convention
 * is "author the right hand, let {@code apply} mirror it" -- so a pre-mirrored entry gets mirrored twice.
 *
 * <p>Per tool, per context: where 1.12's authored left entry <em>is</em> the exact single mirror of its
 * right entry (rotation.y/z negated, translation.x negated), the fix is to stop emitting the
 * {@code *_lefthand} key at all and let vanilla's fallback-then-mirror reproduce it. Where it is not --
 * cleaver and both bows keep {@code translation.x} the same on both hands rather than negating it, and
 * battlesign's first-person pose negates {@code translation.z} (untouched by {@code apply}) instead of
 * {@code rotation.z} -- the fix is to emit the value that {@code apply}'s single mirror turns back into
 * upstream's authored (and, per #616's working assumption for this provider, upstream-rendered) pose:
 * {@code stored = mirror(target)}, i.e. rotation.y/z and translation.x negated from the target, every
 * other field carried over unchanged. Rapier's two contexts are both exact mirrors (its handedness-
 * sensitive field, translation.x, is 0 on both sides), so it drops both {@code *_lefthand} entries
 * outright. The crossbow is excluded: #616 already re-based it on vanilla's own crossbow display block,
 * whose {@code *_lefthand} entries are vanilla's, not upstream 1.12's, and are correct by construction.
 */
class LeftHandDisplayTransformTest {

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static JsonObject displayOf(String tool) throws IOException {
        Path path = projectRoot()
                .resolve("src/generated/resources/assets/forgeweave/models/item/" + tool + ".json");
        assertTrue(Files.isRegularFile(path), "expected model JSON at " + path);
        JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject display = json.getAsJsonObject("display");
        assertNotNull(display, tool + " has no display block");
        return display;
    }

    private static void assertNoTransform(JsonObject display, String context) {
        assertFalse(display.has(context),
                context + " must be absent so vanilla falls it back to its right-hand sibling and mirrors that");
    }

    private static void assertTransform(JsonObject display, String context,
            float rx, float ry, float rz, float tx, float ty, float tz, float sx, float sy, float sz) {
        JsonObject transform = display.getAsJsonObject(context);
        assertNotNull(transform, context + " must be declared");
        assertEquals(List.of(rx, ry, rz), floats(transform, "rotation"), context + " rotation");
        assertEquals(List.of(tx, ty, tz), floats(transform, "translation"), context + " translation");
        assertEquals(List.of(sx, sy, sz), floats(transform, "scale"), context + " scale");
    }

    /**
     * A field is omitted from the generated JSON when it equals its default: {@code (0,0,0)} for
     * rotation and translation, {@code (1,1,1)} for scale.
     */
    private static List<Float> floats(JsonObject transform, String key) {
        if (!transform.has(key)) {
            float fallback = key.equals("scale") ? 1F : 0F;
            return List.of(fallback, fallback, fallback);
        }
        List<Float> values = new ArrayList<>(3);
        for (JsonElement element : transform.getAsJsonArray(key)) {
            values.add(element.getAsFloat());
        }
        return values;
    }

    /** Cleaver: third-person is an exact mirror (translation.x is 0); first-person is not. */
    @Test
    void cleaverFirstPersonLeftHandIsCompensatedForVanillasDoubleMirror() throws IOException {
        JsonObject display = displayOf("cleaver");
        assertNoTransform(display, "thirdperson_lefthand");
        assertTransform(display, "thirdperson_righthand", 0, -90, 55, 0, 10.0F, 0.5F, 1.5F, 1.5F, 1.5F);
        assertTransform(display, "firstperson_righthand", 0, -95, 30, 2.13F, 6.0F, 0.13F, 1.2F, 1.2F, 1.2F);
        assertTransform(display, "firstperson_lefthand", 0, -95, 30, -2.13F, 6.0F, 0.13F, 1.2F, 1.2F, 1.2F);
    }

    /** Rapier: both contexts are exact mirrors (translation.x is 0 throughout), so both entries drop. */
    @Test
    void rapierDropsBothLeftHandEntriesEntirely() throws IOException {
        JsonObject display = displayOf("rapier");
        assertNoTransform(display, "thirdperson_lefthand");
        assertNoTransform(display, "firstperson_lefthand");
        assertTransform(display, "thirdperson_righthand", 0, 90, 15, 0, 4.5F, -1, 0.85F, 0.85F, 0.85F);
        assertTransform(display, "firstperson_righthand", 0, 90, -25, 0, 2, 0.8F, 0.68F, 0.68F, 0.68F);
    }

    /** Battlesign: third-person is a trivial mirror; first-person mirrors translation.z, not rotation.z. */
    @Test
    void battlesignFirstPersonLeftHandIsCompensatedForVanillasDoubleMirror() throws IOException {
        JsonObject display = displayOf("battlesign");
        assertNoTransform(display, "thirdperson_lefthand");
        assertTransform(display, "thirdperson_righthand", 0, 0, 0, 0, 4.0F, 2.5F, 1, 1, 1);
        assertTransform(display, "firstperson_righthand", 0, 0, -5, 0, -2, 0.8F, 1, 1, 1);
        assertTransform(display, "firstperson_lefthand", 0, 0, 5, 0, -2, -0.8F, 1, 1, 1);
    }

    /** Shortbow: 1.12 keeps translation.x the same on both hands instead of mirroring it. */
    @Test
    void shortbowLeftHandTransformsAreCompensatedForVanillasDoubleMirror() throws IOException {
        JsonObject display = displayOf("shortbow");
        assertTransform(display, "thirdperson_righthand",
                -90, 260, -45, -1, -2, 2.5F, 0.875F, 0.875F, 0.75F);
        assertTransform(display, "thirdperson_lefthand",
                -90, 260, -45, 1, -2, 2.5F, 0.875F, 0.875F, 0.75F);
        assertTransform(display, "firstperson_righthand",
                0, -90, 25, 1.13F, 3.2F, 1.13F, 0.68F, 0.68F, 0.68F);
        assertTransform(display, "firstperson_lefthand",
                0, -90, 25, -1.13F, 3.2F, 1.13F, 0.68F, 0.68F, 0.68F);
    }

    /** Longbow: the shortbow's shape, held further out and scaled up -- same translation.x defect. */
    @Test
    void longbowLeftHandTransformsAreCompensatedForVanillasDoubleMirror() throws IOException {
        JsonObject display = displayOf("longbow");
        assertTransform(display, "thirdperson_righthand",
                -90, 260, -45, -1.875F, -1.25F, 4.0F, 1.0625F, 1.0625F, 0.875F);
        assertTransform(display, "thirdperson_lefthand",
                -90, 260, -45, 1.875F, -1.25F, 4.0F, 1.0625F, 1.0625F, 0.875F);
        assertTransform(display, "firstperson_righthand",
                0, -90, 25, 1.13F, 3.2F, 1.13F, 0.875F, 0.875F, 0.7F);
        assertTransform(display, "firstperson_lefthand",
                0, -90, 25, -1.13F, 3.2F, 1.13F, 0.875F, 0.875F, 0.7F);
    }

    /**
     * The crossbow is untouched by #615: #616 re-based its held poses on vanilla's own crossbow display
     * block, whose left-hand entries are vanilla's (deliberately not a mirror of the right hand) rather
     * than upstream 1.12's, and are correct by construction -- see {@code BowDrawModelTest}.
     */
    @Test
    void crossbowKeepsItsOwnLeftHandEntriesUnchanged() throws IOException {
        JsonObject display = displayOf("crossbow");
        assertTransform(display, "thirdperson_lefthand", -90, 0, 30, 2, 0.1F, -3, 0.9F, 0.9F, 0.9F);
        assertTransform(display, "firstperson_lefthand", -90, 0, 35, 1.13F, 3.2F, 1.13F, 0.68F, 0.68F, 0.68F);
    }
}
