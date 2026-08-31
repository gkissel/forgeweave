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
 * Issue #872 (the M6 recovery batch): the ten materials {@code Material.LENIENT_INGREDIENT_CODEC}'s
 * schema fix unblocks -- ProjectE's dark/red matter, AvaritiaNeo's crystal_matrix/cosmic_neutronium/
 * infinity ladder, Refined Storage's quartz enriched iron and silicon, and Powah's energised steel.
 * (Draconic Evolution's wyvern/chaotic core-tier pair ships alongside the existing draconium pair in
 * {@link DraconicEvolutionGameTests} instead, since that class already covers the mod.)
 *
 * <p>Before this issue, every one of these materials failed to parse at all outside a real,
 * mod-loaded game: {@code Material.CODEC} decoded {@code crafting_items}/{@code repair_item} through
 * vanilla's {@code Ingredient.CODEC}, which resolves a concrete {@code {"item": "..."}} entry via
 * {@code BuiltInRegistries.ITEM.holderByNameCodec()} -- an unregistered id is a hard decode error,
 * not merely an absent tag. {@code Material.LENIENT_INGREDIENT_CODEC} tries an "is this id currently
 * registered" check first and falls back to {@link net.minecraft.world.item.crafting.Ingredient#EMPTY}
 * when it is not, so the material parses fine and its ingredient simply never matches a real item
 * stack -- see {@code MaterialTest#unregisteredConcreteItemIdParsesLenientlyAndNeverMatches} for the
 * codec-level proof.
 *
 * <p>None of {@code projecte}, {@code avaritia}, {@code refinedstorage} or {@code powah} is a
 * build/test dependency (see build.gradle), so every material's {@code neoforge:item_exists}
 * condition fails in this GameTest server exactly as it would in a Forgeweave-only install, the same
 * negative-existence proof every earlier Track A batch's own GameTest class gives. The positive
 * existence path is generic infrastructure already covered by {@code ConditionalMaterialGameTests}.
 *
 * <p>Verified directly against each mod's own 1.21.1 tree, not the epic's summary table alone:
 * ProjectE ({@code sinkillerj/ProjectE}'s {@code mc1.21.1} branch) ships {@code dark_matter}/{@code
 * red_matter} with zero {@code c:} tag coverage; AvaritiaNeo ({@code AquaThree/AvaritiaNeo}'s {@code
 * main} branch, no {@code data/c/} directory at all) ships {@code crystal_matrix_ingot}/{@code
 * neutronium_ingot}/{@code infinity_ingot}; Refined Storage ({@code refinedmods/refinedstorage2}'s
 * {@code develop} branch, modid {@code refinedstorage}) ships {@code quartz_enriched_iron} under only
 * the flat parent {@code c:ingots} tag (so it keys on the concrete id) but {@code silicon} under its
 * own real {@code c:silicon} tag (so it keys on that tag instead, following the "use whichever real
 * tag the mod actually ships" precedent batch 2's HDPE and batch 5's Actually Additions crystals set);
 * Powah ({@code Technici4n/Powah}'s {@code v6.2.10} tree) confirms the epic's {@code energised_steel}
 * id was backwards -- the real id is {@code powah:steel_energized}, already caught in #837/PR #866.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class RecoveryBatchGameTests {

    /** The #872 recovery batch roster, one row per material id. */
    private static final String[] RECOVERY_BATCH_MATERIALS = {
            "dark_matter", "red_matter",
            "crystal_matrix", "cosmic_neutronium", "infinity",
            "quartz_enriched_iron", "silicon",
            "energised_steel",
    };

    @GameTest(template = "empty")
    public static void unsuppliedRecoveryBatchMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : RECOVERY_BATCH_MATERIALS) {
            helper.assertTrue(materials.get(materialId(name)) == null,
                    "expected the " + name + " material to be absent without its supplying mod, found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private RecoveryBatchGameTests() {}
}
