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
 * Issue #1058 (D-M8-24, M8-19): Silent Gear's five endgame metals -- {@code crimson_steel}, {@code
 * azure_silver}, {@code azure_electrum}, {@code blaze_gold} and {@code tyrian_steel} -- as Track A
 * material presets. Silent Gear is MIT (verified via the repo's {@code LICENSE} file) and not a
 * build/test dependency, so every {@code neoforge:conditions} check here fails in this GameTest
 * server exactly as it would in a Forgeweave-only install. The positive existence path is generic
 * infrastructure already covered by {@code ConditionalMaterialGameTests}.
 *
 * <p>This is materials only, not an integration with Silent Gear's own tool system -- that stays a
 * recorded skip, since Silent Gear is itself a competing material-based tool/armor mod (see
 * {@code docs/research/atm10-compat-survey.md}).
 *
 * <h2>Roster and provenance</h2>
 *
 * <p>Verified against {@code SilentChaos512/Silent-Gear} branch {@code 1.21.1} (commit {@code
 * aa936c3ec4d54ccec0fad178488b60fae0243543}) -- item ids, {@code c:} tags and each metal's own
 * {@code harvest_tier.level_hint} came from the mod's own generated {@code silentgear_materials}
 * and {@code c:} tag JSON, not from memory. All five live in the base mod, not a Metalworks or
 * Silent's Gems compat pack: each has a real {@code c:ingots}/{@code c:nuggets}/{@code
 * c:storage_blocks}/{@code c:dusts} tag family (Forgeweave ships no dust melting row, matching the
 * #1031 precedent's tag-form scope).
 *
 * <ul>
 *   <li><b>{@code crimson_steel}</b> (level_hint 4, netherite-equivalent): carries the mod's own
 *       {@code flame_ward} and {@code magmatic} traits. Echoed with {@code
 *       forgeweave:fireward}, a {@code damage_type_immunity} against {@code
 *       minecraft:is_fire}.
 *   <li><b>{@code azure_silver}</b> (level_hint 3, diamond-equivalent, the one ore-sourced metal of
 *       the five -- it alone carries {@code c:ores}/{@code c:raw_materials}): carries the mod's own
 *       {@code moonwalker} trait. Echoed with {@code forgeweave:azure_silver_moonstep}, a {@code
 *       movement_bonus} raising jump strength.
 *   <li><b>{@code azure_electrum}</b> (level_hint 4, netherite-equivalent): carries the mod's own
 *       {@code light} and {@code accelerate} traits. Echoed with {@code
 *       forgeweave:swiftward}, a {@code movement_bonus} raising movement speed.
 *   <li><b>{@code blaze_gold}</b> (level_hint 2, iron-equivalent): carries the mod's own {@code
 *       fiery} trait on its tip. Echoed with {@code forgeweave:fireward}, a {@code
 *       damage_type_immunity} against {@code minecraft:is_fire}.
 *   <li><b>{@code tyrian_steel}</b> (level_hint 4, netherite-equivalent, the roster's toughest
 *       metal): carries the mod's own {@code void_ward} trait. Echoed with {@code
 *       forgeweave:lastbreath}, a {@code death_save}.
 * </ul>
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SilentGearGameTests {

    private static final String[] SILENT_GEAR_MATERIALS = {
            "crimson_steel", "azure_silver", "azure_electrum", "blaze_gold", "tyrian_steel",
    };

    @GameTest(template = "empty")
    public static void unsuppliedSilentGearMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : SILENT_GEAR_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without Silent Gear, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private SilentGearGameTests() {}
}
