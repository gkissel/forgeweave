package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Guards against the issue #43 regression (maintainer screenshots: missing-texture magenta/black
 * checkers on nearly every item after the {@code textures/derived/} reorganization). Scans every
 * block/item model JSON under {@code src/main/resources} and {@code src/generated/resources} for
 * {@code forgeweave:}-namespaced texture references, plus every hardcoded GUI background
 * {@link net.minecraft.resources.ResourceLocation} texture path in {@code src/main/java}, and
 * asserts the referenced PNG actually exists. This is intentionally a plain filesystem scan (no
 * Minecraft resource-pack loading, so it stays fast and catches stale paths without needing a
 * running client) rather than a scan of one hardcoded file list -- SCOPE.md's regression rule
 * requires this kind of defect to be automated, and a hardcoded list would silently stop covering
 * new textures added later.
 */
class TextureReferenceAuditTest {

    private static final Pattern TEXTURE_ENTRY = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern GUI_TEXTURE_LITERAL = Pattern.compile("\"(textures/[^\"]+\\.png)\"");

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
    void everyModelTextureReferenceResolvesToAnExistingFile() throws IOException {
        Path root = projectRoot();
        Path texturesRoot = root.resolve("src/main/resources/assets/forgeweave/textures");

        List<String> missing = new ArrayList<>();
        for (Path modelsDir : List.of(
                root.resolve("src/main/resources/assets/forgeweave/models"),
                root.resolve("src/generated/resources/assets/forgeweave/models"))) {
            if (!Files.isDirectory(modelsDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(modelsDir)) {
                for (Path json : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    checkModelTextures(json, texturesRoot, missing);
                }
            }
        }

        assertTrue(missing.isEmpty(), "missing texture files referenced by model JSONs:\n" + String.join("\n", missing));
    }

    private void checkModelTextures(Path json, Path texturesRoot, List<String> missing) throws IOException {
        String content = Files.readString(json, StandardCharsets.UTF_8);
        JsonElement root = JsonParser.parseString(content);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("textures")) {
            return;
        }
        JsonObject textures = root.getAsJsonObject().getAsJsonObject("textures");
        for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
            String value = entry.getValue().getAsString();
            if (value.startsWith("#")) {
                continue; // reference to another texture variable, not a file
            }
            String namespace = "forgeweave";
            String path = value;
            if (value.contains(":")) {
                String[] parts = value.split(":", 2);
                namespace = parts[0];
                path = parts[1];
            }
            if (!namespace.equals("forgeweave")) {
                continue; // vanilla/other-mod textures aren't ours to audit
            }
            Path png = texturesRoot.resolve(path + ".png");
            if (!Files.isRegularFile(png)) {
                missing.add(json + " texture \"" + entry.getKey() + "\": \"" + value + "\" -> " + png + " (missing)");
            }
        }
    }

    @Test
    void everyHardcodedGuiTextureLiteralResolvesToAnExistingFile() throws IOException {
        Path root = projectRoot();
        Path assetsRoot = root.resolve("src/main/resources/assets/forgeweave");
        Path javaDir = root.resolve("src/main/java");

        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(javaDir)) {
            for (Path java : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String content = Files.readString(java, StandardCharsets.UTF_8);
                Matcher matcher = GUI_TEXTURE_LITERAL.matcher(content);
                while (matcher.find()) {
                    String relative = matcher.group(1);
                    Path png = assetsRoot.resolve(relative);
                    if (!Files.isRegularFile(png)) {
                        missing.add(java + " literal \"" + relative + "\" -> " + png + " (missing)");
                    }
                }
            }
        }

        assertTrue(missing.isEmpty(), "missing texture files referenced by hardcoded GUI literals:\n" + String.join("\n", missing));
    }

    /** Sanity check that the scan actually exercises the GUI textures the regression was about. */
    @Test
    void guiTextureLiteralScanFindsTheStationBackgrounds() throws IOException {
        Path javaDir = projectRoot().resolve("src/main/java");
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(javaDir)) {
            for (Path java : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = GUI_TEXTURE_LITERAL.matcher(Files.readString(java, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    found.add(matcher.group(1));
                }
            }
        }
        if (found.stream().noneMatch(f -> f.contains("part_builder")) || found.stream().noneMatch(f -> f.contains("tool_station"))) {
            fail("expected to find both station GUI texture literals, found: " + found);
        }
    }
}
