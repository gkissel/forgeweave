package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Maintainer report 2026-09-19: the crafting station did not break easily with an axe. It, every other
 * wooden table, both chests, the wooden hopper and firewood were in no {@code mineable/*} tag, so
 * vanilla charged its no-correct-tool penalty whatever the player held. The same thing had happened
 * to the seared family on 2026-09-07.
 *
 * <p>This walks the generated data the way a player's game reads it: every block with a blockstate
 * file has to sit in one of the four {@code minecraft:mineable/*} tags (following {@code #forgeweave:}
 * tag references), unless its name matches {@link #TOOL_LESS}, the blocks vanilla leaves tool-less
 * too. A new block that fits neither fails here instead of in a playtest.
 */
class BlockToolCoverageTest {

    /** Glass, slime and plants: vanilla's own counterparts are in no mineable tag either. */
    private static final List<Pattern> TOOL_LESS = Stream.of(
            ".*clear_glass", ".*_slime_block", ".*_congealed_slime",
            ".*_slime_fern", ".*_slime_sapling", ".*_slime_tall_grass")
            .map(Pattern::compile).toList();

    private static final List<String> MINEABLE = List.of("axe", "pickaxe", "shovel", "hoe");

    private static final List<String> DATA_ROOTS =
            List.of("src/generated/resources/data", "src/main/resources/data");

    @Test
    void everyBlockHasAMiningToolOrIsDeliberatelyToolLess() throws IOException {
        Path root = LocalizationAuditTest.projectRoot();
        Set<String> covered = new HashSet<>();
        for (String tool : MINEABLE) {
            collect(root, "minecraft", "mineable/" + tool, covered, new HashSet<>());
        }

        List<String> uncovered = new ArrayList<>();
        for (String assets : List.of("src/generated/resources/assets", "src/main/resources/assets")) {
            Path states = root.resolve(assets).resolve("forgeweave/blockstates");
            if (!Files.isDirectory(states)) {
                continue;
            }
            try (Stream<Path> files = Files.list(states)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String name = file.getFileName().toString().replace(".json", "");
                    boolean toolLess = TOOL_LESS.stream().anyMatch(p -> p.matcher(name).matches());
                    if (!toolLess && !covered.contains("forgeweave:" + name)) {
                        uncovered.add(name);
                    }
                }
            }
        }
        assertTrue(uncovered.isEmpty(), "blocks in no minecraft:mineable/* tag and not listed as tool-less "
                + "(tag them in ForgeweaveBlockTagsProvider, or add a TOOL_LESS pattern with the vanilla "
                + "precedent): " + uncovered);
    }

    /** Adds every block id the tag names to {@code out}, following nested {@code #} references. */
    private static void collect(Path root, String namespace, String tagPath, Set<String> out, Set<String> seen)
            throws IOException {
        if (!seen.add(namespace + ":" + tagPath)) {
            return;
        }
        for (String data : DATA_ROOTS) {
            Path file = root.resolve(data).resolve(namespace).resolve("tags/block").resolve(tagPath + ".json");
            if (!Files.isRegularFile(file)) {
                continue;
            }
            for (JsonElement value : JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("values")) {
                String id = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
                if (id.startsWith("#")) {
                    String[] parts = id.substring(1).split(":", 2);
                    collect(root, parts[0], parts[1], out, seen);
                } else {
                    out.add(id);
                }
            }
        }
    }
}
