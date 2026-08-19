package dev.gkissel.forgeweave.block;

import java.util.Locale;

/**
 * The colour a piece of slimy foliage is tinted with -- upstream 1.12's
 * {@code BlockSlimeGrass.FoliageType} (NOTICE.md), all three of its colours: the two the overworld
 * slime island generates (issue #449, parity audit T18) and the orange of the Nether magma island
 * (issue #450, parity audit T19).
 *
 * <p>{@link #color()} is upstream's {@code SlimeColorizer#colorBlue}/{@code colorPurple}, the
 * position-independent tint it paints inventory and particle renders with; {@link #colormap()} names
 * the 256x256 colour map {@code SlimeColorizer} samples for the position-dependent, mottled world
 * tint, derived byte-for-byte from the clone (NOTICE.md). Both live here rather than in the client
 * colorizer so the enum stays the single source of truth for "what colour is this foliage".
 */
public enum FoliageType {
    /** Upstream {@code FoliageType.BLUE}: the default island's grass, and the purple island's trees. */
    BLUE(0x2aec81, "slime_grass_blue"),
    /** Upstream {@code FoliageType.PURPLE}: the purple island's grass, and the default island's trees. */
    PURPLE(0xa92dff, "slime_grass_purple"),
    /** Upstream {@code FoliageType.ORANGE}: the Nether magma island's grass, trees and plants. */
    ORANGE(0xd09800, "slime_grass_orange");

    private final int color;
    private final String colormap;

    FoliageType(int color, String colormap) {
        this.color = color;
        this.colormap = colormap;
    }

    /** Upstream's position-independent tint for this foliage ({@code SlimeColorizer#getColorStatic}). */
    public int color() {
        return color;
    }

    /** File name (without extension) of this foliage's colour map under {@code textures/derived/colormap/}. */
    public String colormap() {
        return colormap;
    }

    /**
     * Resource path of this foliage's colour map, relative to the {@code forgeweave} namespace --
     * what {@code SlimeColorizer} asks the resource manager for. Lives here rather than in the
     * colorizer so a plain unit test can check the file it names actually ships
     * ({@code FoliageTypeTest}); #449 shipped it pointing one directory above the derived art, which
     * silently fell back to the flat {@link #color()} for every block in the world.
     */
    public String colormapPath() {
        return "textures/derived/colormap/" + colormap + ".png";
    }

    /** Lowercase name, used as the registry-id prefix of every block tinted with this foliage. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
