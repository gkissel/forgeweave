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
 * Issue #1058 (D-M8-24, M8-19): Forbidden and Arcanus' top metal, {@code deorum}, as a Track A
 * material preset. The mod is all rights reserved (the repo reports no license) and not a
 * build/test dependency, so its {@code neoforge:conditions} check fails in this GameTest server
 * exactly as it would in a Forgeweave-only install. The positive existence path is generic
 * infrastructure already covered by {@code ConditionalMaterialGameTests}. Nothing here is derived
 * from the mod: only its concrete item ids are read, never its code or its art.
 *
 * <h2>Naming correction (issue #1058's own ask)</h2>
 *
 * <p>{@code docs/research/atm10-compat-survey.md} named the mod's top metal "aetherium", a name not
 * independently confirmed at planning. It does not exist in the mod's current 1.21.1 tree: the
 * mod's real top metal is {@code deorum} ({@code forbidden_arcanus:deorum_ingot}), reinforced into
 * {@code ModTiers#REINFORCED_DEORUM} for its top tool tier. The survey is corrected alongside this
 * class (see its own row).
 *
 * <h2>Roster and provenance</h2>
 *
 * <p>Verified against {@code stal111/Forbidden-Arcanus} branch {@code 1.21.1} (commit {@code
 * 34f83feb76204fd773e1d2e1d69ecc5b2f2bb933}, {@code gradle.properties} pins {@code
 * minecraft_version=1.21.1}). {@code deorum}'s ingot id and its {@code c:ingots}/{@code
 * c:nuggets}/{@code c:storage_blocks} tags came from the mod's own generated {@code c:} tag JSON;
 * it has no ore or raw form. {@code ModTiers#REINFORCED_DEORUM} ({@code uses=2561, speed=9.0F,
 * damage=3.5F, enchantmentValue=26}, repaired with a Stellarite Piece rather than the ingot itself)
 * is the mod's top tool tier, one step above its own {@code SLIMEC} tier and just under its
 * dragon-scale {@code DRACO_ARCANUS} tier, which is not an ingot metal and is out of this batch's
 * scope. Forgeweave's own numbers sit at the netherite tier, inside the existing Track A envelope,
 * rather than a direct port of the mod's own unit scale. Echoed with {@code
 * forgeweave:bracingplate3}, a {@code stacking_resistance} matching the "reinforced by repeated
 * forge work" theme.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ForbiddenArcanusGameTests {

    private static final String[] FORBIDDEN_ARCANUS_MATERIALS = {
            "deorum",
    };

    @GameTest(template = "empty")
    public static void unsuppliedForbiddenArcanusMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : FORBIDDEN_ARCANUS_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without Forbidden and Arcanus, "
                            + "found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private ForbiddenArcanusGameTests() {}
}
