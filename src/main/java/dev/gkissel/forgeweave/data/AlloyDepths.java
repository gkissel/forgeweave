package dev.gkissel.forgeweave.data;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;

/**
 * How many alloying steps deep each alloy sits, read at datagen time off the shipped {@link
 * AlloyRecipe} JSON rather than written out by hand (issue #1106). {@link
 * ForgeweaveItemTagsProvider} turns the answer into the {@code forgeweave:alloys/deep} and {@code
 * forgeweave:alloys/deepest} item tags the alloy advancements key on, so rebalancing a recipe moves
 * the advancement with it instead of leaving it pointing at a metal that is no longer an endgame
 * one.
 *
 * <p>Depth is the number of alloying steps on the <em>cheapest</em> route to a metal: an alloy whose
 * inputs are all melted directly from an item is depth 1, one that needs a depth-1 alloy as an input
 * is depth 2, and so on. Where a metal has more than one recipe (the {@code _alt1} files) the
 * shallower route wins, because that is the one a player will actually take. A fluid no {@code
 * alloy_recipe} produces is depth 0, and a recipe cycle is read as depth 0 on the edge that closes
 * it -- {@link AlloyRecipe}'s own parse-time rules already refuse the single-recipe case, and
 * nothing shipped forms a longer loop, so this is a guard rather than a behaviour.
 *
 * <p>Datagen only. The directory is read straight off the classpath, which is a plain directory in
 * every dev run {@code runData} happens in.
 */
final class AlloyDepths {
    private static final String DIRECTORY = "/data/" + Forgeweave.MODID + "/" + Forgeweave.MODID + "/alloy_recipe";
    private static final String FLUID_PREFIX = "molten_";

    private AlloyDepths() {}

    /**
     * Every alloy the shipped recipes produce, by material id (the fluid path without its {@code
     * molten_} prefix, so {@code forgeweave:molten_truesteel} reads as {@code truesteel}), mapped to
     * its depth.
     */
    static Map<String, Integer> byMaterial() {
        Map<String, List<List<String>>> recipes = readRecipes();
        Map<String, Integer> depths = new TreeMap<>();
        recipes.keySet().forEach(alloy -> depths.put(alloy, depth(alloy, recipes, new HashSet<>())));
        return depths;
    }

    private static int depth(String material, Map<String, List<List<String>>> recipes, Set<String> onPath) {
        List<List<String>> routes = recipes.get(material);
        if (routes == null || !onPath.add(material)) {
            return 0;
        }
        int cheapest = Integer.MAX_VALUE;
        for (List<String> inputs : routes) {
            int deepestInput = 0;
            for (String input : inputs) {
                deepestInput = Math.max(deepestInput, depth(input, recipes, onPath));
            }
            cheapest = Math.min(cheapest, deepestInput + 1);
        }
        onPath.remove(material);
        return cheapest;
    }

    /** Result material id to the input material ids of each recipe that produces it. */
    private static Map<String, List<List<String>>> readRecipes() {
        Map<String, List<List<String>>> recipes = new HashMap<>();
        try (Stream<Path> files = Files.list(directory())) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject json = parse(file);
                List<String> inputs = new ArrayList<>();
                json.getAsJsonArray("inputs").forEach(input -> inputs.add(material(input)));
                recipes.computeIfAbsent(material(json.get("result")), key -> new ArrayList<>()).add(inputs);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the alloy recipes under " + DIRECTORY, e);
        }
        return recipes;
    }

    private static JsonObject parse(Path file) throws IOException {
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String material(JsonElement fluidAmount) {
        String fluid = fluidAmount.getAsJsonObject().get("fluid").getAsString();
        String path = fluid.substring(fluid.indexOf(':') + 1);
        return path.startsWith(FLUID_PREFIX) ? path.substring(FLUID_PREFIX.length()) : path;
    }

    private static Path directory() {
        URL url = AlloyDepths.class.getResource(DIRECTORY);
        if (url == null) {
            throw new IllegalStateException(DIRECTORY + " is not on the classpath");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(DIRECTORY + " is not a readable directory: " + url, e);
        }
    }
}
