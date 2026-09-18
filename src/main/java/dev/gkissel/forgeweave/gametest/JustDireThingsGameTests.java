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
 * Issue #1031 (D-M8-21, M8-16): Just Dire Things' four tool tiers -- {@code ferricore}, {@code
 * blazegold}, {@code celestigem} and {@code eclipsealloy} -- as Track A material presets. The mod
 * is not a build/test dependency (see {@code build.gradle}; no Java type of its own is needed at
 * all -- every material here is existence-gated JSON keyed on one of the mod's concrete item ids),
 * so every {@code neoforge:conditions} check here fails in this GameTest server exactly as it would
 * in a Forgeweave-only install. The positive existence path is generic infrastructure already
 * covered by {@code ConditionalMaterialGameTests}. (Its own registered id prefix is spelled out only
 * in the shipped material JSON, never in this class or its javadoc, so this file carries none of the
 * mod's state per {@code JustDireThingsIsolationTest}'s scan -- see #1039/D-M8-22.)
 *
 * <h2>Roster and provenance</h2>
 *
 * <p>Verified against {@code Direwolf20-MC/JustDireThings} at tag {@code v1.5.7} (commit {@code
 * 8390e0effa33cd0e0995265245baf5629b61ef22}), the newest tag actually built for {@code
 * minecraft_version=1.21.1} -- the {@code 1.21.1} branch head is unreleased dev work past that tag,
 * and every newer tag (up to {@code v1.6.11}) targets a different, later Minecraft version. MIT,
 * confirmed via the repo's {@code LICENSE.txt}, no separate asset carve-out.
 *
 * <ul>
 *   <li><b>{@code ferricore}</b> (the mod's own ingot, iron-equivalent harvest tier in the mod's
 *       own {@code GooTier}): the entry tier carries no Forge Energy battery at all -- its kit
 *       (ore/mob scanning, auto-mine assist, tree/leaf clearing, movement tweaks) spends durability,
 *       not energy. Echoed here with {@code forgeweave:ferricore_footing}, a datapack {@code
 *       trait_definition} over the existing {@code movement_bonus} behavior (step height), not a
 *       copy of the mod's own ability code.
 *   <li><b>{@code blazegold}</b> (the mod's own ingot, diamond-equivalent): every Blazegold item is
 *       fire-resistant and its signature abilities are an auto-smelter and lava-repair. Echoed with
 *       {@code forgeweave:blazegold_ember}, a {@code trait_definition} over {@code
 *       damage_type_immunity} against {@code minecraft:is_fire}.
 *   <li><b>{@code celestigem}</b> (the mod's own gem item, diamond-equivalent): the mod's first
 *       FE-powered tier ({@code PoweredTool}), and the one gem-shaped material of the four -- the
 *       mod ships no {@code c:ingots}/{@code c:raw_materials} tag for it, only the flat,
 *       single-valued {@code c:gems} tag, the same "ships zero per-material tag" shape that made
 *       Powah's crystals key on a concrete item id in {@code MaterialTest}. No molten fluid either,
 *       so celestigem is Part Builder only (no {@code cast_only}, no melting or casting rows), the
 *       same shape {@code blazing_crystal.json} already ships. Echoed with {@code
 *       forgeweave:celestigem_charge}, a {@code trait_definition} over {@code energized}.
 *   <li><b>{@code eclipsealloy}</b> (the mod's own ingot, netherite-equivalent): the top tier,
 *       {@code PoweredTool}/{@code PoweredItem} throughout with the largest FE reserves the mod
 *       ships, plus a death-protection ability on its chestplate. Echoed with two traits: {@code
 *       forgeweave:eclipsealloy_charge} ({@code energized} at the roster's largest capacity) and
 *       {@code forgeweave:eclipsealloy_ward} ({@code death_save}).
 * </ul>
 *
 * <p>No nugget item exists for any of the four tiers, and the mod's storage-block tag is one flat
 * family shared by all four (plus vanilla charcoal) rather than four per-material subtags, so each
 * material's storage-block crafting/melting row keys on the mod's own concrete block id rather than
 * a tag -- the same trade the celestigem gem row makes for the opposite reason.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class JustDireThingsGameTests {

    private static final String[] JUST_DIRE_THINGS_MATERIALS = {
            "ferricore", "blazegold", "celestigem", "eclipsealloy",
    };

    @GameTest(template = "empty")
    public static void unsuppliedJustDireThingsMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : JUST_DIRE_THINGS_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without Just Dire Things, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private JustDireThingsGameTests() {}
}
