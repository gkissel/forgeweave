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
 * Issue #1059 (D-M8-25): IceAndFire Community Edition's ten Track A material presets -- dragon bone,
 * the three dragonsteel tiers, three death worm chitin colors and three troll leather biomes. Silver
 * is not here: it widens the existing {@code silver} material's {@code neoforge:conditions} with a
 * fourth provider branch instead of shipping a second material, the same dedupe shape #1031 already
 * used for Eternal Ores (see {@code material/silver.json} and {@code CompatMaterialAvailability}).
 * The mod is not a build/test dependency, so every {@code neoforge:conditions} check here fails in
 * this GameTest server exactly as it would in a Forgeweave-only install. The positive existence path
 * is generic infrastructure already covered by {@code ConditionalMaterialGameTests}.
 *
 * <p>Verified against {@code IAFEnvoy/IceAndFire-CE} branch {@code 1.21.1} (the fork ATM10 8.1
 * actually ships for 1.21.1; the original {@code AlexModGuy/ice-and-fire-dragons} has no build past
 * 1.20.1), commit {@code a16787ae2f} (2026-09-15, the branch head at survey time). LGPL-3.0-or-later,
 * confirmed from the repository's own {@code LICENSE}: read-only inspiration, nothing derived,
 * matching every other Track A batch (see {@code docs/research/twilight-forest-and-ice-and-fire.md}
 * for the full survey, including why copper, dragon scales, sea serpent scales, myrmex chitin and
 * the single-sword creature materials do not get presets here).
 *
 * <p>Dragon bone and the three dragonsteel tiers all melt and have full smeltery integration; their
 * harvest tiers come straight from {@code IafTiers} (dragon bone on
 * {@code incorrect_for_iron_tool}, all three dragonsteels on {@code incorrect_for_netherite_tool}),
 * not an inference from the mod's own raw stat numbers. Death worm chitin and troll leather have no
 * tool tier of their own in source (armor-only) and are Part Builder only here, the same shape
 * {@code blazing_crystal.json} already ships; each trio shares one trait since the mod gives all
 * three color/biome variants of each identical stats.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class IceAndFireGameTests {

    private static final String[] ICE_AND_FIRE_MATERIALS = {
            "dragon_bone", "dragonsteel_fire", "dragonsteel_ice", "dragonsteel_lightning",
            "deathworm_chitin_yellow", "deathworm_chitin_white", "deathworm_chitin_red",
            "troll_leather_mountain", "troll_leather_forest", "troll_leather_frost",
    };

    @GameTest(template = "empty")
    public static void unsuppliedIceAndFireMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : ICE_AND_FIRE_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without Ice and Fire, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private IceAndFireGameTests() {}
}
