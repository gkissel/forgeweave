package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Captures entities spawned inside a test's own structure at the {@link EntityJoinLevelEvent} seam,
 * instead of reading them back out of the level's entity index with an AABB query.
 *
 * <p>Why not {@code getEntitiesOfClass}: the GameTest server scatters each run's whole plot grid at
 * a <em>random</em> position up to 15 million blocks out ({@code GameTestServer#startTests} rolls
 * {@code nextIntBetweenInclusive(-14999992, 14999992)} on the level's unseeded random), and
 * {@code StructureUtils#forceLoadChunks} then force-loads only the chunks the test's own structure
 * box intersects. Whether anything a test does a few blocks out lands inside those chunks therefore
 * depends on where the grid happened to fall -- a fresh roll every run. Outside them the entity
 * index serves nobody and nothing ticks at all, which is the whole family of rotating CI-only
 * failures this repo has chased as issues #212/#216, PR #249 ("a beheaded player must drop a player
 * head") and #643 (a thrown shuriken sitting at its spawn point with {@code tickCount == 0}).
 * {@link EntityJoinLevelEvent} fires synchronously inside {@code Level#addFreshEntity}, so a capture
 * around the spawning call is deterministic no matter where the grid landed.
 *
 * <p>The other half of #643's fix is the {@code empty} template's size: at 1x1x1 it force-loaded one
 * chunk and every test working outside it was rolling dice, so it is sized to the area these tests
 * actually use. A test that reaches further than the template still has to expect nothing there to
 * tick.
 */
final class SpawnCapture {

    private SpawnCapture() {}

    /**
     * A {@code thenWaitUntil} condition: fails (and is retried next tick) until the level's entity
     * index serves every one of {@code entities}.
     *
     * <p>For tests whose asserted <em>production</em> behavior queries the index itself -- magnetic's
     * item pull, the sweep seams' AoE lookup, the smeltery's advancement radius -- capturing at the
     * spawn seam is not enough: the shipped code runs its own {@code getEntitiesOfClass}, so
     * triggering it in the same tick the actors spawned races the async registration described above
     * and quietly finds nobody (PR #249's fourth CI flake, magnetic's "got (0.0, 0.0, 0.0)"). Wait
     * on this first, then trigger the behavior. Pair it with a generous {@code timeoutTicks} -- the
     * ticks race wall-clock, so the budget is what buys the chunk system real time.
     */
    static void assertIndexServes(GameTestHelper helper, Entity... entities) {
        AABB box = entities[0].getBoundingBox();
        for (Entity entity : entities) {
            box = box.minmax(entity.getBoundingBox());
        }
        List<Entity> seen = helper.getLevel().getEntitiesOfClass(Entity.class, box.inflate(1.0));
        for (Entity entity : entities) {
            helper.assertTrue(seen.contains(entity),
                    "the entity index does not yet serve " + entity.getType() + " at " + entity.position());
        }
    }

    /** Every {@code type} entity that joined this test's level inside its structure while {@code body} ran. */
    static <T extends Entity> List<T> spawnedDuring(GameTestHelper helper, Class<T> type, Runnable body) {
        return spawnedDuring(helper, type, helper.getBounds().inflate(2.0), body);
    }

    /**
     * The same, over an explicit box, for a test that wants a tighter or differently-centred window
     * than its whole structure -- arrows near the shooting player, a mob's drops around where it
     * died -- rather than the structure bounds the no-box form uses.
     */
    static <T extends Entity> List<T> spawnedDuring(GameTestHelper helper, Class<T> type, AABB bounds, Runnable body) {
        List<T> captured = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (type.isInstance(event.getEntity()) && event.getLevel() == helper.getLevel()
                    && bounds.contains(event.getEntity().position())) {
                captured.add(type.cast(event.getEntity()));
            }
        };
        // LOWEST + receiveCanceled, because a cancelled join is still a spawn this test caused:
        // NeoForge's hasCustomEntity hook cancels the vanilla item entity of every dropped tool and
        // part (#599's IndestructibleItemEntity swap) and re-adds a replacement a tick later, so a
        // plain listener sees none of those drops at all -- it saw one item where a Part Chest had
        // just spilled four (#643).
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, listener);
        try {
            body.run();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        return captured;
    }
}
