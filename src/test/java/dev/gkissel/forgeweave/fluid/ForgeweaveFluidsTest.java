package dev.gkissel.forgeweave.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.pathfinder.PathType;

import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Pins the nine molten metal fluids' temperatures against upstream 1.12's {@code
 * TinkerFluids#setupFluids} constants (docs/SCOPE.md M2 issue #92; NOTICE.md), and rose
 * gold/netherite/netherite scrap's maintainer-picked deviation values (see {@link
 * ForgeweaveFluids}'s class javadoc). Exercises {@link ForgeweaveFluids#moltenFluidType} directly
 * rather than the {@code DeferredHolder}-backed {@code MoltenMetal} constants' {@code .get()} --
 * this needs no more than the same Minecraft bootstrap the rest of the test suite already uses.
 */
class ForgeweaveFluidsTest {

    /** Ported 1:1 from upstream's {@code TinkerFluids#setupFluids} (NOTICE.md); the last three have no 1.12 counterpart. */
    private static final Map<String, Integer> EXPECTED_TEMPERATURES = Map.of(
            "iron", 769,
            "copper", 542,
            "gold", 532,
            "cobalt", 950,
            "ardite", 860,
            "manyullyn", 1000,
            "rose_gold", 550,
            "netherite_scrap", 1100,
            "netherite", 1200);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @ParameterizedTest
    @MethodSource("catalog")
    void temperatureIsQueryableAndMatchesExpected(String metalId, ForgeweaveFluids.MoltenMetal metal) {
        int expected = EXPECTED_TEMPERATURES.get(metalId);

        assertEquals(expected, metal.temperature(), metalId + "'s MoltenMetal.temperature() should match the expected constant");

        FluidType type = ForgeweaveFluids.moltenFluidType(metal.temperature());
        assertEquals(expected, type.getTemperature(), metalId + "'s FluidType.getTemperature() should be queryable and match the expected constant");
    }

    @Test
    void alloysRunHotterThanTheirComponents() {
        // Same pattern upstream uses (manyullyn 1000 > cobalt 950 / ardite 860): an alloy's
        // temperature is higher than either of its inputs, both for the ported and the deviated
        // fluids (see ForgeweaveFluids's class javadoc).
        assertTrue(ForgeweaveFluids.MANYULLYN.temperature() > ForgeweaveFluids.COBALT.temperature());
        assertTrue(ForgeweaveFluids.MANYULLYN.temperature() > ForgeweaveFluids.ARDITE.temperature());
        assertTrue(ForgeweaveFluids.NETHERITE.temperature() > ForgeweaveFluids.NETHERITE_SCRAP.temperature());
        assertTrue(ForgeweaveFluids.NETHERITE.temperature() > ForgeweaveFluids.GOLD.temperature());
    }

    private static Stream<Arguments> catalog() {
        return Stream.of(
                Arguments.of("iron", ForgeweaveFluids.IRON),
                Arguments.of("copper", ForgeweaveFluids.COPPER),
                Arguments.of("gold", ForgeweaveFluids.GOLD),
                Arguments.of("cobalt", ForgeweaveFluids.COBALT),
                Arguments.of("ardite", ForgeweaveFluids.ARDITE),
                Arguments.of("manyullyn", ForgeweaveFluids.MANYULLYN),
                Arguments.of("rose_gold", ForgeweaveFluids.ROSE_GOLD),
                Arguments.of("netherite_scrap", ForgeweaveFluids.NETHERITE_SCRAP),
                Arguments.of("netherite", ForgeweaveFluids.NETHERITE));
    }

    // ------------------------------------------------------------------ #285 fluid physics parity

    /**
     * #285: molten fluids need lava's entity hazard behavior -- both upstream generations do this
     * (1.12's {@code BlockMolten} extends {@code BlockTinkerFluid} with {@code Material.LAVA}; 1.20's
     * {@code TinkerFluids#hot} builds the equivalent modern property set). {@code FluidType}'s
     * entity-facing accessors ({@code canSwim}, {@code canDrownIn}, {@code getBlockPathType}) ignore
     * their entity/level/pos arguments and just return the configured property, so {@code null} is
     * safe to pass here without a live level or entity.
     */
    @Test
    void moltenFluidTypeHasLavaLikeEntityBehavior() {
        FluidType type = ForgeweaveFluids.moltenFluidType(1000);

        assertFalse(type.canSwim(null), "molten fluid should be un-swimmable like lava");
        assertFalse(type.canDrownIn(null), "molten fluid should not be drownable like water -- lava damages instead");
        assertEquals(PathType.LAVA, type.getBlockPathType(null, null, null, null, false),
                "mobs should path around molten fluid the same way they avoid lava");
        assertEquals(SoundEvents.BUCKET_FILL_LAVA, type.getSound(SoundActions.BUCKET_FILL));
        assertEquals(SoundEvents.BUCKET_EMPTY_LAVA, type.getSound(SoundActions.BUCKET_EMPTY));
    }

