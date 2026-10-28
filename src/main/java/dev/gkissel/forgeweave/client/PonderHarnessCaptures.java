package dev.gkissel.forgeweave.client;

import java.util.List;

import net.createmod.catnip.api.client.gui.ScreenOpener;
import net.createmod.ponder.api.client.element.WorldSectionElement;
import net.createmod.ponder.api.client.PonderIndex;
import net.createmod.ponder.api.client.scene.PonderScene;
import net.createmod.ponder.impl.client.gui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The screenshot harness's Ponder stage (issue #700): opens every Forgeweave Ponder scene through
 * Ponder's real UI, lets it play to its {@code markAsFinished} frame and hands the screen back to
 * {@link ScreenshotHarness} for capture. Scene playback is client-rendered and no test can see it,
 * so this is how a directional block facing away from the camera (#700's defect) or a schematic
 * that fails to load shows up before a release rather than in a playtest.
 *
 * <p>Kept out of {@code ScreenshotHarness} itself so that class never references Ponder types:
 * it is an {@code @EventBusSubscriber} loaded on every client, and Ponder's presence is a jar-in-jar
 * guarantee rather than a hard dependency (see {@code ForgeweavePonderPlugin}).
 */
final class PonderHarnessCaptures {

    /** One scene: the item the scene is registered on, which of its scenes (in registration order), and the PNG name. */
    record Capture(Identifier item, int sceneIndex, String fileName) {}

    static final List<Capture> CAPTURES = List.of(
            new Capture(ForgeweaveItems.STANDARD_CORE.getId(), 0, "ponder_smeltery"),
            new Capture(ForgeweaveItems.STANDARD_CORE.getId(), 1, "ponder_smeltery_sizes"),
            new Capture(ForgeweaveItems.FAUCET.getId(), 0, "ponder_casting"),
            // Issue #1006 (retiring the Armor Station #782 added): the armor assembly scene is back
            // on the Tool Station's item, its only scene.
            new Capture(ForgeweaveItems.TOOL_STATION.getId(), 0, "ponder_armor"),
            // Issue #891: the seared furnace and reservoir on their controllers; the core tiers
            // ladder is on every core, captured through the Nether Core's (its only scene).
            new Capture(ForgeweaveItems.SEARED_FURNACE_CONTROLLER.getId(), 0, "ponder_seared_furnace"),
            new Capture(ForgeweaveItems.SEARED_RESERVOIR_CONTROLLER.getId(), 0, "ponder_seared_reservoir"),
            new Capture(ForgeweaveItems.NETHER_CORE.getId(), 0, "ponder_core_tiers"));

    /** {@link PonderUI}'s constructor and scene paging are protected; this is the harness's way in. */
    private static final class HarnessPonderUI extends PonderUI {
        HarnessPonderUI(List<PonderScene> scenes) {
            super(scenes);
        }

        void nextScene() {
            scroll(true);
        }
    }

    /** Opens the capture's scene; the caller waits on {@link #finished(Minecraft)} before taking the frame. */
    static void open(Capture capture) {
        List<PonderScene> scenes = PonderIndex.getSceneAccess().compile(capture.item());
        HarnessPonderUI ui = new HarnessPonderUI(scenes);
        // Comfy reading slows a scene down while text is up; the harness reads nothing. This writes
        // Ponder's client config in the harness's own game directory (build.gradle), never run/config.
        ui.setComfyReadingEnabled(false);
        ScreenOpener.open(ui);
        for (int i = 0; i < capture.sceneIndex(); i++) {
            ui.nextScene();
        }
    }

    /** Whether the open Ponder scene has reached its {@code markAsFinished} instruction. */
    static boolean finished(Minecraft mc) {
        return mc.screen instanceof PonderUI ui && ui.getActiveScene().isFinished();
    }

    /**
     * How many non-air blocks the open scene shows: its world's blocks, or 0 when no world section of
     * the scene is visible at all. A scene whose schematic failed to load plays to its end anyway,
     * empty, so finishing proves nothing on its own.
     */
    static int visibleBlockCount(Minecraft mc) {
        if (!(mc.screen instanceof PonderUI ui)) {
            return 0;
        }
        PonderScene scene = ui.getActiveScene();
        boolean anySectionShown = !scene.getBaseWorldSection().isEmpty() || scene.getElements().stream()
                .anyMatch(element -> element instanceof WorldSectionElement section && !section.isEmpty());
        if (!anySectionShown) {
            return 0;
        }
        return (int) scene.getWorld().getBlockMap().values().stream().filter(state -> !state.isAir()).count();
    }

    private PonderHarnessCaptures() {}
}
