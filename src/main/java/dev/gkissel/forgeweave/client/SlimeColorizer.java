package dev.gkissel.forgeweave.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.FoliageType;

/**
 * The mottled colour slimy foliage takes at a given position: upstream 1.12's {@code SlimeColorizer}
 * (NOTICE.md), ported whole (issue #449, parity audit T18).
 *
 * <p>Upstream samples one 256x256 colour map per foliage colour with a coordinate pattern that
 * mirrors and loops every 256 blocks, so an island's grass shades across its surface instead of
 * being one flat colour. Both maps are derived from the clone byte-for-byte and read straight off
 * the resource manager here, exactly as upstream's own resource-reload listener does -- they are
 * lookup tables, not sprites, so they live outside the block atlas.
 *
 * <p>{@link #colorAt} is upstream's {@code getColorForPos} and {@link FoliageType#color()} its
 * {@code getColorStatic}, the flat colour it falls back to for inventory renders. When a map has not
 * loaded (a resource pack removed it, or an item is drawn before the first reload), the flat colour
 * stands in rather than a black or magenta hole.
 */
public final class SlimeColorizer {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Upstream {@code SlimeColorizer.loop}: the pattern repeats every 256 blocks on each axis. */
    private static final float LOOP = 256.0F;

    private static final Map<FoliageType, int[]> COLOR_MAPS = new EnumMap<>(FoliageType.class);

    private SlimeColorizer() {}

    /** Upstream {@code getColorForPos}: the foliage colour this block position samples. */
    public static int colorAt(FoliageType foliage, BlockPos pos) {
        int[] map = COLOR_MAPS.get(foliage);
        if (map == null) {
            return foliage.color();
        }
        float x = Math.abs((LOOP - Math.abs(pos.getX()) % (2 * LOOP)) / LOOP);
        float z = Math.abs((LOOP - Math.abs(pos.getZ()) % (2 * LOOP)) / LOOP);
        if (x < z) {
            float swap = x;
            x = z;
            z = swap;
        }
        return map[(int) (x * 255f) << 8 | (int) (z * 255f)] & 0xffffff;
    }

    /** Re-reads both colour maps; called from {@code ForgeweaveFoliageColors}' reload listener. */
    public static void reload(ResourceManager resourceManager) {
        COLOR_MAPS.clear();
        for (FoliageType foliage : FoliageType.values()) {
            ResourceLocation location =
                    ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, foliage.colormapPath());
            resourceManager.getResource(location).ifPresent(resource -> {
                try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
                    int[] pixels = new int[image.getWidth() * image.getHeight()];
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            // NativeImage stores ABGR; the colour map is read as RGB, same as upstream's
                            // TextureUtil.readImageData does.
                            int abgr = image.getPixelRGBA(x, y);
                            int rgb = (abgr & 0x0000ff) << 16 | (abgr & 0x00ff00) | (abgr & 0xff0000) >> 16;
                            pixels[y * image.getWidth() + x] = rgb;
                        }
                    }
                    COLOR_MAPS.put(foliage, pixels);
                } catch (IOException e) {
                    LOGGER.error("could not read the slime foliage color map {}", location, e);
                }
            });
        }
    }

    /** Convenience for callers with no resource manager to hand (the color handlers). */
    public static void reload() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            reload(minecraft.getResourceManager());
        }
    }
}
