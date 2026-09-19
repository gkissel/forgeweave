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
 * Issue #1069: Actually Additions' six empowered crystals join {@link PresetBatch5GameTests}'
 * plain-crystal roster as Track A material presets, and every one of the mod's thirteen materials
 * (the seven #837 already ships plus these six) is proven buildable at the Part Builder from its own
 * item. {@code actuallyadditions} is not a build/test dependency (see build.gradle), so every
 * material's {@code neoforge:item_exists} condition fails in this GameTest server exactly as it would
 * in a Forgeweave-only install -- the same negative-existence proof {@link PresetBatch5GameTests} and
 * {@link AetherGameTests} give their own batches. The positive existence path is generic
 * infrastructure already covered by {@code ConditionalMaterialGameTests}.
 *
 * <h2>Roster and provenance</h2>
 *
 * <p>Verified against the shipped {@code actuallyadditions-1.3.24+mc1.21.1.jar} (NeoForge, MIT,
 * {@code github.com/Ellpeck/ActuallyAdditions}) -- its {@code data/actuallyadditions/recipe/empowering/}
 * folder registers each color's Empowerer recipe, and {@code data/actuallyadditions/tags/item/crystal_blocks.json}
 * plus the per-color {@code data/c/tags/item/storage_blocks/empowered_*_crystal.json} tags confirm
 * every one of the six items and their storage blocks are real, registered content:
 *
 * <ul>
 *   <li><b>{@code empowered_restonia_crystal}</b> ({@code actuallyadditions:empowered_restonia_crystal},
 *       block {@code actuallyadditions:empowered_restonia_crystal_block}). The mod's own {@code
 *       Crystals} enum (decompiled from the jar) pairs restonia with {@code REDSTONE}. Echoed with
 *       {@code forgeweave:empowered_restonia_bloodsurge}, a stronger {@code damage_scales_with}
 *       (target max health) than the plain crystal's {@code bloodgem} trait -- the same behavior
 *       family at a higher level, issue #1069's own steer.
 *   <li><b>{@code empowered_palis_crystal}</b> ({@code actuallyadditions:empowered_palis_crystal},
 *       block {@code actuallyadditions:empowered_palis_crystal_block}). Paired with {@code LAPIS}.
 *       Echoed with {@code forgeweave:empowered_palis_tempest}, {@code stormglass}'s impact-velocity
 *       scaling at a higher coefficient and cap.
 *   <li><b>{@code empowered_diamatine_crystal}</b> ({@code actuallyadditions:empowered_diamatine_crystal},
 *       block {@code actuallyadditions:empowered_diamatine_crystal_block}). Paired with {@code
 *       DIAMOND}. Echoed with {@code forgeweave:empowered_diamatine_prism}, {@code radiant_edge}'s
 *       full-charge bonus damage raised from 3.0 to 5.0.
 *   <li><b>{@code empowered_void_crystal}</b> ({@code actuallyadditions:empowered_void_crystal}, block
 *       {@code actuallyadditions:empowered_void_crystal_block}). Paired with {@code COAL}. Echoed
 *       with {@code forgeweave:empowered_void_maw}, {@code voidtouched}'s flat attack bonus doubled
 *       from 1.0 to 2.0.
 *   <li><b>{@code empowered_emeradic_crystal}</b> ({@code actuallyadditions:empowered_emeradic_crystal},
 *       block {@code actuallyadditions:empowered_emeradic_crystal_block}). Paired with {@code
 *       EMERALD}. Echoed with {@code forgeweave:empowered_emeradic_bulwark}, a {@code damage_floor}
 *       (never take a hit below 2 hearts) -- {@code verdant_ward}'s knockback-resistance shield idea
 *       at a concrete, stronger defensive guarantee no {@code TraitBehaviors} seam expresses as flat
 *       knockback resistance.
 *   <li><b>{@code empowered_enori_crystal}</b> ({@code actuallyadditions:empowered_enori_crystal},
 *       block {@code actuallyadditions:empowered_enori_crystal_block}). Paired with {@code IRON}.
 *       Echoed with {@code forgeweave:empowered_enori_radiance}, {@code luminous}'s on-hit glowing
 *       effect held twice as long (200 ticks instead of 100).
 * </ul>
 *
 * <p>All six sit at the {@code minecraft:incorrect_for_netherite_tool} rung -- one step above the
 * plain crystals' diamond tier, "clearly above its plain counterpart" per the issue, and still no rung
 * above {@code resonite} (D-M8-10). The jar ships no decompilable {@code ToolMaterial}/{@code Tier}
 * class for any crystal color (only the {@code Crystals} enum's particle/cluster colors), so, like
 * PneumaticCraft's {@code compressed_iron} in D-M8-24, tier and stats are Forgeweave's own placement
 * within the existing netherite-tier envelope rather than a ported tool number.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class EmpoweredCrystalsGameTests {

    private static final String[] EMPOWERED_CRYSTAL_MATERIALS = {
            "empowered_restonia_crystal", "empowered_palis_crystal", "empowered_diamatine_crystal",
            "empowered_void_crystal", "empowered_emeradic_crystal", "empowered_enori_crystal",
    };

    @GameTest(template = "empty")
    public static void unsuppliedEmpoweredCrystalsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : EMPOWERED_CRYSTAL_MATERIALS) {
            helper.assertTrue(materials.get(materialId(name)) == null,
                    "expected the " + name + " material to be absent without Actually Additions, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private EmpoweredCrystalsGameTests() {}
}
