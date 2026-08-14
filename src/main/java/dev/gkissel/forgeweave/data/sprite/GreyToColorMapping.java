package dev.gkissel.forgeweave.data.sprite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps greyscale pixel values to a material's color ramp, the core of the greyscale texture
 * generation pipeline (issue #280). A palette is an ordered list of (grey, ARGB color) stops;
 * a pixel's grey value (the largest of its RGB channels) is looked up against the stops and
 * linearly interpolated between the two nearest, then any channel below the grey maximum and
 * any partial alpha is scaled back down proportionally, so shading baked into the greyscale
 * base survives the recolor.
 *
 * <p>Port of the 1.20 upstream's
 * {@code library/client/data/spritetransformer/GreyToColorMapping.java} (NOTICE.md), adapted
 * from NativeImage's ABGR ints to plain ARGB ints so it runs on {@link BufferedImage} with no
 * Minecraft classes -- which lets the datagen provider and the JUnit regression test share the
 * exact same code path. The Gson (de)serializer and the generic {@code getNearestByGrey} were
 * dropped: nothing in Forgeweave reads palettes from JSON yet, and the only interpolation
 * consumer is the color lookup itself.
 */
public final class GreyToColorMapping {

    /** Palette stops ordered by ascending grey value; always at least two. */
    private final List<ColorMapping> mappings;
    /** Same 256-entry lookup cache as upstream; a sprite hits few distinct greys. */
    private final Integer[] recolorCache = new Integer[256];

    private GreyToColorMapping(List<ColorMapping> mappings) {
        this.mappings = mappings;
    }

    /** Mapping from a greyscale value to the ARGB color it should become. */
    private record ColorMapping(int grey, int color) {}

    /** Recolors one ARGB pixel. Fully transparent pixels stay fully transparent. */
    public int mapColor(int color) {
        if (alpha(color) == 0) {
            return 0x00000000;
        }
        int grey = getGrey(color);
        return scaleColor(color, getColorForGrey(grey), grey);
    }

    /** Returns a recolored copy of the greyscale base; the base is not modified. */
    public BufferedImage recolor(BufferedImage base) {
        BufferedImage result = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                result.setRGB(x, y, mapColor(base.getRGB(x, y)));
            }
        }
        return result;
    }

    /** Gets the palette color for the given grey value, interpolating between stops. */
    public int getColorForGrey(int grey) {
        if (recolorCache[grey] == null) {
            recolorCache[grey] = computeColorForGrey(grey);
        }
        return recolorCache[grey];
    }

    /**
     * Uncached lookup. Grey values at or below the first stop clamp to it, at or above the last
     * stop clamp to it, and anything between two stops interpolates linearly per channel --
     * the same behavior as upstream's {@code getNearestByGrey} + {@code INTERPOLATE_COLORS}.
     */
    private int computeColorForGrey(int grey) {
        ColorMapping before = mappings.get(0);
        if (grey <= before.grey()) {
            return before.color();
        }
        for (int i = 1; i < mappings.size(); i++) {
            ColorMapping after = mappings.get(i);
            if (grey <= after.grey()) {
                return interpolateColors(before.color(), before.grey(), after.color(), after.grey(), grey);
            }
            before = after;
        }
        return before.color();
    }

    /**
     * Interpolates two numbers with upstream's integer math: {@code a + ((b - a) * x) / divisor}.
     * Kept bit-identical (truncating division included) so a palette produces the same bytes here
     * as it does in the 1.20 upstream's generator.
     */
    static int interpolate(int a, int b, int x, int divisor) {
        return a + (((b - a) * x) / divisor);
    }

    /** Interpolates each channel of two ARGB colors for a grey strictly between their stops. */
    static int interpolateColors(int colorBefore, int greyBefore, int colorAfter, int greyAfter, int grey) {
        int diff = grey - greyBefore;
        int divisor = greyAfter - greyBefore;
        int alpha = interpolate(alpha(colorBefore), alpha(colorAfter), diff, divisor);
        int red = interpolate(red(colorBefore), red(colorAfter), diff, divisor);
        int green = interpolate(green(colorBefore), green(colorAfter), diff, divisor);
        int blue = interpolate(blue(colorBefore), blue(colorAfter), diff, divisor);
        return argb(alpha, red, green, blue);
    }

    /** A pixel's grey value is its largest RGB channel (upstream's convention). */
    static int getGrey(int color) {
        return Math.max(red(color), Math.max(green(color), blue(color)));
    }

    /**
     * Scales the palette color back down wherever the original pixel was not pure grey or fully
     * opaque: each RGB channel below the grey maximum shrinks the mapped channel proportionally,
     * and partial alpha multiplies into the mapped alpha. When {@code grey} is 0 every channel is
     * 0, so the {@code < grey} guards also keep the division safe -- same as upstream.
     */
    static int scaleColor(int original, int newColor, int grey) {
        int alpha = alpha(newColor);
        int originalAlpha = alpha(original);
        if (originalAlpha < 255) {
            alpha = originalAlpha * alpha / 255;
        }
        int red = red(newColor);
        int green = green(newColor);
        int blue = blue(newColor);
        if (red(original) < grey) {
            red = red * red(original) / grey;
        }
        if (green(original) < grey) {
            green = green * green(original) / grey;
        }
        if (blue(original) < grey) {
            blue = blue * blue(original) / grey;
        }
        return argb(alpha, red, green, blue);
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** Creates a new palette builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Creates a new palette builder whose grey 0 maps to opaque black, like most palettes. */
    public static Builder builderFromBlack() {
        return builder().addARGB(0, 0xFF000000);
    }

    /** Builds a palette from ascending (grey, ARGB) stops. */
    public static final class Builder {
        private final List<ColorMapping> stops = new ArrayList<>();
        private int lastGrey = -1;

        /** Adds a stop; grey must be 0-255 and strictly greater than the previous stop's. */
        public Builder addARGB(int grey, int color) {
            if (grey < 0 || grey > 255) {
                throw new IllegalArgumentException("Invalid grey value " + grey + ", must be between 0 and 255, inclusive");
            }
            if (grey <= lastGrey) {
                throw new IllegalArgumentException("Grey value " + grey + " must be greater than the previous value " + lastGrey);
            }
            lastGrey = grey;
            stops.add(new ColorMapping(grey, color));
            return this;
        }

        /** Builds the palette; at least two stops are required. */
        public GreyToColorMapping build() {
            if (stops.size() < 2) {
                throw new IllegalStateException("Too few colors in palette, must have at least 2");
            }
            return new GreyToColorMapping(List.copyOf(stops));
        }
    }
}
