package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.projectile.AbstractArrow;

import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.registries.DeferredHolder;

import dev.gkissel.forgeweave.entity.ArrowEntity;
import dev.gkissel.forgeweave.entity.ForgeweaveEntities;
import dev.gkissel.forgeweave.entity.ShurikenEntity;

/**
 * Regression for issue #697: the thrown shuriken and the fired material arrow were invisible
 * in-game. Coverage-shaped, like {@code ItemColorCoverageTest}: every entity type
 * {@code ForgeweaveEntities} registers must get a renderer in
 * {@code ForgeweaveBlockEntityRenderers}, and every projectile whose renderer draws its carried
 * stack must ship that stack to the client -- vanilla never syncs
 * {@code AbstractArrow#pickupItemStack}, so without spawn data the client draws air.
 */
class EntityRendererCoverageTest {

    private static final String RENDERERS = "src/main/java/dev/gkissel/forgeweave/client/ForgeweaveBlockEntityRenderers.java";

    /** {@code LocalizationAuditTest#projectRoot}'s walk-up; the test JVM does not start at the project root. */
    private static Path projectRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve("settings.gradle"))) {
                return dir;
            }
        }
        throw new AssertionError("no settings.gradle above " + Path.of("").toAbsolutePath());
    }

    @Test
    void everyRegisteredEntityTypeGetsARenderer() throws IOException {
        String source = Files.readString(projectRoot().resolve(RENDERERS));
        List<String> missing = Arrays.stream(ForgeweaveEntities.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == DeferredHolder.class)
                .map(field -> field.getName())
                .filter(name -> !source.contains("registerEntityRenderer(ForgeweaveEntities." + name + ".get()"))
                .toList();
        assertTrue(missing.isEmpty(), () -> "entity types without a renderer registration (they render nothing): " + missing);
    }

    /** The renderers draw {@code getPickupItemStackOrigin()}, which only reaches the client through spawn data. */
    @Test
    void everyStackCarryingProjectileShipsItsStackInSpawnData() {
        for (Class<? extends AbstractArrow> entity : List.of(ShurikenEntity.class, ArrowEntity.class)) {
            assertTrue(IEntityWithComplexSpawn.class.isAssignableFrom(entity),
                    entity.getSimpleName() + " must implement IEntityWithComplexSpawn or its renderer draws air (#697)");
        }
    }
}
