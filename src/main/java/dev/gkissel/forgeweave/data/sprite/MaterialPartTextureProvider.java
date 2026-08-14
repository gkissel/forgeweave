package dev.gkissel.forgeweave.data.sprite;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.imageio.ImageIO;

import com.google.common.hash.Hashing;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Datagen stage of the greyscale texture pipeline (issue #280): for every part x material pair in
 * {@link MaterialPartSprites}, reads the greyscale base sprite from
 * {@code assets/forgeweave/textures/derived/item/}, recolors it through the material's
 * {@link GreyToColorMapping} palette, and writes the tinted sprite to
 * {@code assets/forgeweave/textures/staging/part/} in the generated resources. This is the
 * ahead-of-time replacement for the runtime single-color ItemColor tint (ADR-0002,
 * {@code ForgeweaveItemColors}): a multi-stop ramp per material instead of one flat multiply.
 *
 * <p>Port of the 1.20 upstream's {@code library/client/data/material/MaterialPartTextureGenerator}
 * plus its {@code GenericTextureGenerator} save plumbing (NOTICE.md), with the moving parts
 * Forgeweave does not need yet cut down: no stat-type/fallback sprite resolution (parts are named
 * directly), no animation metadata, and {@link BufferedImage}/{@link ImageIO} instead of
 * NativeImage so the recolor itself ({@link GreyToColorMapping}) stays unit-testable. PNG bytes
 * from ImageIO are deterministic for a given raster, so reruns are byte-stable and the committed
 * output diffs only when a base sprite or palette actually changes.
 */
public final class MaterialPartTextureProvider implements DataProvider {

    private final PackOutput.PathProvider pathProvider;
    private final ExistingFileHelper existingFileHelper;

    public MaterialPartTextureProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        this.pathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
        this.existingFileHelper = existingFileHelper;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.runAsync(() -> {
            try {
                for (String part : MaterialPartSprites.PARTS) {
                    BufferedImage base = readBase(part);
                    for (Map.Entry<String, GreyToColorMapping> palette : MaterialPartSprites.PALETTES.entrySet()) {
                        BufferedImage tinted = palette.getValue().recolor(base);
                        ResourceLocation output = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID,
                                MaterialPartSprites.outputName(part, palette.getKey()));
                        byte[] bytes = encodePng(tinted);
                        cache.writeIfNeeded(pathProvider.file(output, "png"), bytes, Hashing.sha1().hashBytes(bytes));
                    }
                }
            } catch (IOException e) {
                throw new CompletionException("Failed to generate material part textures", e);
            }
        });
    }

    /** Reads a greyscale base sprite from the existing (main) resources. */
    private BufferedImage readBase(String part) throws IOException {
        ResourceLocation base = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/item/" + part);
        try (InputStream stream = existingFileHelper
                .getResource(base, PackType.CLIENT_RESOURCES, ".png", "textures").open()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IOException("Could not decode greyscale base sprite " + base);
            }
            return image;
        }
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    @Override
    public String getName() {
        return "Forgeweave Material Part Textures";
    }
}
