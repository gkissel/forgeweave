package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class CompatProviderCoverageTest {
    @Test
    @SuppressWarnings("unchecked")
    void bucketVisibilityRecognizesEveryProviderThatEnablesItsMaterial() throws Exception {
        var field = CompatMaterialAvailability.class.getDeclaredField("ANY_OF");
        field.setAccessible(true);
        var providers = (Map<String, List<ResourceLocation>>) field.get(null);
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("settings.gradle"))) {
            root = root.getParent();
        }
        Path materials = root.resolve("src/main/resources/data/forgeweave/forgeweave/material");
        for (var entry : providers.entrySet()) {
            JsonElement json = JsonParser.parseString(Files.readString(materials.resolve(entry.getKey() + ".json")));
            JsonElement conditions = json.getAsJsonObject().get("neoforge:conditions");
            if (conditions == null) {
                continue;
            }
            Set<String> expected = new HashSet<>();
            collect(conditions, expected);
            assertEquals(expected, entry.getValue().stream().map(ResourceLocation::toString)
                    .collect(java.util.stream.Collectors.toSet()), entry.getKey());
        }
    }

    private static void collect(JsonElement element, Set<String> items) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> collect(value, items));
        } else {
            var object = element.getAsJsonObject();
            if (object.has("item")) {
                items.add(object.get("item").getAsString());
            } else if (object.has("values")) {
                collect(object.get("values"), items);
            }
        }
    }
}
