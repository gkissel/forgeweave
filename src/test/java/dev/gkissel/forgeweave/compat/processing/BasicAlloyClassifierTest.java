package dev.gkissel.forgeweave.compat.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Issue #995 (M8-11, docs/SCOPE.md D-M8-12): re-derives the "basic alloy" set straight from every
 * shipped {@code alloy_recipe} JSON, independently of {@code scripts/generate_compat_processing.py}'s
 * own hardcoded list -- this is what actually enforces the issue's own instruction to "confirm the
 * four against the shipped alloying recipes rather than trusting this list, and say in the PR if the
 * check turns up a fifth or drops one".
 *
 * <p><b>The rule, read literally off the issue text</b> ("an alloy qualifies as basic when it takes
 * ingot plus ingot with no catalyst fluid and no fuel material"): a recipe is basic when it has
 * exactly two inputs, and neither input is itself the {@code result} of another {@code alloy_recipe}
 * -- an "ingot", not an "alloy" -- and neither input is one of the fluids
 * {@link dev.gkissel.forgeweave.fluid.ForgeweaveFluids}' own class comments document as having no
 * ingot/nugget/block item of their own: the six smeltery-only catalysts, the two mined fuel-only
 * rungs, the fuel ladder's top rung, and the handful of fluids that "ride a shared texture" but are
 * explicitly "not a metal" in their own registration comment (obsidian, basalt, amethyst), plus the
 * non-metal vanilla-adjacent fluids (blood, clay, dirt, netherite scrap, carbon, quartz, magma cream)
 * that back one of the multi-catalyst recipes.
 *
 * <p>Applying that rule to the 34 files under {@code alloy_recipe/} today: {@code manyullyn} (cobalt +
 * ardite) and {@code rose_gold} (copper + gold) confirm the issue's own guess; {@code alumite} and
 * {@code pig_iron} drop out because both take three inputs, one of them a catalyst/fuel fluid
 * (obsidian; blood and clay), not "ingot plus ingot"; {@code embercast} (duskspar + ardite) and
 * {@code osmiridium} (osmium + iridium) are the two the issue's own guess missed -- both are
 * genuinely two-ingot, catalyst-free recipes, one Track B, one compat-gated. Net: still four, a
 * different four. See this class's own {@link #NON_INGOT_FLUIDS} for the exact denylist and why each
 * entry is on it.
 */
class BasicAlloyClassifierTest {

    private static final Path ALLOY_DIR =
            projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/alloy_recipe");

    /**
     * Fluids with no ingot/nugget/block item of their own, so an alloy_recipe naming one as an input
     * is never "ingot plus ingot" no matter how many inputs it has. Sourced from
     * {@code ForgeweaveFluids}' own class comments (the six smeltery-only catalysts and the two mined
     * fuel-only rungs plus the fuel ladder's top rung), the two fluids explicitly commented "not a
     * metal" (obsidian, basalt -- amethyst_bronze's own amethyst input is the third), and the
     * remaining non-metal vanilla-adjacent inputs the pig_iron/hepatizon/knightslime/queens_slime/
     * netherite recipes take (blood, clay, quartz, magma_cream, purple_slime, seared_stone,
     * netherite_scrap, carbon) plus water/lava (obsidian.json's own inputs).
     */
    private static final Set<String> NON_INGOT_FLUIDS = Set.of(
            // The 6 smeltery-only catalysts (ForgeweaveFluids, "no ingot/nugget/block item of their own").
            "flarealloy", "deepalloy", "sparkalloy", "redcinder", "pearlcinder", "ambercinder",
            // The mined fuel-only rungs and the ladder's top rung (same javadoc, same "no ingot" note).
            "magma", "brimspar", "pyrealloy",
            // Explicitly "not a metal" in their own registration comment.
            "obsidian", "basalt", "amethyst",
            // Non-metal vanilla-adjacent catalyst/fuel inputs with no ingot of their own.
            "blood", "clay", "quartz", "magma_cream", "purple_slime", "seared_stone",
            "netherite_scrap", "carbon", "water", "lava");

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private record ParsedRecipe(String outputId, List<String> inputIds) {}

    private static String materialId(String fluidId) {
        String path = fluidId.substring(fluidId.indexOf(':') + 1);
        return path.startsWith("molten_") ? path.substring("molten_".length()) : path;
    }

    private static List<ParsedRecipe> parseAll() throws IOException {
        List<ParsedRecipe> recipes = new ArrayList<>();
        try (Stream<Path> files = Files.list(ALLOY_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                String outputId = materialId(root.getAsJsonObject("result").get("fluid").getAsString());
                JsonArray inputsArray = root.getAsJsonArray("inputs");
                List<String> inputIds = new ArrayList<>();
                for (int i = 0; i < inputsArray.size(); i++) {
                    inputIds.add(materialId(inputsArray.get(i).getAsJsonObject().get("fluid").getAsString()));
                }
                recipes.add(new ParsedRecipe(outputId, inputIds));
            }
        }
        return recipes;
    }

    private static Set<String> classifyBasicAlloys(List<ParsedRecipe> recipes) {
        Set<String> alloyResults = new LinkedHashSet<>();
        for (ParsedRecipe recipe : recipes) {
            alloyResults.add(recipe.outputId());
        }
        Set<String> basic = new TreeSet<>();
        for (ParsedRecipe recipe : recipes) {
            if (recipe.inputIds().size() != 2) {
                continue;
            }
            boolean allIngots = recipe.inputIds().stream()
                    .noneMatch(id -> NON_INGOT_FLUIDS.contains(id) || alloyResults.contains(id));
            if (allIngots) {
                basic.add(recipe.outputId());
            }
        }
        return basic;
    }

    @Test
    void theShippedAlloyRecipesClassifyToExactlyFourBasicAlloys() throws IOException {
        List<ParsedRecipe> recipes = parseAll();
        Set<String> basic = classifyBasicAlloys(recipes);

        assertEquals(Set.of("manyullyn", "rose_gold", "embercast", "osmiridium"), basic,
                "the basic-alloy classifier, run over every shipped alloy_recipe file, must return "
                        + "exactly this set -- a change here is either a new catalyst-free two-ingot "
                        + "alloy (decide whether it also belongs in Create/IE/EnderIO) or a roster edit "
                        + "that needs scripts/generate_compat_processing.py updated alongside it");
    }
}
