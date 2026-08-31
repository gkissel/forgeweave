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
 * Issue #835's negative existence path for M6 Track A batch 3 (the epic #824's "Ender IO alloys"),
 * following {@link TechMetalGameTests}'s pattern for batch 1: Ender IO ({@code enderio}) is not a
 * build dependency (see build.gradle), so every one of this batch's {@code neoforge:item_exists}
 * conditions fails here exactly as it would in a Forgeweave-only install -- each material is absent
 * from the registry entirely, matching issue #826's existence-gating contract (not merely
 * present-and-uncraftable).
 *
 * <p>The positive existence path can't be demonstrated with the real eight materials for the same
 * reason batch 1's real eleven can't -- a GameTest server can fake a {@code c:} tag but not a modid
 * or another mod's item id (docs/research/m6-material-expansion-references.md &sect;1.4) -- so
 * {@link ConditionalMaterialGameTests} covers the mechanism itself with a gametest-only conditional
 * material. Every material below is mechanism-tested via that shared proof, not provider-tested.
 *
 * <p>The roster is Ender IO's eight surviving 1.21.1 alloy ingots, verified directly against the
 * mod's own {@code 1.21.1} branch (not the epic's summary table, not memory): the 1.12-era
 * {@code pulsating_iron}/{@code conductive_iron} ids are renamed to {@code pulsating_alloy}/
 * {@code conductive_alloy}, and {@code electrical_steel} no longer exists at all --
 * {@code MaterialTest#noShippedMaterialConditionsOnEnderIosRemovedElectricalSteel} is the guard
 * against that trap creeping back into a shipped material JSON.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class EnderIoAlloyGameTests {

    /** The batch 3 roster, issue #835's roster table, one row per material id. */
    private static final String[] BATCH_3_MATERIALS = {
            "redstone_alloy", "energetic_alloy", "pulsating_alloy", "conductive_alloy",
            "vibrant_alloy", "soularium", "dark_steel", "end_steel"
    };

    @GameTest(template = "empty")
    public static void unsuppliedEnderIoAlloysDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : BATCH_3_MATERIALS) {
            helper.assertTrue(materials.get(materialId(name)) == null,
                    "expected the " + name + " material to be absent without Ender IO installed, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private EnderIoAlloyGameTests() {}
}
