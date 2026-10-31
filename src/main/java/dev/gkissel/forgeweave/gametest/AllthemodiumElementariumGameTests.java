package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import dev.gkissel.forgeweave.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.gametest.GameTestHolder;
import dev.gkissel.forgeweave.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.config.ForgeweaveConfigCondition;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.material.CompatMaterialAvailability;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.worldgen.TrackBOrePlacement;

/**
 * The two D-M8-19 (issue #998) compat toggles' off paths, following {@code CompatToggleGameTests}'
 * own shape: neither integration mod is present in {@code runGameTestServer}, so each is read at a
 * site that needs no mod-free half to exist at all -- {@link TrackBOrePlacement#allowed} is a pure
 * function of a dimension key, and {@link ForgeweaveConfigCondition} is a config read that, inside
 * a running GameTest server, always takes its {@code ForgeweaveConfig.loaded()} branch. The other
 * branch -- what it reads before the spec has loaded, which is where {@code elementariumMaterials}
 * actually spends its first few seconds every boot -- is {@code ForgeweaveConfigConditionTest}'s
 * subject, not this file's: a GameTest server has no "spec not loaded yet" moment to reach.
 *
 * <p>Kept out of {@code CompatToggleGameTests} itself: that file is a hot spot for the several other
 * PRs landing compat toggles in this milestone (D-M8-5), so a new toggle gets a new file instead of
 * another concurrent editor of the same one.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class AllthemodiumElementariumGameTests {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));

    /**
     * {@code allthemodiumTiers} off stops Track B's ore family generating in
     * {@code allthemodium:mining} specifically, while leaving every other dimension's generation
     * (governed by {@code genTrackBOres} alone) untouched -- proven by flipping the toggle and
     * checking both dimensions rather than only the one it targets, so a leak into the Overworld
     * would fail here too.
     */
    @GameTest(template = "empty")
    public static void allthemodiumTiersOffStopsOnlyTheMiningDimension(GameTestHelper helper) {
        helper.assertTrue(TrackBOrePlacement.allowed(TrackBOrePlacement.ALLTHEMODIUM_MINING),
                "generation is allowed in the mining dimension while allthemodiumTiers is on, or this "
                        + "test proves nothing");
        helper.assertTrue(TrackBOrePlacement.allowed(OVERWORLD),
                "generation is allowed in the Overworld while allthemodiumTiers is on, or this test "
                        + "proves nothing");

        ForgeweaveConfig.ALLTHEMODIUM_TIERS.set(false);
        try {
            helper.assertFalse(TrackBOrePlacement.allowed(TrackBOrePlacement.ALLTHEMODIUM_MINING),
                    "no generation may be allowed in the mining dimension while allthemodiumTiers is off");
            helper.assertTrue(TrackBOrePlacement.allowed(OVERWORLD),
                    "the Overworld must be unaffected -- allthemodiumTiers governs only the mining "
                            + "dimension, genTrackBOres governs everywhere else");
        } finally {
            ForgeweaveConfig.ALLTHEMODIUM_TIERS.set(true);
        }

        helper.assertTrue(TrackBOrePlacement.allowed(TrackBOrePlacement.ALLTHEMODIUM_MINING),
                "turning allthemodiumTiers back on must allow generation again with no reload");
        helper.succeed();
    }

    /**
     * {@code genTrackBOres} off still wins over {@code allthemodiumTiers}: the mining dimension is
     * never a back door around the group switch every other Track B dimension already respects.
     */
    @GameTest(template = "empty")
    public static void genTrackBOresOffStillWinsInTheMiningDimension(GameTestHelper helper) {
        ForgeweaveConfig.GEN_TRACK_B_ORES.set(false);
        try {
            helper.assertFalse(TrackBOrePlacement.allowed(TrackBOrePlacement.ALLTHEMODIUM_MINING),
                    "genTrackBOres off must stop the mining dimension too, even with allthemodiumTiers "
                            + "left on");
        } finally {
            ForgeweaveConfig.GEN_TRACK_B_ORES.set(true);
        }
        helper.succeed();
    }

    /**
     * {@code elementariumMaterials} off makes {@code forgeweave:compat_toggle("elementariumMaterials")}
     * answer false, so none of the generated presets register; on restores it with no reload. {@code
     * neoforge:mod_loaded("elementarium")}, the other half of every generated preset's condition, is
     * not this toggle's concern and is not tested here. The spec is loaded throughout a GameTest
     * server, so this exercises {@link ForgeweaveConfigCondition}'s {@code ForgeweaveConfig.loaded()}
     * branch only -- see this class's own javadoc for where the other branch is tested.
     */
    @GameTest(template = "empty")
    public static void elementariumMaterialsOffDisablesTheCondition(GameTestHelper helper) {
        ForgeweaveConfigCondition condition = new ForgeweaveConfigCondition("elementariumMaterials");

        helper.assertTrue(condition.test(null),
                "the condition answers true while elementariumMaterials is on, or this test proves nothing");

        ForgeweaveConfig.ELEMENTARIUM_MATERIALS.set(false);
        try {
            helper.assertFalse(condition.test(null), "the condition must answer false while "
                    + "elementariumMaterials is off");
        } finally {
            ForgeweaveConfig.ELEMENTARIUM_MATERIALS.set(true);
        }

        helper.assertTrue(condition.test(null),
                "turning elementariumMaterials back on must answer true again with no reload");
        helper.succeed();
    }

    /**
     * The 1.0.0 cast-only fix gave the Elementarium and Allthemodium presets (and the Mystical
     * Agriculture metals) a molten fluid and a bucket, registered whether or not the mod is there.
     * None of those mods runs on the GameTest server, so each fluid must exist, its material must
     * not, and its bucket must stay out of the creative tab and JEI.
     */
    @GameTest(template = "empty")
    public static void unsuppliedCastOnlyPresetsKeepTheirFluidButHideTheirBucket(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().lookupOrThrow(Material.REGISTRY);
        for (String name : List.of("elementarium_vanadium", "elementarium_chromium", "elementarium_molybdenum",
                "elementarium_palladium", "elementarium_hafnium", "elementarium_tantalum",
                "allthemodium", "vibranium", "unobtainium", "vibranium_allthemodium_alloy",
                "unobtainium_allthemodium_alloy", "unobtainium_vibranium_alloy",
                "inferium", "prudentium", "tertium", "imperium", "supremium", "awakened_supremium",
                "insanium", "prosperity", "soulium")) {
            helper.assertTrue(ForgeweaveFluids.compatMetalFluid(name) != null, name + " has no registered molten fluid");
            helper.assertTrue(materials.getValue(Identifier.fromNamespaceAndPath(Forgeweave.MODID, name)) == null,
                    name + " registered with its mod absent");
            helper.assertFalse(CompatMaterialAvailability.isAvailable(name),
                    name + "'s bucket would show in the creative tab with its mod absent");
        }
        helper.succeed();
    }
}
