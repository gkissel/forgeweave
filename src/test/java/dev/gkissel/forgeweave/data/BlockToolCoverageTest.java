package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

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

        // Fluid blocks, like vanilla's water and lava: nothing mines a fluid. They have blockstates
        // since playtest 1.0.0 line 8's missing-model WARNs.
        Set<String> fluids = new HashSet<>();
        ForgeweaveFluids.all().forEach(fluid -> fluids.add(fluid.block().getId().getPath()));

        List<String> uncovered = new ArrayList<>();
        for (String assets : List.of("src/generated/resources/assets", "src/main/resources/assets")) {
            Path states = root.resolve(assets).resolve("forgeweave/blockstates");
            if (!Files.isDirectory(states)) {
                continue;
            }
            try (Stream<Path> files = Files.list(states)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String name = file.getFileName().toString().replace(".json", "");
                    boolean toolLess = fluids.contains(name)
                            || TOOL_LESS.stream().anyMatch(p -> p.matcher(name).matches());
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

    /**
     * Playtest 1.0.0 line 8: atomic matter alloy names {@code forgeweave:incorrect_for_resonite_tool},
     * which no data file defined, so every world load logged "Not all defined tags ... are present".
     * Every Forgeweave tag a material's {@code incorrect_for_tool} names needs a file, even an empty one.
     */
    @Test
    void everyIncorrectForToolTagAMaterialNamesIsDefined() throws IOException {
        Path root = LocalizationAuditTest.projectRoot();
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(root.resolve("src/main/resources/data/forgeweave/forgeweave/material"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonElement tag = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject().get("incorrect_for_tool");
                if (tag == null || !tag.getAsString().startsWith("forgeweave:")) {
                    continue;
                }
                String path = "forgeweave/tags/block/" + tag.getAsString().substring("forgeweave:".length()) + ".json";
                if (DATA_ROOTS.stream().noneMatch(data -> Files.isRegularFile(root.resolve(data).resolve(path)))) {
                    missing.add(tag.getAsString());
                }
            }
        }
        assertTrue(missing.isEmpty(), "material incorrect_for_tool tags with no tag file: " + missing);
    }

    /**
     * Playtest checklist 1.0.0, lines 38 to 40: which tool each named block breaks fast with. The test
     * above only proves a block sits in some mineable tag; this pins the right one, and that the block
     * is in no other. Line 38 is the wooden stations, both chests, the wooden hopper and firewood
     * (axe), line 39 the slime vines (axe) and the mud brick block (shovel), and line 40 the Tool
     * Forge and the Modifier Worktable (pickaxe). A tag check is the practical test for the vines,
     * because a loose vine doesn't stay placed long enough to time a break.
     */
    @Test
    void theChecklistsBlocksBreakWithTheirOwnTool() throws IOException {
        Map<String, String> expected = new LinkedHashMap<>();
        for (String block : List.of("crafting_station", "part_builder", "stencil_table", "tool_station",
                "part_chest", "pattern_chest", "wooden_hopper", "firewood",
                "blue_slime_vine", "blue_slime_vine_mid", "blue_slime_vine_end",
                "purple_slime_vine", "purple_slime_vine_mid", "purple_slime_vine_end")) {
            expected.put(block, "axe");
        }
        expected.put("mud_brick_block", "shovel");
        expected.put("tool_forge", "pickaxe");
        expected.put("modifier_worktable", "pickaxe");

        Path root = LocalizationAuditTest.projectRoot();
        Map<String, Set<String>> tagged = new LinkedHashMap<>();
        for (String tool : MINEABLE) {
            Set<String> blocks = new HashSet<>();
            collect(root, "minecraft", "mineable/" + tool, blocks, new HashSet<>());
            tagged.put(tool, blocks);
        }

        List<String> wrong = new ArrayList<>();
        expected.forEach((block, tool) -> {
            List<String> tools = MINEABLE.stream().filter(t -> tagged.get(t).contains("forgeweave:" + block)).toList();
            if (!tools.equals(List.of(tool))) {
                wrong.add(block + " should be in mineable/" + tool + " only, is in " + tools);
            }
        });
        assertTrue(wrong.isEmpty(), "blocks with the wrong mining tool: " + wrong);
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
