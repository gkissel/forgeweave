package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * Guards issue #257's modifier-overlay art the way {@code TextureReferenceAuditTest} guards the tool
 * layers: {@link ModifierArt}'s declared overlay set and the files under
 * {@code textures/derived/tools/mods/} must describe each other exactly, in both directions, so a
 * future modifier (or tool) cannot point the renderer at art that was never derived -- and a stray
 * file cannot sit on disk with a name no modifier will ever resolve. Rendering correctness itself is
 * the screenshot harness's release-checklist job ({@code weapon_broadsword_modified*.png}).
 */
class ModifierArtTest {

    private static Path projectRoot() {
        for (Path candidate = Path.of("").toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root");
    }

    private static Path texturesRoot() {
        return projectRoot().resolve("src/main/resources/assets/forgeweave/textures");
    }

    /**
     * Walked off {@code ToolAssemblyRecipes.ENTRIES} x {@link ModifierArt#OVERLAY_MODIFIERS}, like
     * {@code TextureReferenceAuditTest#everyToolLayerHasArt}, so a new tool or a new overlay-bearing
     * modifier fails here until its overlays are derived (scripts/derive_modifier_overlays.py).
     */
    @Test
    void everyToolHasEveryDeclaredModifierOverlay() {
        List<String> missing = new ArrayList<>();
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            String tool = entry.constants().id();
            for (ResourceLocation modifier : ModifierArt.OVERLAY_MODIFIERS) {
                String texture = ModifierArt.overlay(tool, modifier);
                if (texture == null) {
                    continue; // a pair upstream ships no art for (ModifierArt#NO_UPSTREAM_ART)
                }
                Path png = texturesRoot().resolve(texture + ".png");
                if (!Files.isRegularFile(png)) {
                    missing.add(tool + " x " + modifier + " -> " + png);
                }
            }
        }
        assertTrue(missing.isEmpty(), "modifier overlays with no derived art:\n" + String.join("\n", missing));
    }

    /**
     * The same walk over the bows' draw stages (M3.5 issue #400): whatever
     * {@link ModifierArt#overlay(String, ResourceLocation, int)} resolves for a stage has to exist,
     * whether that is a staged file or the fallback to the undrawn one.
     */
    @Test
    void everyDrawStageOverlayResolvesToDerivedArt() {
        List<String> missing = new ArrayList<>();
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            String tool = entry.constants().id();
            for (ResourceLocation modifier : ModifierArt.OVERLAY_MODIFIERS) {
                for (int stage = 0; stage <= ToolArt.DRAW_STAGES; stage++) {
                    String texture = ModifierArt.overlay(tool, modifier, stage);
                    if (texture == null) {
                        continue;
                    }
                    Path png = texturesRoot().resolve(texture + ".png");
                    if (!Files.isRegularFile(png)) {
                        missing.add(tool + " x " + modifier + " @ stage " + stage + " -> " + png);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "draw-stage modifier overlays with no derived art:\n"
                + String.join("\n", missing));
    }

    /** The reverse direction: no orphaned art with a name the renderer can never resolve. */
    @Test
    void everyOverlayFileBelongsToARealToolAndModifier() throws IOException {
        Set<String> expected = ToolAssemblyRecipes.ENTRIES.stream()
                .flatMap(entry -> ModifierArt.OVERLAY_MODIFIERS.stream()
                        .flatMap(modifier -> IntStream.rangeClosed(0, ToolArt.DRAW_STAGES)
                                .mapToObj(stage -> ModifierArt.overlay(entry.constants().id(), modifier, stage))))
                .filter(java.util.Objects::nonNull)
                .map(texture -> texture.substring(texture.lastIndexOf('/') + 1) + ".png")
                .collect(Collectors.toSet());
        Path mods = texturesRoot().resolve("derived/tools/mods");
        try (Stream<Path> files = Files.list(mods)) {
            List<String> orphans = files.map(file -> file.getFileName().toString())
                    .filter(name -> !expected.contains(name))
                    .sorted()
                    .toList();
            assertTrue(orphans.isEmpty(), "files under " + mods + " no (tool, modifier) pair resolves:\n"
                    + String.join("\n", orphans));
        }
    }

    /** The Forgeweave-original modifiers deliberately draw nothing (issue #257 maintainer table). */
    @Test
    void forgeweaveOriginalModifiersHaveNoOverlay() {
        for (String original : List.of("searing", "magnetic_pull", "aquadynamic", "resonant",
                "far_reach", "extra_slot", "wind_burst")) {
            assertNull(ModifierArt.overlay("broadsword",
                    ResourceLocation.fromNamespaceAndPath("forgeweave", original)),
                    original + " should have no overlay art");
        }
    }
}
