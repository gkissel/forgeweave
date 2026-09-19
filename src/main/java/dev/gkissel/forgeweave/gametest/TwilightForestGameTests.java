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
 * Issue #1059 (D-M8-25): The Twilight Forest's eight Track A material presets -- {@code ironwood},
 * {@code steeleaf}, {@code knightmetal}, {@code fiery}, {@code naga_scale}, {@code arctic_fur},
 * {@code alpha_yeti_fur} and {@code carminite}. The mod is not a build/test dependency, so every
 * {@code neoforge:conditions} check here fails in this GameTest server exactly as it would in a
 * Forgeweave-only install. The positive existence path is generic infrastructure already covered
 * by {@code ConditionalMaterialGameTests}.
 *
 * <p>Verified against {@code TeamTwilight/twilightforest} branch {@code 1.21.1} (the branch built
 * for {@code minecraft_version=1.21.1}). Code license is LGPL-2.1, assets are CC BY-NC-SA 4.0, and
 * sounds/structures are all rights reserved (the repository's own {@code README.md}): read-only
 * inspiration, nothing derived, matching every other Track A batch (see
 * {@code docs/research/twilight-forest-and-ice-and-fire.md} for the full survey).
 *
 * <p>Ironwood, steeleaf, knightmetal and fiery all melt and have full smeltery integration; their
 * harvest tiers come straight from {@code BlockTagGenerator}'s own tag inheritance (ironwood on
 * {@code incorrect_for_iron_tool}, fiery on {@code incorrect_for_netherite_tool}, steeleaf and
 * knightmetal both on {@code incorrect_for_diamond_tool}), not an inference from the mod's own raw
 * stat numbers. Naga scale, arctic fur, alpha yeti fur and carminite have no tool tier of their own
 * in source (armor-only, or no gear at all for carminite) and are Part Builder only here, the same
 * shape {@code blazing_crystal.json} already ships.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class TwilightForestGameTests {

    private static final String[] TWILIGHT_FOREST_MATERIALS = {
            "ironwood", "steeleaf", "knightmetal", "fiery",
            "naga_scale", "arctic_fur", "alpha_yeti_fur", "carminite",
    };

    @GameTest(template = "empty")
    public static void unsuppliedTwilightForestMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : TWILIGHT_FOREST_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without the Twilight Forest, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private TwilightForestGameTests() {}
}
