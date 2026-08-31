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
 * Issue #837 (M6 preset batch 5): Actually Additions, Psi, Powah, Industrial Foregoing and Extreme
 * Reactors -- the epic #824's "gem/crystal tier". None of {@code actuallyadditions}, {@code psi},
 * {@code powah}, {@code industrialforegoing} or {@code bigreactors} is a build/test dependency (see
 * build.gradle), so every material's {@code neoforge:item_exists} condition fails in this GameTest
 * server exactly as it would in a Forgeweave-only install, the same negative-existence proof {@link
 * PresetBatch2GameTests} and {@link EnderIoAlloyGameTests} give their own batches. The positive
 * existence path is generic infrastructure already covered by {@code ConditionalMaterialGameTests}
 * and is not re-proven per material here.
 *
 * <h2>Roster, provenance, and what's skipped</h2>
 *
 * <p>Verified against each mod's own 1.21.1 git tree (not the epic issue's roster table alone, which
 * got Powah's item id backwards -- see below):
 *
 * <ul>
 *   <li><b>Actually Additions</b> ({@code v1.3.26}): black quartz ({@code c:gems/black_quartz} +
 *       {@code c:storage_blocks/black_quartz}) plus its six coloured crystals -- restonia, palis,
 *       diamatine, void, emeradic, enori. The six crystals ship <em>no</em> {@code c:gems/*} tag for
 *       the raw item (confirmed: only {@code c:storage_blocks/<crystal>} exists), so their {@code
 *       crafting_items}/{@code repair_item} key on that storage-block tag instead -- the same
 *       "use whichever real tag the mod actually ships" move batch 2's HDPE stick and sky stone dust
 *       already made, not the "ships zero tags at all" shape that got Refined Storage skipped. All
 *       seven share {@code forgeweave:pristine} (#827's {@code damage_scales_with(REMAINING_DURABILITY)}
 *       instance, previously registered but unassigned); diamatine additionally carries {@code
 *       forgeweave:surging} as the "one of them carries an extra" trait the issue calls for.
 *   <li><b>Psi</b> ({@code release-1.21.1-109}): all four of {@code psimetal}, {@code psigem}, {@code
 *       ivory_psimetal} and {@code ebony_psimetal} ship full {@code c:ingots/*}/{@code c:gems/*} plus
 *       {@code c:storage_blocks/*} tags -- no id trap here. Ivory and ebony finally give the M6 utility
 *       library's {@code forgeweave:sunmend}/{@code forgeweave:duskmend} self-repair instances (issue
 *       #829, registered but "not yet assigned to a material" per their own javadoc) their first home,
 *       matching the reference roster's "ivory adds holy, ebony adds darkness" plus the shared
 *       "psi-energy repair" idea in one reused trait each.
 *   <li><b>Powah</b> ({@code v6.2.10}): only {@code uraninite} ships ({@code c:raw_materials/uraninite}
 *       + {@code c:storage_blocks/uraninite}, condition on {@code powah:uraninite_raw}). The epic
 *       issue's {@code energised_steel} id is backwards -- Powah's own tree registers it as {@code
 *       powah:steel_energized} -- and, worse, it and the four crystals ({@code crystal_blazing}/{@code
 *       crystal_niotic}/{@code crystal_nitro}/{@code crystal_spirited}) ship <em>no</em> per-material
 *       {@code c:ingots/*}/{@code c:gems/*} subtag at all, only the flat parent {@code c:ingots}/{@code
 *       c:gems} tags -- the same "ships zero tags" shape that got Refined Storage skipped in batch 2.
 *       All five were skipped rather than keyed on an item id at the time; issue #872's recovery
 *       batch unblocks {@code steel_energized} (shipped as {@code energised_steel}, see {@link
 *       RecoveryBatchGameTests}), but the four crystals are still tagless and still skipped -- {@link
 *       dev.gkissel.forgeweave.material.MaterialTest#noShippedMaterialConditionsOnPowahsUntaggedCrystals}
 *       guards that narrower skip.
 *   <li><b>Industrial Foregoing</b> ({@code 1.21} branch, {@code 1.21-3.6.39}): only {@code pink_slime}
 *       ships, keyed on {@code c:ingots/pink_slime} (condition {@code
 *       industrialforegoing:pink_slime_ingot}) -- no nugget or item-level storage-block tag exists for
 *       it, so its {@code crafting_items} is the single ingot entry. It carries {@code
 *       forgeweave:slimey_green} per the issue's own steer ("a natural fit for Forgeweave's existing
 *       slime trait family... rather than a new behaviour"), not a bespoke pink variant. Essence is
 *       <em>not</em> shipped: it is a fluid ({@code essence_bucket}), never an item, on this mod's own
 *       tree, so it cannot back a Part Builder material under JC3 (no fluids).
 *   <li><b>Extreme Reactors</b> ({@code bigreactors}, last <em>published</em> 1.21.1 build {@code
 *       1.21.1-2.4.9}, 2024-09-18, matching the issue's "abandoned since" claim even though the git
 *       repo itself has continued unreleased dev commits since -- ids here are read from that exact
 *       tag, not an unreleased HEAD): {@code cyanite}, {@code blutonium} and {@code ludicrite} ship,
 *       each keyed on its own {@code c:ingots/*}/{@code c:storage_blocks/*} pair, no nugget tag for
 *       any of the four. <b>Yellorium is deliberately not shipped as a separate material</b>: {@code
 *       bigreactors:yellorium_ingot} is tagged under <em>both</em> {@code c:ingots/yellorium} and
 *       {@code c:ingots/uranium} on Extreme Reactors' own tree, and the already-shipped {@code uranium}
 *       material (#833 batch 1) already lists {@code bigreactors:yellorium_ingot} as one of its three
 *       {@code neoforge:or} providers -- shipping a second "yellorium" material on the same tag would
 *       let one real ingot back two different, differently-statted Forgeweave materials. No existing
 *       trait models a leveled radioactive self-effect (the M6 on-hit library's {@code EffectOnHit}/
 *       {@code EffectOnSelfOnHit} classes only ever reach the struck target or ride a combat hit, not a
 *       passive on-use aura), so the three shipped materials carry no trait -- a reported gap, not a
 *       guess, per the "reuse existing trait ids, report gaps rather than minting new behaviours" rule
 *       this batch follows throughout.
 * </ul>
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class PresetBatch5GameTests {

    /** The batch 5 roster that actually ships, issue #837's roster minus the skips documented above. */
    private static final String[] BATCH_5_MATERIALS = {
            "black_quartz", "restonia_crystal", "palis_crystal", "diamatine_crystal", "void_crystal",
            "emeradic_crystal", "enori_crystal",
            "psimetal", "psigem", "ivory_psimetal", "ebony_psimetal",
            "uraninite",
            "pink_slime",
            "cyanite", "blutonium", "ludicrite",
    };

    @GameTest(template = "empty")
    public static void unsuppliedBatch5PresetsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : BATCH_5_MATERIALS) {
            helper.assertTrue(materials.get(materialId(name)) == null,
                    "expected the " + name + " material to be absent without its supplying mod, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private PresetBatch5GameTests() {}
}
