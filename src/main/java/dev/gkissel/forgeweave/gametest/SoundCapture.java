package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/**
 * Captures {@code Level#playSound}/{@code #playSeededSound} calls at {@link
 * PlayLevelSoundEvent.AtPosition} -- the seam NeoForge fires from inside {@code Level#playSound}
 * itself (see {@code PlayLevelSoundEvent}'s class javadoc), which is observable in a GameTest
 * regardless of whether any tracked player is around to actually receive the resulting sound
 * packet. Same shape as {@link SpawnCapture}, one event type over.
 *
 * <p>There is no equivalent seam for {@code ServerLevel#sendParticles}: NeoForge fires no
 * server-side event for it (only client-side rendering hooks), so a particle burst emitted
 * alongside a captured sound in the same method is verified by code inspection at the call site,
 * not by a GameTest assertion (issue #415).
 */
final class SoundCapture {

    private SoundCapture() {}

    record Played(Holder<SoundEvent> sound, Vec3 position, float volume, float pitch) {}

    /** Every sound played at a position inside this test's structure while {@code body} ran. */
    static List<Played> playedDuring(GameTestHelper helper, Runnable body) {
        List<Played> captured = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == helper.getLevel()
                    && helper.getBounds().inflate(2.0).contains(event.getPosition())) {
                captured.add(new Played(event.getSound(), event.getPosition(),
                        event.getNewVolume(), event.getNewPitch()));
            }
        };
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            body.run();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        return captured;
    }
}
