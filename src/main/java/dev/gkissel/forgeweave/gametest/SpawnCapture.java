package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Captures entities spawned inside a test's own structure at the {@link EntityJoinLevelEvent} seam,
 * instead of reading them back out of the level's entity index with an AABB query.
 *
 * <p>Why not {@code getEntitiesOfClass}: the GameTest server scatters each run's whole plot grid at
 * a <em>random</em> position up to 15 million blocks out ({@code GameTestServer#startTests} rolls
 * {@code nextIntBetweenInclusive(-14999992, 14999992)} on the level's unseeded random), so every
 * plot sits in freshly force-loaded chunks whose full-status promotion and entity-section
 * registration run asynchronously. On a loaded runner a just-spawned entity can stay invisible to
 * entity queries for several ticks -- the repeated CI-only flake this repo has hit as issues
 * #212/#216 and PR #249 ("a beheaded player must drop a player head": the head had spawned, the
 * index just could not see it yet). {@link EntityJoinLevelEvent} fires synchronously inside
 * {@code Level#addFreshEntity}, so a capture around the spawning call is deterministic no matter
 * where the grid landed or how far behind the chunk system is.
 */
final class SpawnCapture {

    private SpawnCapture() {}

    /** Every {@code type} entity that joined this test's level inside its structure while {@code body} ran. */
    static <T extends Entity> List<T> spawnedDuring(GameTestHelper helper, Class<T> type, Runnable body) {
        List<T> captured = new ArrayList<>();
        AABB bounds = helper.getBounds().inflate(2.0);
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (type.isInstance(event.getEntity()) && event.getLevel() == helper.getLevel()
                    && bounds.contains(event.getEntity().position())) {
                captured.add(type.cast(event.getEntity()));
            }
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, listener);
        try {
            body.run();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        return captured;
    }
}
