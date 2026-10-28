package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
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

    private static final Identifier NAHUATL = Identifier.fromNamespaceAndPath("forgeweave", "nahuatl");
    private static final Identifier COBALT = Identifier.fromNamespaceAndPath("forgeweave", "cobalt");

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

    /**
     * Every worn pass resolves to one of the four gray bases on disk -- no per-material file is ever
     * looked up. On 26.1 the passes are the layers of the piece's equipment asset,
     * {@code equipment/plate.json}, each a texture id the game resolves under
     * {@code textures/entity/equipment/<layer type>/}; both layer types carry maille under plating, in
     * {@link ArmorPieceItem#PLATE_LAYERS}' order, which is the order the tint hook keys on.
     */
    @Test
    void everyWornLayerResolvesToAGrayBaseOnDisk() throws IOException {
        Identifier asset = ArmorPieceItem.PLATE_ASSET.identifier();
        Path assets = projectRoot().resolve("src/main/resources/assets/" + asset.getNamespace());
        JsonObject layers = JsonParser.parseString(Files.readString(assets.resolve("equipment/" + asset.getPath() + ".json")))
                .getAsJsonObject().getAsJsonObject("layers");
        for (String layerType : List.of("humanoid", "humanoid_leggings")) {
            List<Identifier> textures = new ArrayList<>();
            for (JsonElement layer : layers.getAsJsonArray(layerType)) {
                textures.add(Identifier.parse(layer.getAsJsonObject().get("texture").getAsString()));
            }
            assertEquals(ArmorPieceItem.PLATE_LAYERS, textures, layerType + ": maille under plating");
            for (Identifier texture : textures) {
                assertTrue(texture.getPath().matches("derived/(plating|maille)"), texture.toString());
                Path png = assets.resolve("textures/entity/equipment/" + layerType + "/" + texture.getPath() + ".png");
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
        assertEquals(COBALT, ArmorPieceItem.layerMaterial(chestplate, ArmorPieceItem.MAILLE_LAYER));
        assertEquals(NAHUATL, ArmorPieceItem.layerMaterial(chestplate, ArmorPieceItem.PLATING_LAYER));
    }

    /** A stack without materials (creative-tab dummy, corrupted save) tints nothing and keeps the gray base. */
    @Test
    void aStackWithoutMaterialsHasNoLayerMaterial() {
        ItemStack bare = new ItemStack(ForgeweaveItems.ARMOR_CHESTPLATE.get());
        assertNull(ArmorPieceItem.layerMaterial(bare, 0));
        assertNull(ArmorPieceItem.layerMaterial(bare, 1));
    }
}
