package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Issues #615 and #699: the five tools upstream 1.12 gives a {@code display} block of their own
 * (cleaver, rapier, battlesign, shortbow, longbow) carry their {@code *_lefthand} entries
 * <em>verbatim</em> from the {@code .tcon.json} files.
 *
 * <p>#615 read those entries as already-rendered values -- vanilla's {@code ItemTransform#apply}
 * mirrors {@code rotation.y}, {@code rotation.z} and {@code translation.x} for every left-hand
 * context, so a pre-mirrored entry "gets mirrored twice" -- and dropped or sign-compensated them.
 * That was the bug #699 reports: the second mirror is vanilla's convention. Vanilla's own
 * {@code item/handheld} and {@code item/bow} author their left entries exactly the way 1.12's
 * {@code .tcon.json} files do (rotation y/z negated, translation.x kept) and feed them through the
 * same {@code apply}; Forge 1.12's {@code ForgeHooksClient#handleCameraTransforms} conjugated the
 * authored left matrix by {@code flipX} the same way. Every other Forgeweave tool inherits
 * {@code item/handheld}'s pre-mirrored left entries and poses correctly in the off-hand; #615 had
 * negated these five tools' off-hand rotation relative to them. Pinning the verbatim values keeps
 * the next "double mirror" reading from re-introducing it.
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

    private static void assertTransform(JsonObject display, String context,
            float rx, float ry, float rz, float tx, float ty, float tz, float sx, float sy, float sz) {
        JsonObject transform = display.getAsJsonObject(context);
        assertNotNull(transform, context + " must be declared verbatim from upstream, not left to fallback");
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

    /** cleaver.tcon.json, all four held contexts verbatim. */
    @Test
    void cleaverHeldPosesAreUpstreamsVerbatim() throws IOException {
        JsonObject display = displayOf("cleaver");
        assertTransform(display, "thirdperson_righthand", 0, -90, 55, 0, 10.0F, 0.5F, 1.5F, 1.5F, 1.5F);
        assertTransform(display, "thirdperson_lefthand", 0, 90, -55, 0, 10.0F, 0.5F, 1.5F, 1.5F, 1.5F);
        assertTransform(display, "firstperson_righthand", 0, -95, 30, 2.13F, 6.0F, 0.13F, 1.2F, 1.2F, 1.2F);
        assertTransform(display, "firstperson_lefthand", 0, 95, -30, 2.13F, 6.0F, 0.13F, 1.2F, 1.2F, 1.2F);
    }

    /** rapier.tcon.json, all four held contexts verbatim. */
    @Test
    void rapierHeldPosesAreUpstreamsVerbatim() throws IOException {
        JsonObject display = displayOf("rapier");
        assertTransform(display, "thirdperson_righthand", 0, 90, 15, 0, 4.5F, -1, 0.85F, 0.85F, 0.85F);
        assertTransform(display, "thirdperson_lefthand", 0, -90, -15, 0, 4.5F, -1, 0.85F, 0.85F, 0.85F);
        assertTransform(display, "firstperson_righthand", 0, 90, -25, 0, 2, 0.8F, 0.68F, 0.68F, 0.68F);
        assertTransform(display, "firstperson_lefthand", 0, -90, 25, 0, 2, 0.8F, 0.68F, 0.68F, 0.68F);
    }

    /** battlesign.tcon.json: upstream's first-person left entry negates translation.z, not rotation.z. */
    @Test
    void battlesignHeldPosesAreUpstreamsVerbatim() throws IOException {
        JsonObject display = displayOf("battlesign");
        assertTransform(display, "thirdperson_righthand", 0, 0, 0, 0, 4.0F, 2.5F, 1, 1, 1);
        assertTransform(display, "thirdperson_lefthand", 0, 0, 0, 0, 4.0F, 2.5F, 1, 1, 1);
        assertTransform(display, "firstperson_righthand", 0, 0, -5, 0, -2, 0.8F, 1, 1, 1);
        assertTransform(display, "firstperson_lefthand", 0, 0, -5, 0, -2, -0.8F, 1, 1, 1);
    }

    /** shortbow.tcon.json: the same left-hand authoring as vanilla's own bow.json. */
    @Test
    void shortbowHeldPosesAreUpstreamsVerbatim() throws IOException {
        JsonObject display = displayOf("shortbow");
        assertTransform(display, "thirdperson_righthand",
                -90, 260, -45, -1, -2, 2.5F, 0.875F, 0.875F, 0.75F);
        assertTransform(display, "thirdperson_lefthand",
                -90, -260, 45, -1, -2, 2.5F, 0.875F, 0.875F, 0.75F);
        assertTransform(display, "firstperson_righthand",
                0, -90, 25, 1.13F, 3.2F, 1.13F, 0.68F, 0.68F, 0.68F);
        assertTransform(display, "firstperson_lefthand",
                0, 90, -25, 1.13F, 3.2F, 1.13F, 0.68F, 0.68F, 0.68F);
    }

    /** longbow.tcon.json: the shortbow's angles, held further out and scaled up. */
    @Test
    void longbowHeldPosesAreUpstreamsVerbatim() throws IOException {
        JsonObject display = displayOf("longbow");
        assertTransform(display, "thirdperson_righthand",
                -90, 260, -45, -1.875F, -1.25F, 4.0F, 1.0625F, 1.0625F, 0.875F);
        assertTransform(display, "thirdperson_lefthand",
                -90, -260, 45, -1.875F, -1.25F, 4.0F, 1.0625F, 1.0625F, 0.875F);
        assertTransform(display, "firstperson_righthand",
                0, -90, 25, 1.13F, 3.2F, 1.13F, 0.875F, 0.875F, 0.7F);
        assertTransform(display, "firstperson_lefthand",
                0, 90, -25, 1.13F, 3.2F, 1.13F, 0.875F, 0.875F, 0.7F);
    }

    /**
     * The crossbow's block is #616's, re-based on vanilla's own crossbow display -- whose left-hand
     * entries follow the same pre-mirrored convention -- see {@code BowDrawModelTest}.
     */
    @Test
    void crossbowKeepsItsOwnLeftHandEntriesUnchanged() throws IOException {
        JsonObject display = displayOf("crossbow");
        assertTransform(display, "thirdperson_lefthand", -90, 0, 30, 2, 0.1F, -3, 0.9F, 0.9F, 0.9F);
        assertTransform(display, "firstperson_lefthand", -90, 0, 35, 1.13F, 3.2F, 1.13F, 0.68F, 0.68F, 0.68F);
    }
}
