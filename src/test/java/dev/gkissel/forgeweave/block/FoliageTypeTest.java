package dev.gkissel.forgeweave.block;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Every slime foliage colour has to actually ship the colour map {@code SlimeColorizer} asks the
 * resource manager for. A missing one is silent -- the colorizer falls back to the flat tint and
 * every slimy block in the world goes one solid colour -- which is exactly what happened between
 * issue #449 and #450, when the lookup pointed one directory above the derived art.
 */
class FoliageTypeTest {

    @Test
    void everyFoliageColourShipsItsColourMap() {
        for (FoliageType foliage : FoliageType.values()) {
            String resource = "assets/forgeweave/" + foliage.colormapPath();
            assertTrue(FoliageTypeTest.class.getClassLoader().getResource(resource) != null,
                    foliage + " has no colour map at " + resource);
        }
    }
}
