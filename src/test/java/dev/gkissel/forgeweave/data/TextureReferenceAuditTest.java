package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolArt;

/**
 * Guards against the issue #43 regression (maintainer screenshots: missing-texture magenta/black
 * checkers on nearly every item after the {@code textures/derived/} reorganization). Scans every
 * block/item model JSON under {@code src/main/resources} and {@code src/generated/resources} for
 * {@code forgeweave:}-namespaced texture references, plus every hardcoded GUI background
 * {@link net.minecraft.resources.ResourceLocation} texture path in {@code src/main/java}, and
 * asserts (a) the referenced PNG actually exists on disk, and (b) it's actually stitched into the
 * block atlas -- since 1.19.3, block/item models can only render sprites the atlas knows about,
 * which by default is only {@code textures/block/} and {@code textures/item/}; every other
 * directory (like our {@code textures/derived/...} tree) needs an explicit {@code directory} source
 * in an atlas definition, or the model resolves to a real file that never gets stitched and renders
 * as a missing-texture checker anyway. This is intentionally a plain filesystem scan (no Minecraft
 * resource-pack loading, so it stays fast and catches stale paths without needing a running client)
 * rather than a scan of one hardcoded file list -- SCOPE.md's regression rule requires this kind of
 * defect to be automated, and a hardcoded list would silently stop covering new textures added
 * later.
 */
class TextureReferenceAuditTest {

    /**
     * Only matches literals passed to {@code ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, ...)}
     * -- the convention every forgeweave-owned GUI texture constant uses (see {@code PartBuilderScreen},
     * {@code ToolStationScreen}, {@code StencilTableScreen}, {@code InfoPanel}). {@code CraftingStationScreen}
     * (issue #40/#59) instead points at vanilla's own {@code textures/gui/container/crafting_table.png}
     * via {@code ResourceLocation.withDefaultNamespace(...)}, deliberately -- there is no forgeweave asset
     * to derive or audit for that path, so this pattern must not match it (or its javadoc mention).
     */
    private static final Pattern GUI_TEXTURE_LITERAL =
            Pattern.compile("fromNamespaceAndPath\\(Forgeweave\\.MODID,\\s*\"(textures/[^\"]+\\.png)\"");

    /**
     * Prefixes the block atlas stitches by default, with no {@code atlases/blocks.json} of our own
     * (vanilla's own {@code assets/minecraft/atlases/blocks.json} sources {@code block/} and
     * {@code item/} for every namespace's resource manager, not just {@code minecraft:}).
     */
    private static final Set<String> DEFAULT_ATLAS_PREFIXES = Set.of("block/", "item/");

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
        forEachForgeweaveModelTexture(root, (json, entry) -> {
            String path = entry.path();
            Path png = texturesRoot.resolve(path + ".png");
            if (!Files.isRegularFile(png)) {
                missing.add(json + " texture \"" + entry.key() + "\": \"" + entry.rawValue() + "\" -> " + png + " (missing)");
            }
        });

