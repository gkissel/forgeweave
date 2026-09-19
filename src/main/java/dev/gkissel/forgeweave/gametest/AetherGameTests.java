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
 * Issue #1058 (D-M8-24, M8-19): The Aether's {@code zanite}, {@code gravitite} and {@code
 * ambrosium} as Track A material presets. The Aether is LGPL-3.0 (verified via the repo's {@code
 * LICENSE} file) and not a build/test dependency, so every {@code neoforge:conditions} check here
 * fails in this GameTest server exactly as it would in a Forgeweave-only install. The positive
 * existence path is generic infrastructure already covered by {@code ConditionalMaterialGameTests}.
 * Only tags and item ids are read here, nothing is derived (ADR-0003).
 *
 * <h2>Roster and provenance</h2>
 *
 * <p>Verified against {@code The-Aether-Team/The-Aether} branch {@code 1.21.1-develop} (commit
 * {@code e07d30e16fbd0f09cc067b39594035e395dcf996}). The Aether ships no {@code c:} (NeoForge
 * Common Conventions) tags for any of the three items at all -- only its own {@code aether:}
 * namespace tags ({@code aether:tags/item/gems/*}, {@code aether:tags/item/ores/*}) -- so all three
 * materials key on the mod's own concrete item id, the same shape {@code celestigem} used for the
 * same reason.
 *
 * <ul>
 *   <li><b>{@code zanite}</b> ({@code aether:zanite_gemstone}, {@code aether:zanite_block}):
 *       {@code AetherItemTiers.ZANITE} (iron-equivalent, {@code uses=250, speed=6.0F, damage=2.0F,
 *       enchantability=14}). Echoed with {@code forgeweave:zanite_growth}, a {@code
 *       stat_scales_with_wear} raising mining speed as the tool wears -- the issue's own "zanite
 *       growing stronger as it wears" hint.
 *   <li><b>{@code gravitite}</b> ({@code aether:enchanted_gravitite}, no storage-block form): {@code
 *       AetherItemTiers.GRAVITITE} (diamond-equivalent, {@code uses=1561, speed=8.0F, damage=3.0F,
 *       enchantability=10}), the metal behind the Aether's own flight-granting Gravitite Boots.
 *       Echoed with {@code forgeweave:gravitite_levity}, a {@code movement_bonus} raising flying
 *       speed -- the issue's own "gravitite's lightness" hint.
 *   <li><b>{@code ambrosium}</b> ({@code aether:ambrosium_shard}, {@code aether:ambrosium_block}):
 *       the mod's own healing/light-themed utility gem (the Ambrosium Torch's regeneration aura);
 *       the mod gives it no {@code AetherItemTiers} entry of its own, so Forgeweave's stats sit at
 *       the iron tier by placement. Echoed with {@code forgeweave:ambrosium_glow}, an {@code
 *       effect_on_hurt} granting a short burst of regeneration.
 * </ul>
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class AetherGameTests {

    private static final String[] AETHER_MATERIALS = {
            "zanite", "gravitite", "ambrosium",
    };

    @GameTest(template = "empty")
    public static void unsuppliedAetherMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : AETHER_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without The Aether, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private AetherGameTests() {}
}
