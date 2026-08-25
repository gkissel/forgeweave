package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * Issue #726: the worn plate layers are the 1.20 clone's four gray bases tinted at render time from
 * {@code Material.color} ({@code IClientItemExtensions#getArmorLayerTintColor}), so a datapack
 * material with no PNG of its own -- nahuatl here, which the clone never shipped one for -- still
 * resolves a texture and a tint. Pins the two halves the render hook is built from.
 */
class ArmorLayerTintTest {

    private static final ResourceLocation NAHUATL = ResourceLocation.fromNamespaceAndPath("forgeweave", "nahuatl");
    private static final ResourceLocation COBALT = ResourceLocation.fromNamespaceAndPath("forgeweave", "cobalt");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Path projectRoot() {
        for (Path candidate = Path.of("").toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found)");
    }

    /** Every worn pass resolves to one of the four gray bases on disk -- no per-material file is ever looked up. */
    @Test
    void everyWornLayerResolvesToAGrayBaseOnDisk() {
        List<ArmorMaterial.Layer> layers = ArmorPieceItem.plateMaterial().layers();
        assertEquals(2, layers.size(), "maille under plating");
        for (ArmorMaterial.Layer layer : layers) {
            for (boolean inner : new boolean[] {false, true}) {
                ResourceLocation texture = layer.texture(inner);
                assertTrue(texture.getPath().matches("textures/models/armor/derived/(plating|maille)_layer_[12]\\.png"),
                        texture.toString());
                Path png = projectRoot().resolve("src/main/resources/assets/forgeweave/" + texture.getPath());
                assertTrue(Files.exists(png), png + " is missing -- run scripts/derive_armor_art.py");
            }
        }
    }

    /** The maille pass (layer 0) tints with the maille part's material, the plating pass (layer 1) with the plating's. */
    @Test
    void eachLayerTintsWithItsOwnPartsMaterial() {
        ItemStack chestplate = new ItemStack(ForgeweaveItems.ARMOR_CHESTPLATE.get());
        chestplate.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                ToolMaterials.of(ToolConstants.CHESTPLATE.parts(), List.of(NAHUATL, COBALT)));
        assertEquals(COBALT, ArmorPieceItem.layerMaterial(chestplate, 0));
        assertEquals(NAHUATL, ArmorPieceItem.layerMaterial(chestplate, 1));
    }

    /** A stack without materials (creative-tab dummy, corrupted save) tints nothing and keeps the gray base. */
    @Test
    void aStackWithoutMaterialsHasNoLayerMaterial() {
        ItemStack bare = new ItemStack(ForgeweaveItems.ARMOR_CHESTPLATE.get());
        assertNull(ArmorPieceItem.layerMaterial(bare, 0));
        assertNull(ArmorPieceItem.layerMaterial(bare, 1));
    }
}