    /** #285: obsidian and seared stone ride the stone still/flowing texture pair (molten clay already does), not the shared metal texture. */
    @Test
    void obsidianAndSearedStoneUseStoneTextures() {
        ResourceLocation stoneStill = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid_stone");
        ResourceLocation stoneFlowing = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid_stone_flow");
        ResourceLocation metalStill = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/molten_metal");

        assertEquals(stoneStill, ForgeweaveFluids.OBSIDIAN.stillTexture());
        assertEquals(stoneFlowing, ForgeweaveFluids.OBSIDIAN.flowingTexture());
        assertEquals(stoneStill, ForgeweaveFluids.SEARED_STONE.stillTexture());
        assertEquals(stoneFlowing, ForgeweaveFluids.SEARED_STONE.flowingTexture());
        assertNotEquals(metalStill, ForgeweaveFluids.OBSIDIAN.stillTexture());
        assertNotEquals(metalStill, ForgeweaveFluids.SEARED_STONE.stillTexture());
    }

    /**
     * #502 (T71): molten dirt is upstream's third {@code fluidStone} fluid alongside obsidian and
     * molten clay (TinkerFluids#dirt, 0xa68564, temperature 500) -- same stone texture pair, same
     * lava-tier {@link ForgeweaveFluids#moltenFluidType}, not the shared metal texture.
     */
    @Test
    void moltenDirtMatchesUpstreamTemperatureAndUsesStoneTextures() {
        ResourceLocation stoneStill = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid_stone");
        ResourceLocation stoneFlowing = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid_stone_flow");

        assertEquals(500, ForgeweaveFluids.MOLTEN_DIRT.temperature());
        assertEquals(stoneStill, ForgeweaveFluids.MOLTEN_DIRT.stillTexture());
        assertEquals(stoneFlowing, ForgeweaveFluids.MOLTEN_DIRT.flowingTexture());
    }

    /**
     * #473 (T42): molten glass is a {@code fluidMetal} upstream ({@code TinkerFluids#setupFluids}:
     * {@code fluidMetal("glass", 0xc0f5fe)}, {@code setTemperature(625)}), so unlike obsidian and
     * molten dirt it rides the shared tinted metal texture pair rather than the stone one.
     */
    @Test
    void moltenGlassMatchesUpstreamTemperatureAndUsesTheSharedMetalTextures() {
        ResourceLocation metalStill = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/molten_metal");
        ResourceLocation metalFlowing = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/molten_metal_flow");

        assertEquals(625, ForgeweaveFluids.GLASS.temperature());
        assertEquals(0xC0F5FE, ForgeweaveFluids.GLASS.color());
        assertEquals(metalStill, ForgeweaveFluids.GLASS.stillTexture());
        assertEquals(metalFlowing, ForgeweaveFluids.GLASS.flowingTexture());
    }

    /**
     * #285: the placed fluid block's light level derives from the fluid's own {@code
     * FluidType#getLightLevel()} instead of a hardcoded 10, so blood -- whose {@code FluidType}
     * never calls {@code lightLevel()}, leaving the property at its 0 default -- renders
     * non-glowing, as {@link ForgeweaveFluids}'s field comment on {@code BLOOD} documents.
     */
    @Test
    void liquidBlockLightLevelDerivesFromFluidType() {
        assertEquals(10, ForgeweaveFluids.IRON.block().get().defaultBlockState().getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                "molten metal keeps its lit-lava-like light level");
        assertEquals(0, ForgeweaveFluids.BLOOD.block().get().defaultBlockState().getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                "blood's FluidType has no configured light level, so its block should not glow");
    }

    /**
     * #285: molten slime is de-tuned from the shared lava-tier density/viscosity, the same way
     * upstream's {@code TinkerFluids#slime} builder (density 1600 / viscosity 1600) departs from
     * {@code hot()}'s lava-tier density 2000 / viscosity 10000.
     */
    @Test
    void slimeFluidTypeIsDeTunedFromLavaTierDensityAndViscosity() {
        FluidType slimeType = ForgeweaveFluids.SLIME.fluidType().get();

        assertEquals(1600, slimeType.getDensity());
        assertEquals(1600, slimeType.getViscosity());
        assertNotEquals(ForgeweaveFluids.moltenFluidType(310).getDensity(), slimeType.getDensity(),
                "slime should not share the lava-tier density every other molten fluid uses");
    }
}