        assertTrue(missing.isEmpty(), "missing texture files referenced by model JSONs:\n" + String.join("\n", missing));
    }

    /**
     * The atlas-coverage counterpart to the file-existence check above: a texture can exist on disk
     * (see {@link #everyModelTextureReferenceResolvesToAnExistingFile}) and still never render,
     * because the block atlas only stitches sprites under a directory some {@code atlases/*.json}
     * source declares. See {@code assets/minecraft/atlases/blocks.json} for the {@code derived/...}
     * sources this project ships.
     */
    @Test
    void everyModelTextureReferenceIsCoveredByAnAtlasSource() throws IOException {
        Path root = projectRoot();
        Set<String> customPrefixes = readAtlasDirectoryPrefixes(root.resolve("src/main/resources/assets/minecraft/atlases/blocks.json"));

        List<String> uncovered = new ArrayList<>();
        forEachForgeweaveModelTexture(root, (json, entry) -> {
            boolean covered = DEFAULT_ATLAS_PREFIXES.stream().anyMatch(entry.path()::startsWith)
                    || customPrefixes.stream().anyMatch(entry.path()::startsWith);
            if (!covered) {
                uncovered.add(json + " texture \"" + entry.key() + "\": \"" + entry.rawValue()
                        + "\" is not under any known atlas source prefix (default block/, item/, or a "
                        + "directory source in assets/minecraft/atlases/blocks.json)");
            }
        });

        assertTrue(uncovered.isEmpty(),
                "model texture references not covered by any block-atlas source:\n" + String.join("\n", uncovered));
    }

    private record TextureEntry(String key, String rawValue, String path) {}

    /** Walks every {@code forgeweave:}-namespaced texture entry in every model JSON we ship or generate. */
    private void forEachForgeweaveModelTexture(Path root, BiConsumer<Path, TextureEntry> consumer) throws IOException {
        for (Path modelsDir : List.of(
                root.resolve("src/main/resources/assets/forgeweave/models"),
                root.resolve("src/generated/resources/assets/forgeweave/models"))) {
            if (!Files.isDirectory(modelsDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(modelsDir)) {
                for (Path json : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    forEachTextureEntry(json, entry -> consumer.accept(json, entry));
                }
            }
        }
    }

    private void forEachTextureEntry(Path json, java.util.function.Consumer<TextureEntry> consumer) throws IOException {
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
            consumer.accept(new TextureEntry(entry.getKey(), value, path));
        }
    }

    /** Reads every {@code {"type": "(minecraft:)directory", "prefix": "..."}} source's prefix, if the file exists. */
    private static Set<String> readAtlasDirectoryPrefixes(Path atlasJson) throws IOException {
        Set<String> prefixes = new HashSet<>();
        if (!Files.isRegularFile(atlasJson)) {
            return prefixes;
        }
        JsonObject root = JsonParser.parseString(Files.readString(atlasJson, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray sources = root.getAsJsonArray("sources");
        if (sources == null) {
            return prefixes;
        }
        for (JsonElement element : sources) {
            JsonObject source = element.getAsJsonObject();
            String type = source.has("type") ? source.get("type").getAsString() : "";
            if (!(type.equals("directory") || type.equals("minecraft:directory")) || !source.has("prefix")) {
                continue;
            }
            prefixes.add(source.get("prefix").getAsString());
        }
        return prefixes;
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

    /**
     * {@code ToolStationScreen} builds a part's ghost-icon path from the part item's registry id
     * ({@code textures/derived/item/<id>.png}, issue #47). That path is assembled at runtime, so the
     * literal scan above cannot see it; this is the guard that the id-equals-texture-name convention
     * holds for every part the station can ask a ghost for.
     */
    @Test
    void everyToolPartHasAGhostIconTexture() {
        Path items = projectRoot().resolve("src/main/resources/assets/forgeweave/textures/derived/item");
        for (String part : List.of("pickaxe_head", "shovel_head", "axe_head", "tool_binding", "tool_handle")) {
            assertTrue(Files.isRegularFile(items.resolve(part + ".png")),
                    "the Tool Station's ghost icon for forgeweave:" + part + " has no texture under " + items);
        }
    }

    /**
     * Every assembled-tool layer {@link ToolArt#layer} names has to actually exist under
     * {@code textures/derived/tools/}, a real atlas source ({@code assets/minecraft/atlases/blocks.json}),
     * or it renders as a missing-texture checker on the station's build tab, exactly the way issue #43
     * did. Through issue #198, three tools (the dagger, the scimitar, the katana) had no upstream
     * original to derive from and kept freshly-authored layers under a separate {@code textures/tools/}
     * folder instead; #198 replaced all three with derived art, and #279 sent the katana back (its
     * derived stand-in did not read as a katana at playtest, and unlike the scimitar -- which #279
     * matched to 1.12's unregistered cutlass art -- neither clone has any counterpart shape). So the
     * katana's layers live under {@code textures/tools/} and every other tool's under
     * {@code textures/derived/tools/}; this resolves whichever {@link ToolArt#layer} names, which is
     * the point of routing every caller through it.
     *
     * <p>Walked off {@code ToolAssemblyRecipes.ENTRIES} rather than listed, so a later new-shape tool
     * that forgets to ship a layer fails here instead of at a playtest.
     */
    @Test
    void everyToolLayerHasArt() {
        Path textures = projectRoot().resolve("src/main/resources/assets/forgeweave/textures");
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            if (entry.constants().category() == ToolConstants.Category.ARMOR) {
                continue; // #679 lands the armor render; #678 ships vanilla placeholder art
            }
            String tool = entry.constants().id();
            for (String layer : ToolArt.layers(entry.constants().parts())) {
                Path png = textures.resolve(ToolArt.layer(tool, layer) + ".png");
                assertTrue(Files.isRegularFile(png),
                        "tool layer art for forgeweave:" + tool + " layer '" + layer + "' is missing: " + png);
            }
        }
    }

    /**
     * Issue #217: every assembled tool's generated model must carry held-render display transforms,
     * not the flat {@code item/generated} defaults it shipped with (the maintainer's third-person
     * harness captures showed a near-invisible edge-on held item). The parity source is upstream
     * 1.12, whose tool models carry no {@code display} block and fall back to Mantle's
     * {@code DEFAULT_TOOL_STATE} -- numerically vanilla's {@code item/handheld} -- so inheriting that
     * parent is the check, and six tools additionally mirror upstream's own per-tool block -- the
     * three M3 ones plus, since M3.5 issue #400, the three bows, which upstream lays across the body
     * rather than swinging.
     *
     * <p>Walked off {@code ToolAssemblyRecipes.ENTRIES} so a tool added later cannot quietly go back
     * to a flat model.
     */
    @Test
    void everyToolModelInheritsTheHandheldDisplayTransforms() throws IOException {
        Path models = projectRoot().resolve("src/generated/resources/assets/forgeweave/models/item");
        Set<String> overriding = Set.of("cleaver", "rapier", "battlesign", "shortbow", "longbow", "crossbow");

        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            if (entry.constants().category() == ToolConstants.Category.ARMOR) {
                continue; // #679 lands the armor render; #678 ships vanilla placeholder art
            }
            String tool = entry.constants().id();
            Path json = models.resolve(tool + ".json");
            assertTrue(Files.isRegularFile(json), "no generated item model for forgeweave:" + tool + ": " + json);

            JsonObject model = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("minecraft:item/handheld", model.get("parent").getAsString(),
                    tool + " must inherit the handheld held-render transforms (issue #217)");

            boolean hasDisplay = model.has("display");
            assertEquals(overriding.contains(tool), hasDisplay,
                    tool + (hasDisplay ? " has a display override upstream 1.12 does not"
                            : " is missing the display override upstream 1.12 gives it"));
        }

        // The two upstream shapes the override exists for at all: the cleaver's oversized
        // third-person pose and the rapier's inverted point-forward yaw.
        JsonObject cleaver = JsonParser.parseString(
                Files.readString(models.resolve("cleaver.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray cleaverScale = cleaver.getAsJsonObject("display")
                .getAsJsonObject("thirdperson_righthand").getAsJsonArray("scale");
        assertEquals(1.5f, cleaverScale.get(0).getAsFloat(), "cleaver third-person scale (upstream cleaver.tcon.json)");
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
