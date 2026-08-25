package dev.gkissel.forgeweave.client;

import java.util.List;

import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
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
    record Capture(ResourceLocation item, int sceneIndex, String fileName) {}

    static final List<Capture> CAPTURES = List.of(
            new Capture(ForgeweaveItems.STANDARD_CORE.getId(), 0, "ponder_smeltery"),
            new Capture(ForgeweaveItems.STANDARD_CORE.getId(), 1, "ponder_smeltery_sizes"),
            new Capture(ForgeweaveItems.FAUCET.getId(), 0, "ponder_casting"),
            new Capture(ForgeweaveBlocks.TOOL_STATION.getId(), 0, "ponder_armor"));

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
        ScreenOpener.open(ui);
        for (int i = 0; i < capture.sceneIndex(); i++) {
            ui.nextScene();
        }
    }

    /** Whether the open Ponder scene has reached its {@code markAsFinished} instruction. */
    static boolean finished(Minecraft mc) {
        return mc.screen instanceof PonderUI ui && ui.getActiveScene().isFinished();
    }

    private PonderHarnessCaptures() {}
}
