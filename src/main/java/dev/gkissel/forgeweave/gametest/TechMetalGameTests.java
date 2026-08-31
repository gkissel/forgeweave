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
 * Issue #833's negative existence path for M6 Track A batch 1 (the epic #824's "generic tech
 * metals, re-homed across providers"), following {@code SteelAndTagGatedGameTests
 * #unsuppliedCompatMetalsDoNotExistAtAll}'s pattern for the original four compat metals: none of
 * Mekanism, Immersive Engineering, Modern Industrialization or Extreme Reactors (modid
 * {@code bigreactors}) is a build dependency (see build.gradle), so every one of this batch's
 * {@code neoforge:item_exists}/{@code neoforge:or} conditions fails here exactly as it would in a
 * Forgeweave-only install -- each material is absent from the registry entirely, matching issue
 * #826's existence-gating contract (not merely present-and-uncraftable).
 *
 * <p>The positive existence path (an installed provider actually supplying the ingot) can't be
 * demonstrated with the real eleven materials for the same reason the real four can't -- a
 * GameTest server can fake a {@code c:} tag but not a modid or another mod's item id
 * (docs/research/m6-material-expansion-references.md &sect;1.4) -- so {@link
 * ConditionalMaterialGameTests} covers the mechanism itself with a gametest-only conditional
 * material. Every material below is mechanism-tested via that shared proof, not provider-tested.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class TechMetalGameTests {

    /** The batch 1 roster, issue #833's roster table, one row per material id. */
    private static final String[] BATCH_1_MATERIALS = {
            "tin", "aluminium", "nickel", "constantan", "invar", "platinum",
            "titanium", "tungsten", "iridium", "uranium", "graphite"
    };

    @GameTest(template = "empty")
    public static void unsuppliedTechMetalsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : BATCH_1_MATERIALS) {
            helper.assertTrue(materials.get(materialId(name)) == null,
                    "expected the " + name + " material to be absent without its supplying mod, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private TechMetalGameTests() {}
}
