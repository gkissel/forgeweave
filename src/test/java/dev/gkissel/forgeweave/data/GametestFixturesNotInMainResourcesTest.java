package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards against issue #927: the GameTest-only datapack fixtures under {@code
 * src/gametest/resources} leaking into every dev run (and the published jar) because they used to
 * be folded straight into {@code sourceSets.main}. They now live in their own {@code gametest}
 * source set, wired only into the {@code gameTestServer} run's mod binding (see build.gradle) --
 * this test fails if any of those files show up in {@code build/resources/main}, the folder every
 * other run (and the {@code jar} task) actually reads from.
 */
class GametestFixturesNotInMainResourcesTest {

    @Test
    void gametestResourcesAreAbsentFromMainResourcesOutput() throws IOException {
        Path root = LocalizationAuditTest.projectRoot();
        Path gametestResources = root.resolve("src/gametest/resources");
        Path mainResourcesOutput = root.resolve("build/resources/main");

        List<String> leaked = new ArrayList<>();
        try (Stream<Path> files = Files.walk(gametestResources)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path relative = gametestResources.relativize(file);
                if (Files.exists(mainResourcesOutput.resolve(relative))) {
                    leaked.add(relative.toString());
                }
            }
        }

        assertTrue(leaked.isEmpty(),
                "GameTest-only fixture(s) leaked into build/resources/main -- they must load only in "
                        + "the gameTestServer run, never runClient/runServer/runData or the published "
                        + "jar (see the gametest source set and mods wiring in build.gradle, issue #927):\n"
                        + String.join("\n", leaked));
    }
}
