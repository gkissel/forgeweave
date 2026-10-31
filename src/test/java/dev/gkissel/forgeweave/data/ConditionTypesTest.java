package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import dev.gkissel.forgeweave.config.ForgeweaveConfigCondition;

/**
 * Every {@code neoforge:conditions} entry in the shipped JSON names a condition type the NeoForge this
 * builds against (or Forgeweave itself) registers. The 26.1 port found 9313 {@code neoforge:item_exists}
 * conditions after NeoForge had renamed it to {@code neoforge:registered}; an unknown type fails the
 * whole file at load, so every gated material, recipe and alloy would quietly have gone missing.
 */
class ConditionTypesTest {

    private static final List<Path> ROOTS = List.of(
            Path.of("src/main/resources"), Path.of("src/generated/resources"), Path.of("src/gametest/resources"));

    @Test
    void everyConditionTypeIsRegistered() throws Exception {
        Set<String> known = registeredConditionTypes();
        assertTrue(known.contains("neoforge:mod_loaded") && known.contains("forgeweave:compat_toggle"),
                "reading the registered condition types went wrong: " + known);

        List<String> unknown = new ArrayList<>();
        for (Path root : ROOTS) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    try (Reader reader = Files.newBufferedReader(file)) {
                        collect(JsonParser.parseReader(reader), file, known, unknown);
                    }
                }
            }
        }
        assertTrue(unknown.isEmpty(), unknown.size() + " conditions of an unregistered type, e.g. "
                + unknown.subList(0, Math.min(10, unknown.size())));
    }

    /** The ids of NeoForge's own condition codec holders and Forgeweave's. */
    private static Set<String> registeredConditionTypes() throws IllegalAccessException {
        Set<String> known = new HashSet<>();
        for (Field field : NeoForgeMod.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.get(null) instanceof DeferredHolder<?, ?> holder
                    && holder.getKey().isFor(NeoForgeRegistries.Keys.CONDITION_CODECS)) {
                known.add(holder.getId().toString());
            }
        }
        ForgeweaveConfigCondition.CONDITION_CODECS.getEntries().forEach(holder -> known.add(holder.getId().toString()));
        return known;
    }

    private static void collect(JsonElement node, Path file, Set<String> known, List<String> unknown) throws IOException {
        if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) {
                collect(child, file, known, unknown);
            }
        } else if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            for (var entry : object.entrySet()) {
                if (entry.getKey().equals("neoforge:conditions") && entry.getValue().isJsonArray()) {
                    for (JsonElement condition : entry.getValue().getAsJsonArray()) {
                        check(condition, file, known, unknown);
                    }
                } else {
                    collect(entry.getValue(), file, known, unknown);
                }
            }
        }
    }

    /** One condition, and the conditions an {@code and}/{@code or}/{@code not} nests. */
    private static void check(JsonElement condition, Path file, Set<String> known, List<String> unknown) {
        if (!condition.isJsonObject() || !condition.getAsJsonObject().has("type")) {
            return;
        }
        JsonObject object = condition.getAsJsonObject();
        String type = object.get("type").getAsString();
        if (!known.contains(type)) {
            unknown.add(type + " in " + file);
        }
        for (String key : List.of("value", "values")) {
            JsonElement nested = object.get(key);
            if (nested != null && (nested.isJsonObject() || nested.isJsonArray())) {
                for (JsonElement child : nested.isJsonArray() ? nested.getAsJsonArray() : List.of(nested)) {
                    check(child, file, known, unknown);
                }
            }
        }
    }
}
