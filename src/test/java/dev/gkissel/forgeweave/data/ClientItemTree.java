package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads a generated 26.x client item ({@code assets/forgeweave/items/<item>.json}) as the list of
 * models it can end on, each with the conditions that lead there.
 *
 * <p>1.21.1 item models switched state through an {@code overrides} list, resolved as "the last
 * entry whose every predicate matches". 26.x replaced that with a tree: {@code minecraft:condition}
 * nodes (a boolean property, {@code on_true}/{@code on_false}) and {@code minecraft:range_dispatch}
 * nodes (a numeric property, threshold entries, a fallback). Walking every path to its leaf gives
 * the tests the same facts the override list did: which model a state draws and what has to be
 * true for it, and, because a condition tested nearer the root decides first, which state wins.
 */
final class ClientItemTree {

    private ClientItemTree() {}

    /**
     * One reachable model. {@code conditions} maps each boolean property tested on the way down to
     * the branch taken; {@code range} is the numeric property's threshold when the leaf is a
     * range_dispatch entry (null for its fallback or outside one).
     */
    record Leaf(Map<String, Boolean> conditions, Float range, String model) {
        /** The branch taken on {@code property}, or null if this path never tests it. */
        Boolean is(String property) {
            return conditions.get(property);
        }
    }

    static List<Leaf> leaves(Path projectRoot, String item) throws IOException {
        Path path = projectRoot.resolve("src/generated/resources/assets/forgeweave/items/" + item + ".json");
        assertTrue(Files.isRegularFile(path), "expected client item JSON at " + path);
        JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        List<Leaf> leaves = new ArrayList<>();
        walk(root.getAsJsonObject("model"), new HashMap<>(), null, leaves);
        return leaves;
    }

    /**
     * A leaf drawing {@code model}, or null. The idle model is legitimately reachable along more than
     * one path (a range_dispatch's fallback is the idle model too), so this answers the path that
     * takes the fewest true branches: the plain way to reach it.
     */
    static Leaf leafFor(List<Leaf> leaves, String model) {
        return leaves.stream().filter(leaf -> leaf.model().equals(model))
                .min(java.util.Comparator.comparingLong(leaf -> leaf.conditions().values().stream().filter(b -> b).count()))
                .orElse(null);
    }

    private static void walk(JsonObject node, Map<String, Boolean> conditions, Float range, List<Leaf> out) {
        String type = node.get("type").getAsString();
        switch (type) {
            case "minecraft:condition" -> {
                String property = node.get("property").getAsString();
                for (boolean branch : new boolean[] {true, false}) {
                    Map<String, Boolean> next = new HashMap<>(conditions);
                    next.put(property, branch);
                    walk(node.getAsJsonObject(branch ? "on_true" : "on_false"), next, range, out);
                }
            }
            case "minecraft:range_dispatch" -> {
                for (JsonElement entry : node.getAsJsonArray("entries")) {
                    JsonObject value = entry.getAsJsonObject();
                    walk(value.getAsJsonObject("model"), conditions, value.get("threshold").getAsFloat(), out);
                }
                if (node.has("fallback")) {
                    walk(node.getAsJsonObject("fallback"), conditions, null, out);
                }
            }
            default -> {
                assertTrue(node.has("model") && node.get("model").isJsonPrimitive(),
                        "client item leaf of type " + type + " names no model: " + node);
                out.add(new Leaf(Map.copyOf(conditions), range, node.get("model").getAsString()));
            }
        }
    }
}
