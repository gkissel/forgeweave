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
 * Issue #1058 (D-M8-24, M8-19): L_Ender's Cataclysm's {@code ignitium} and {@code cursium} boss
 * metals as Track A material presets. Cataclysm is CC-BY-NC-ND-4.0 (verified via its Modrinth
 * project page), the strictest license in this batch, so nothing beyond a bare item id is read;
 * numbers, text and traits here are Forgeweave's own. The mod is not a build/test dependency, so
 * every {@code neoforge:conditions} check here fails in this GameTest server exactly as it would in
 * a Forgeweave-only install. The positive existence path is generic infrastructure already covered
 * by {@code ConditionalMaterialGameTests}.
 *
 * <h2>Roster and provenance</h2>
 *
 * <p>The mod's public source tree ({@code lender544/new1.20.1}) has not been ported past 1.20.1
 * Forge, so its item ids and {@code c:} tag coverage were read from the shipped jar itself: {@code
 * L_Ender's Cataclysm 1.21.1-3.33.jar} (Modrinth project {@code l_enders-cataclysm}, version {@code
 * 3.33}, NeoForge, published 2026-08-22 -- the latest 1.21.1 NeoForge release at research time).
 * Neither metal carries a {@code c:} tag of any kind; both key on the mod's own concrete item id.
 * Stat numbers come from the mod's own {@code Armortier} enum, read off its still-public 1.20.1
 * source tree (commit {@code 6dd12cb6b44965ef5157091b6962d9d13d7fc099}) rather than guessed, since
 * Cataclysm's own armor numbers have historically carried forward unchanged across Minecraft
 * version ports.
 *
 * <ul>
 *   <li><b>{@code ignitium}</b> ({@code cataclysm:ignitium_ingot}, {@code
 *       cataclysm:ignitium_block}, dropped by the Ignis boss): {@code Armortier.IGNITIUM} (defense
 *       {@code [6, 11, 9, 6]}, toughness {@code 4.0f}, knockback resistance {@code 0.15f}) --
 *       strictly above vanilla netherite's {@code [3, 8, 6, 3]} / {@code 3.0f} / {@code 0.1f}, and
 *       the armor is non-damageable in the mod's own item class. No rung above {@code resonite}
 *       (D-M8-10): Forgeweave still places it at the netherite tier. Echoed with {@code
 *       forgeweave:fireward}, a {@code damage_type_immunity} against {@code
 *       minecraft:is_fire} -- the issue's own "ignitium's fire" hint.
 *   <li><b>{@code cursium}</b> ({@code cataclysm:cursium_ingot}, {@code cataclysm:cursium_block},
 *       dropped by the Maledictus boss): {@code Armortier.CURSIUM} (defense {@code [5, 10, 8, 5]},
 *       toughness {@code 4.0f}, knockback resistance {@code 0.05f}), the roster's other
 *       above-netherite boss armor metal, found alongside ignitium in the same jar. Echoed with
 *       {@code forgeweave:grievous}, a {@code reduce_target_healing} matching a curse theme.
 * </ul>
 *
 * <p>The jar also ships a third above-netherite armor tier, {@code Armortier.BONE_REPTILE} ({@code
 * ancient_metal}, a smelted rather than boss-drop metal), left out of this batch: the maintainer's
 * roster named ignitium plus "any other tool-worthy boss metal", and ignitium/cursium are the pair
 * that share ignitium's boss-drop, non-damageable-armor shape. {@code ancient_metal} is a real
 * candidate for a follow-up batch.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CataclysmGameTests {

    private static final String[] CATACLYSM_MATERIALS = {
            "ignitium", "cursium",
    };

    @GameTest(template = "empty")
    public static void unsuppliedCataclysmMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : CATACLYSM_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without L_Ender's Cataclysm, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private CataclysmGameTests() {}
}
