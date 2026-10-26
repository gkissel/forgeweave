package dev.gkissel.forgeweave.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Playtest 1.0.0 line 8: with Powah and Apotheosis absent, their data maps in the mod's own
 * {@code data/} folder logged a WARN on every load, because NeoForge warns about an unregistered
 * data map type before it reads any condition (see {@link CompatDataPacks}). Only data map types
 * that always exist may live there; another mod's go in that mod's own built-in pack.
 */
class CompatDataPacksTest {

    /** Data map namespaces that are always registered when Forgeweave runs. */
    private static final Set<String> ALWAYS_PRESENT = Set.of("forgeweave", "minecraft", "neoforge");

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    @Test
    void theModsOwnDataFolderShipsNoDataMapForAnOptionalMod() throws IOException {
        Set<String> stray = new TreeSet<>();
        for (String tree : List.of("src/main/resources/data", "src/generated/resources/data")) {
            Path data = projectRoot().resolve(tree);
            if (!Files.isDirectory(data)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(data)) {
                files.filter(file -> file.toString().replace('\\', '/').contains("/data_maps/"))
                        .filter(Files::isRegularFile)
                        .filter(file -> !ALWAYS_PRESENT.contains(data.relativize(file).getName(0).toString()))
                        .forEach(file -> stray.add(data.relativize(file).toString()));
            }
        }
        assertTrue(stray.isEmpty(), "these data maps warn whenever their mod is absent; move each into "
                + "datapacks/<mod>/ and list the mod in CompatDataPacks.MODS: " + stray);
    }

    @Test
    void everyListedModHasAPackAndEveryPackIsListed() throws IOException {
        Path packs = projectRoot().resolve("src/main/resources/datapacks");
        Set<String> shipped = new TreeSet<>();
        try (Stream<Path> dirs = Files.list(packs)) {
            dirs.filter(dir -> Files.isRegularFile(dir.resolve("pack.mcmeta")))
                    .forEach(dir -> shipped.add(dir.getFileName().toString()));
        }
        assertEquals(new TreeSet<>(CompatDataPacks.MODS), shipped);
    }
}
