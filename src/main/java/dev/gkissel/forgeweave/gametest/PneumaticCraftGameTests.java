package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.material.Material;

/**
 * Issue #1058 (D-M8-24, M8-19): PneumaticCraft: Repressurized's compressed iron as a Track A
 * material preset. The mod is GPL-3.0 (verified via the repo's {@code LICENSE} file) and not a
 * build/test dependency, so its {@code neoforge:conditions} check fails in this GameTest server
 * exactly as it would in a Forgeweave-only install. The positive existence path is generic
 * infrastructure already covered by {@code ConditionalMaterialGameTests}.
 *
 * <h2>Roster and provenance</h2>
 *
 * <p>Verified against {@code TeamPneumatic/pnc-repressurized} branch {@code 1.21} (commit {@code
 * 824ad514ecbdcc02cca59deccecefaa0a9b913a0}, {@code gradle.properties} pins {@code
 * minecraft_version=1.21.1}). {@code compressed_iron}'s ingot id ({@code
 * pneumaticcraft:ingot_iron_compressed}) and its {@code c:ingots}/{@code c:gears}/{@code
 * c:storage_blocks} tags came from the mod's own generated {@code c:} tag JSON. No nugget or dust
 * tag exists, and the metal has no ore or raw form -- it is crafted, not mined (the mod's own
 * pressure-chamber and explosion-crafting recipes).
 *
 * <p>The mod ships no {@code ToolMaterial} for compressed iron at all -- only an {@code
 * ArmorMaterial} ({@code ModArmorMaterials#COMPRESSED_IRON}: defense {@code [2, 5, 6, 2]},
 * toughness {@code 1.0f}, knockback resistance {@code 0.075f}, durability factor {@code 24}, between
 * vanilla iron's {@code 15} and diamond's {@code 33}) -- so Forgeweave's tool-part stats sit at the
 * iron tier by placement, not by a ported number. Echoed with {@code
 * forgeweave:heft}, a {@code damage_floor} guaranteeing a minimum hit, matching the
 * "compressed, heavy metal" theme the armor's above-iron toughness implies.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class PneumaticCraftGameTests {

    private static final String[] PNEUMATICCRAFT_MATERIALS = {
            "compressed_iron",
    };

    @GameTest(template = "empty")
    public static void unsuppliedPneumaticCraftMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : PNEUMATICCRAFT_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without PneumaticCraft: Repressurized, "
                            + "found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private PneumaticCraftGameTests() {}
}
