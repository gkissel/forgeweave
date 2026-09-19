package dev.gkissel.forgeweave.fluid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Every {@code forgeweave:}-namespaced fluid id a shipped datapack file names must actually be a
 * registered {@link net.minecraft.world.level.material.Fluid} (issue #1059, repairing #1031).
 * {@code ferricore}, {@code blazegold} and {@code eclipsealloy} shipped {@code cast_only} material
 * JSON plus melting and casting rows naming {@code forgeweave:molten_ferricore}, {@code
 * molten_blazegold} and {@code molten_eclipsealloy} -- none of which {@link ForgeweaveFluids} ever
 * registered. {@code neoforge:conditions} hid every one of those rows from a GameTest server (Just
 * Dire Things is not a build/test dependency, so the condition never passes), which is exactly why
 * nothing caught it: a decode failure that only happens when the partner mod is actually installed
 * is invisible to every test that runs without it. This test does not need the partner mod --
 * it checks the shipped <em>string</em> against the real fluid registry, independent of whether any
 * particular row's condition would pass.
 *
 * <p>Walks every {@code .json} file under both data trees ({@code src/main/resources/data}, hand
 * written, and {@code src/generated/resources/data}, datagen output) and collects a
 * {@code forgeweave:}-namespaced fluid id from each of the three shapes a fluid id actually appears
 * in this codebase, structurally rather than by a blind string scan (a regex over every
 * {@code "forgeweave:molten_*"}-looking string would also catch non-fluid ids like a molten bucket
 * item, e.g. {@code forgeweave:molten_iron_bucket}):
 *
 * <ul>
 *   <li>A {@code melting_recipe}/{@code casting_recipe} JSON's top-level {@code fluid} field, and a
 *       melting recipe's optional {@code byproduct.fluid}.
 *   <li>A fluid tag file's ({@code .../tags/fluid/*.json}) {@code values} array entries.
 *   <li>A fluid data map file's ({@code .../data_maps/fluid/*.json}) {@code values} object keys
 *       (Powah's {@code heat_source} data map, D-M8-13).
 * </ul>
 *
 * <p>Resolves every collected id against {@link BuiltInRegistries#FLUID} directly -- the real
 * registry every {@link ForgeweaveFluids#register} call populates -- rather than a hand-written id
 * list, so a future fluid-reference gap fails here without this test needing an update.
 */
class FluidReferenceAuditTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyReferencedForgeweaveFluidIsRegistered() throws IOException {
        Path root = projectRoot();
        Set<String> referenced = new LinkedHashSet<>();
        for (Path dataDir : List.of(root.resolve("src/main/resources/data"), root.resolve("src/generated/resources/data"))) {
            if (!Files.isDirectory(dataDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dataDir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    collectFluidReferences(file, referenced);
                }
            }
        }

        assertFalse(referenced.isEmpty(), "expected to find at least one forgeweave: fluid reference across both data trees");

        List<String> unregistered = new ArrayList<>();
        for (String id : referenced) {
            ResourceLocation location = ResourceLocation.parse(id);
            if (!BuiltInRegistries.FLUID.containsKey(location)) {
                unregistered.add(id);
            }
        }

        assertTrue(unregistered.isEmpty(), "these " + Forgeweave.MODID + " fluid ids are named by a recipe, fluid tag or "
                + "data map but never registered in ForgeweaveFluids: " + unregistered);
    }

    private static void collectFluidReferences(Path file, Set<String> out) throws IOException {
        String pathText = file.toString().replace('\\', '/');
        JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        if (!root.isJsonObject()) {
            return;
        }
        JsonObject json = root.getAsJsonObject();

        if (pathText.contains("/melting_recipe/") || pathText.contains("/casting_recipe/")) {
            addIfForgeweave(json.get("fluid"), out);
            JsonElement byproduct = json.get("byproduct");
            if (byproduct != null && byproduct.isJsonObject()) {
                addIfForgeweave(byproduct.getAsJsonObject().get("fluid"), out);
            }
            return;
        }

        if (pathText.contains("/tags/fluid/")) {
            JsonElement values = json.get("values");
            if (values != null && values.isJsonArray()) {
                for (JsonElement entry : values.getAsJsonArray()) {
                    addIfForgeweave(tagEntryId(entry), out);
                }
            }
            return;
        }

        if (pathText.contains("/data_maps/fluid/")) {
            JsonElement values = json.get("values");
            if (values != null && values.isJsonObject()) {
                for (String key : values.getAsJsonObject().keySet()) {
                    addIfForgeweave(key, out);
                }
            }
        }
    }

    /** A tag `values` entry is either a plain string id or {@code {"id": "...", "required": ...}}. */
    private static String tagEntryId(JsonElement entry) {
        if (entry.isJsonPrimitive()) {
            return entry.getAsString();
        }
        if (entry.isJsonObject() && entry.getAsJsonObject().has("id")) {
            return entry.getAsJsonObject().get("id").getAsString();
        }
        return null;
    }

    private static void addIfForgeweave(JsonElement element, Set<String> out) {
        if (element != null && element.isJsonPrimitive()) {
            addIfForgeweave(element.getAsString(), out);
        }
    }

    private static void addIfForgeweave(String id, Set<String> out) {
        if (id != null && id.startsWith(Forgeweave.MODID + ":")) {
            out.add(id);
        }
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }
}
