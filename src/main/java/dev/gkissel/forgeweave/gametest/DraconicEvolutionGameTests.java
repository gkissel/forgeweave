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
 * Issue #836's negative existence path for M6 Track A batch 4 (the epic #824's endgame tier):
 * Draconic Evolution ({@code draconicevolution}) is not a build dependency (see build.gradle), so
 * both of this batch's {@code neoforge:item_exists} conditions fail here exactly as they would in a
 * Forgeweave-only install -- each material is absent from the registry entirely, matching issue
 * #826's existence-gating contract (not merely present-and-uncraftable).
 *
 * <p>The positive existence path can't be demonstrated with the real two materials for the same
 * reason batch 1/3's real rosters can't -- a GameTest server can fake a {@code c:} tag but not a
 * modid or another mod's item id (docs/research/m6-material-expansion-references.md &sect;1.4) -- so
 * {@link ConditionalMaterialGameTests} covers the mechanism itself with a gametest-only conditional
 * material. Both materials below are mechanism-tested via that shared proof, not provider-tested.
 *
 * <p>The original roster was Draconic Evolution's draconium pair only -- the wyvern/draconic/chaotic
 * core items exist on the mod's 1.21.1 tree too, but issue #836 left turning them into tool materials
 * as a separate design call ("the simplest answer is two materials, not five"). Issue #872 (the M6
 * recovery batch) resolves that deferral: {@code wyvern}/{@code chaotic} are gated on the mod's
 * {@code wyvern_core}/{@code chaotic_core} items and ship here too, stats laddered between draconium
 * and draconium_awakened. ProjectE, Avaritia, Refined Storage and Powah's own materials -- blocked
 * until #872's {@code Material.LENIENT_INGREDIENT_CODEC} schema fix, now shipped -- are covered by
 * {@link RecoveryBatchGameTests} instead, since none of those four mods is Draconic Evolution.
 *
 * <p>Verified directly against {@code Draconic-Evolution-1.21.1-3.1.4.632.jar} (the version issue
 * #836 names, still the mod's pinned {@code mod_version} on its {@code 1.21} branch as of #872), not
 * the epic's summary table or memory: {@code c:tags/item/ingots/draconium.json} and {@code
 * .../draconium_awakened.json} both exist, and the tag suffix order is genuinely inverted from the
 * item id as the issue warns -- the tag is {@code c:ingots/draconium_awakened} while the item is
 * {@code draconicevolution:awakened_draconium_ingot} -- which is why this batch's material file (and
 * this class's constant below) is named {@code draconium_awakened}, matching the tag, not the item id.
 * {@code wyvern_core} and {@code chaotic_core} ship no {@code c:} tag at all (confirmed against the
 * same tree), so {@code wyvern}/{@code chaotic} key {@code crafting_items}/{@code repair_item} on
 * those concrete item ids directly -- exactly the case #872's schema fix exists for.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class DraconicEvolutionGameTests {

    /**
     * The Draconic Evolution roster: issue #836's original draconium pair plus #872's wyvern/chaotic
     * core-tier pair, one row per material id.
     */
    private static final String[] BATCH_4_MATERIALS = { "draconium", "draconium_awakened", "wyvern", "chaotic",
            // #946 M8: the three fusion metals sit above that preset roster and hide the same way,
            // each gated on the Draconic Evolution core its own fusion recipe consumes. The four
            // above are unchanged and keep their ids -- they are the raw tier under these three.
            "emberweld", "starweld", "voidweld" };

    @GameTest(template = "empty")
    public static void unsuppliedDraconicEvolutionMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : BATCH_4_MATERIALS) {
            helper.assertTrue(materials.get(materialId(name)) == null,
                    "expected the " + name + " material to be absent without Draconic Evolution installed, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private DraconicEvolutionGameTests() {}
}
