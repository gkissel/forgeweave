package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;

import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolArt;

/**
 * M3.5 issue #400: the release-checklist scene list actually covers every draw state a bow's model
 * can resolve to. Coverage-shaped for the same reason {@code ItemColorCoverageTest} is -- the
 * harness's own scene checks only fire when the harness runs, which is a Gradle task nobody runs per
 * commit, so "the frame exists at all" has to be a unit test.
 */
class BowScreenshotSceneTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<Item> bows() {
        return ToolAssemblyRecipes.ENTRIES.stream()
                .filter(entry -> ToolArt.drawThresholds(entry.constants().id()) != null)
                .<Item>map(entry -> entry.tool().get())
                .toList();
    }

    /**
     * Issue #712: the harness flags a capture whose shape is not the 854x480 window it asked for. A
     * tiling compositor re-tiling the client into a portrait column is what emptied the undrawn
     * bows' first-person frames -- the pose is at the right edge of a 16:9 frame -- and an empty
     * frame must fail a scene check rather than pass for a model bug.
     */
    @Test
    void aReTiledWindowFailsTheFrameShapeCheck() {
        assertTrue(ScreenshotHarness.isExpectedFrameShape(854, 480), "the requested window");
        assertTrue(ScreenshotHarness.isExpectedFrameShape(908, 513), "the requested window plus decorations");
        assertTrue(!ScreenshotHarness.isExpectedFrameShape(908, 1038), "a portrait tile");
        assertTrue(!ScreenshotHarness.isExpectedFrameShape(854, 0), "an empty frame");
    }

    /** Every bow gets the undrawn third- and first-person pair every other tool on the list gets. */
    @Test
    void everyBowIsPosedUndrawn() {
        List<Item> posed = ScreenshotHarness.WEAPONS.stream().<Item>map(java.util.function.Supplier::get).toList();
        List<Item> missing = bows().stream().filter(bow -> !posed.contains(bow)).toList();
        assertTrue(missing.isEmpty(), () -> "bows with no held capture: "
                + missing.stream().map(BuiltInRegistries.ITEM::getKey).toList());
    }

    /** And a drawn frame per pull stage, so a stage whose art or threshold is wrong shows up in one. */
    @Test
    void everyBowIsPosedAtEveryPullStage() {
        List<String> missing = new ArrayList<>();
        for (Item bow : bows()) {
            for (int stage = 1; stage <= ToolArt.DRAW_STAGES; stage++) {
                int wanted = stage;
                boolean posed = ScreenshotHarness.BOW_POSES.stream()
                        .anyMatch(pose -> pose.bow().get() == bow && !pose.loaded() && pose.drawStage() == wanted);
                if (!posed) {
                    missing.add(BuiltInRegistries.ITEM.getKey(bow) + " stage " + stage);
                }
            }
        }
        assertTrue(missing.isEmpty(), "bow draw states with no capture: " + missing);
    }

    /** The crossbow's stored crank is its own model, so it is its own frame. */
    @Test
    void theLoadedCrossbowIsPosed() {
        List<ScreenshotHarness.BowPose> loaded = ScreenshotHarness.BOW_POSES.stream()
                .filter(ScreenshotHarness.BowPose::loaded)
                .toList();
        assertEquals(1, loaded.size(), "exactly one loaded pose, the crossbow's");
        assertEquals(ForgeweaveItems.TOOL_CROSSBOW.get(), loaded.get(0).bow().get());
        assertEquals("bow_crossbow_loaded", loaded.get(0).fileName());
    }

    /**
     * Issue #425: the crossbow's arm poses are third-person artifacts by definition -- a first-person
     * frame cannot show an arm pose at all -- so those states get a second capture from behind. Only
     * the crossbow's: every other pose on the list differs from its neighbours by <em>model</em>,
     * which first person shows better, and the arm is doing the same thing in all of them.
     *
     * <p>Issue #601 added the mid-crank one. The cranking pose is an <em>animation</em>, and the
     * fully-wound frame is identical whatever paces it; stage 2, roughly half the crossbow's own
     * draw, is the frame where a crank running on vanilla's 25-tick clock instead of the crossbow's
     * 45 is visibly ahead of the limbs.
     */
    @Test
    void theCrossbowsArmPosesAreAlsoCapturedInThirdPerson() {
        List<String> thirdPerson = ScreenshotHarness.BOW_POSES.stream()
                .filter(ScreenshotHarness.BowPose::thirdPerson)
                .map(ScreenshotHarness.BowPose::fileName)
                .toList();
        assertEquals(List.of("bow_crossbow_draw2", "bow_crossbow_draw3", "bow_crossbow_loaded"), thirdPerson,
                "the crank half-wound, the crank finished, and the shouldered one");
    }

    /**
     * Issue #673: the draw is pressed once, waited on until the server-synced using flag and the
     * wanted stage are both there, and bounded -- a draw that never syncs times out instead of
     * hanging the run for good, which is what the client-only {@code startUsingItem} did.
     */
    @Test
    void aDrawnPosePressesOnceWaitsForTheStageAndTimesOut() {
        assertEquals(ScreenshotHarness.DrawStep.PRESS, ScreenshotHarness.drawStep(15, false, 0, 2));
        assertEquals(ScreenshotHarness.DrawStep.WAIT, ScreenshotHarness.drawStep(16, false, 0, 2));
        assertEquals(ScreenshotHarness.DrawStep.WAIT, ScreenshotHarness.drawStep(40, true, 1, 2));
        assertEquals(ScreenshotHarness.DrawStep.CAPTURE, ScreenshotHarness.drawStep(40, true, 2, 2));
        assertEquals(ScreenshotHarness.DrawStep.CAPTURE, ScreenshotHarness.drawStep(40, true, 3, 2));
        assertEquals(ScreenshotHarness.DrawStep.TIMED_OUT, ScreenshotHarness.drawStep(200, false, 0, 2));
        assertEquals(ScreenshotHarness.DrawStep.TIMED_OUT, ScreenshotHarness.drawStep(500, true, 1, 2));
    }

    /** File names are the frames a reviewer looks for; keep them derived from the item, not hand-typed. */
    @Test
    void poseFileNamesNameTheirBowAndState() {
        for (ScreenshotHarness.BowPose pose : ScreenshotHarness.BOW_POSES) {
            BowItem bow = pose.bow().get();
            String name = BuiltInRegistries.ITEM.getKey(bow).getPath();
            assertTrue(pose.fileName().startsWith("bow_" + name),
                    pose.fileName() + " should name the bow it poses");
        }
    }
}
