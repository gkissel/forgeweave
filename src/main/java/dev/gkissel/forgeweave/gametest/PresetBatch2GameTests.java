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
 * Issue #834 (M6 preset batch 2): Mekanism, Applied Energistics 2 and Occultism. Every material here
 * follows the same {@code neoforge:conditions} existence gate as batch 1 (issue #833) and the
 * original four (#826) -- one {@code neoforge:item_exists} on a concrete item id, no {@code or}
 * combinator needed since each material has exactly one candidate source mod (unlike #833's
 * re-homed generics).
 *
 * <p>Mechanism-tested, not provider-tested: none of {@code mekanism}, {@code ae2} or {@code
 * occultism} is a build/test dependency (see build.gradle), so every material's condition fails in
 * this GameTest server exactly as it would in a Forgeweave-only install -- {@link
 * #unsuppliedPresetsDoNotExistAtAll} proves the negative path the same way {@code
 * SteelAndTagGatedGameTests#unsuppliedCompatMetalsDoNotExistAtAll} does for the original four. The
 * positive existence path is generic infrastructure already covered by {@code
 * ConditionalMaterialGameTests} (a gametest-only material on {@code minecraft:diamond}, since a
 * GameTest server can fake a {@code c:} tag but not another mod's item id -- prep doc &sect;1.4)
 * and is not re-proven per material here.
 *
 * <h2>Roster and provenance</h2>
 *
 * <p>Verified against each mod's own 1.21.1 git tree (not the epic's roster table, which listed
 * ids that turned out not to exist or not to ship -- see below):
 *
 * <ul>
 *   <li>Mekanism ({@code mekanism:ingot_osmium}, {@code mekanism:ingot_refined_obsidian}, {@code
 *       mekanism:ingot_refined_glowstone}, {@code mekanism:hdpe_stick}, {@code
 *       mekanism:fluorite_gem}) -- five materials, all confirmed. HDPE ships as four separate items
 *       ({@code hdpe_pellet}/{@code _rod}/{@code _sheet}/{@code _stick}); {@code c:rods/plastic} --
 *       the only convention tag Mekanism gives any of them -- tags the stick, not the sheet, so the
 *       stick is the crafting unit here rather than the sheet the epic's roster table suggested.
 *   <li>Applied Energistics 2 ({@code ae2:certus_quartz_crystal}, {@code ae2:fluix_crystal}, {@code
 *       ae2:sky_stone_block}) -- three materials, all confirmed. Sky stone's crafting/repair item is
 *       {@code c:dusts/sky_stone} (a real AE2 tag) rather than the concrete block id: {@code
 *       Ingredient}'s single-item form resolves the id against the registry even outside {@code
 *       ConditionalOps}, which every material test in this repo parses through directly, so a
 *       concrete id from a mod absent on the test classpath fails {@code Material.CODEC.parse}
 *       (verified -- an earlier draft of this file used the concrete block id and broke
 *       {@code MaterialSyncSizeTest}/{@code MaterialTest}/{@code ArmorMaterialTest}). A tag survives
 *       because {@code Ingredient}'s tag branch never needs a member to exist. The existence
 *       condition below still names the concrete block id -- {@code neoforge:conditions} is inert to
 *       {@link Material#CODEC}, so it never hits this problem.
 *   <li>Refined Storage is <b>not shipped in this batch</b>, for two reasons found during
 *       verification rather than assumed from the epic's table. First, the epic's roster listed a
 *       "quartz enriched copper"; the mod's own {@code RSItems} registers only {@code
 *       quartz_enriched_iron} and {@code silicon} (no copper variant exists at 2.0.9). Second --
 *       and the one that actually blocks the other two -- Refined Storage ships <em>no</em> {@code
 *       c:} tags at all, so {@code quartz_enriched_iron}/{@code silicon} could only be expressed
 *       with the same concrete-item-id shape that broke sky stone above, and unlike sky stone there
 *       is no tag to fall back to. Shipping them would mean either failing this repo's own material
 *       test suite or teaching that suite to parse conditional materials through {@code
 *       ConditionalOps} (a shared-infrastructure change touching the same test files sibling batch
 *       #833 also edits) -- recorded as a maintainer decision / follow-up rather than guessed at
 *       here.
 *   <li>Occultism ({@code occultism:iesnium_ingot}, {@code occultism:dragonyst_dust}) -- the epic's
 *       roster also listed "dark gem"; Occultism's own {@code c:gems/dark_gem} tag resolves only to
 *       {@code evilcraft:dark_gem} (an optional EvilCraft item, {@code required: false}), so it is
 *       not really Occultism's own material and is skipped rather than gated on a different,
 *       unlisted mod.
 * </ul>
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class PresetBatch2GameTests {

    /** Every material this batch ships, none of whose provider mods are a build dependency here. */
    private static final String[] MATERIALS = {
            "osmium", "refined_obsidian", "refined_glowstone", "hdpe", "fluorite",
            "certus_quartz", "fluix", "sky_stone",
            "iesnium", "dragonyst",
    };

    @GameTest(template = "empty")
    public static void unsuppliedPresetsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : MATERIALS) {
            helper.assertTrue(materials.get(materialId(name)) == null,
                    "expected the " + name + " material to be absent without its supplying mod, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private PresetBatch2GameTests() {}
}
