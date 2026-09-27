package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolArt;

class MaterialFinishAssetsTest {
    private static final Set<String> ARMOR = Set.of("helmet", "chestplate", "leggings", "boots");
    private static final Set<String> DRAW_TOOLS = Set.of("shortbow", "longbow", "crossbow");

    @Test
    void everyRegisteredMaterialHasEverySelectedToolLayer() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) {
            root = root.getParent();
        }
        assertNotNull(root, "project root missing");
        Path materials = root.resolve("src/main/resources/data/forgeweave/forgeweave/material");
        Path finishes = root.resolve("src/main/resources/assets/forgeweave/textures/material_finishes");
        Set<String> parts;
        try (Stream<Path> files = Files.list(finishes.resolve("parts/wood"))) {
            parts = files.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
        assertEquals(37, parts.size(), "all loose part item sprites must be covered");
        try (Stream<Path> files = Files.list(materials)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String material = file.getFileName().toString().replace(".json", "");
                for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
                    String tool = entry.constants().id();
                    if (ARMOR.contains(tool) || tool.startsWith("heavy_")) {
                        continue;
                    }
                    for (String layer : ToolArt.layers(entry.constants().parts())) {
                        Path png = finishes.resolve(tool).resolve(material).resolve(layer + ".png");
                        assertTrue(Files.isRegularFile(png), "missing finish: " + png);
                        if (DRAW_TOOLS.contains(tool)) {
                            for (int stage = 1; stage <= 3; stage++) {
                                Path drawn = finishes.resolve(tool).resolve(material)
                                        .resolve(layer + "_draw" + stage + ".png");
                                assertTrue(Files.isRegularFile(drawn), "missing draw finish: " + drawn);
                            }
                        }
                    }
                }
                for (String part : parts) {
                    Path png = finishes.resolve("parts").resolve(material).resolve(part);
                    assertTrue(Files.isRegularFile(png), "missing part finish: " + png);
                }
            }
        }
        BufferedImage image = ImageIO.read(finishes.resolve("broadsword/wood/head.png").toFile());
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
    }
}
