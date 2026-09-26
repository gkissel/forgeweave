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
    private static final Set<String> TOOLS = Set.of("broadsword", "pickaxe", "warmace");

    @Test
    void everyRegisteredMaterialHasEverySelectedToolLayer() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) {
            root = root.getParent();
        }
        assertNotNull(root, "project root missing");
        Path materials = root.resolve("src/main/resources/data/forgeweave/forgeweave/material");
        Path finishes = root.resolve("src/main/resources/assets/forgeweave/textures/material_finishes");
        try (Stream<Path> files = Files.list(materials)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String material = file.getFileName().toString().replace(".json", "");
                for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
                    String tool = entry.constants().id();
                    if (!TOOLS.contains(tool)) {
                        continue;
                    }
                    for (String layer : ToolArt.layers(entry.constants().parts())) {
                        Path png = finishes.resolve(tool).resolve(material).resolve(layer + ".png");
                        assertTrue(Files.isRegularFile(png), "missing finish: " + png);
                        BufferedImage image = ImageIO.read(png.toFile());
                        assertNotNull(image, "invalid finish: " + png);
                        assertEquals(16, image.getWidth(), png.toString());
                        assertEquals(16, image.getHeight(), png.toString());
                    }
                }
            }
        }
    }
}
