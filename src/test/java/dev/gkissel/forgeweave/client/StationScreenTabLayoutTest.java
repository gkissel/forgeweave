package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Guards issue #86: the Pattern/Part Chest rendered its tab row off the top of the window.
 *
 * <p>{@code AbstractContainerScreen} centres on {@code imageHeight}, which covers the panel and not
 * the 28px tab strip drawn above it, so every tabbed station sat half a strip too high. The 166 and
 * 174px stations had the headroom to absorb that; the 222px chest did not.
 *
 * <p>Vanilla picks the largest integer GUI scale whose scaled size is still at least 320x240, so
 * 240 is the smallest scaled height that can ever occur -- and the case the chest failed hardest.
 *
 * <p>The chest itself is a 166px panel again since parity audit T45 (issue #476) replaced its
 * {@code generic_54} background with upstream's scaling chest, so 222 below is the height that
 * exposed the bug rather than any station's current one. The formula is what is under test.
 */
class StationScreenTabLayoutTest {

    private static final int TAB_STRIP = 28;
    private static final int CHEST_HEIGHT = 222;
    /** The other stations, for the no-regression check -- and, since issue #476, the chest too. */
    private static final int PART_BUILDER_HEIGHT = 166;
    private static final int TOOL_STATION_HEIGHT = 174;

    /** Vanilla's floor: the smallest scaled height any GUI scale can produce. */
    private static final int MIN_SCALED_HEIGHT = 240;

    private static int tabTop(int screenHeight, int imageHeight) {
        return StationScreen.centreWithTabStrip(screenHeight, imageHeight) - TAB_STRIP;
    }

    /** What vanilla does on its own, i.e. the pre-fix behaviour. */
    private static int unfixedTabTop(int screenHeight, int imageHeight) {
        return (screenHeight - imageHeight) / 2 - TAB_STRIP;
    }

    @Test
    void theChestTabRowFitsOnceThereIsRoomForIt() {
        // 250px of content needs a 250px window; from there up the tab row must be fully visible.
        for (int screenHeight = CHEST_HEIGHT + TAB_STRIP; screenHeight <= 1080; screenHeight++) {
            assertTrue(tabTop(screenHeight, CHEST_HEIGHT) >= 0,
                    "chest tab row is off the top of a " + screenHeight + "px window (top = "
                            + tabTop(screenHeight, CHEST_HEIGHT) + ")");
        }
    }

    @Test
    void theUnfixedCentringIsWhatPushedTheChestOffScreen() {
        // The regression itself: vanilla's centring loses the tab row on any window that a 250px
        // layout does fit in, up to the point where the panel alone leaves a full strip of headroom.
        assertTrue(unfixedTabTop(MIN_SCALED_HEIGHT, CHEST_HEIGHT) < 0,
                "expected the pre-fix formula to put the chest tab row off-screen at the minimum "
                        + "scaled height -- if it no longer does, this guard is testing nothing");
        assertTrue(unfixedTabTop(270, CHEST_HEIGHT) < 0 && tabTop(270, CHEST_HEIGHT) >= 0,
                "a 270px window has room for the whole 250px layout; the fix must be what recovers it");
    }

    @Test
    void overflowIsSharedWhenTheLayoutIsTallerThanTheWindow() {
        // 222 + 28 > 240: something must clip. It should not be the whole tab row.
        int top = StationScreen.centreWithTabStrip(MIN_SCALED_HEIGHT, CHEST_HEIGHT);
        int clippedAbove = -Math.min(0, top - TAB_STRIP);
        int clippedBelow = Math.max(0, top + CHEST_HEIGHT - MIN_SCALED_HEIGHT);
        assertTrue(clippedAbove <= TAB_STRIP / 2 && clippedBelow <= TAB_STRIP / 2,
                "overflow should be split between the two edges, got " + clippedAbove + " above and "
                        + clippedBelow + " below");
        assertTrue(clippedAbove < unfixedClipAbove(),
                "the fix must clip less of the tab row than vanilla's centring did");
    }

    private static int unfixedClipAbove() {
        return -Math.min(0, unfixedTabTop(MIN_SCALED_HEIGHT, CHEST_HEIGHT));
    }

    @Test
    void theShortStationsStillFitAtTheMinimumScaledHeight() {
        for (int imageHeight : new int[] {PART_BUILDER_HEIGHT, TOOL_STATION_HEIGHT}) {
            int top = StationScreen.centreWithTabStrip(MIN_SCALED_HEIGHT, imageHeight);
            assertTrue(top - TAB_STRIP >= 0,
                    "tab row off-screen for a " + imageHeight + "px station at the minimum scaled height");
            assertTrue(top + imageHeight <= MIN_SCALED_HEIGHT,
                    "panel bottom off-screen for a " + imageHeight + "px station at the minimum scaled height");
        }
    }
}
