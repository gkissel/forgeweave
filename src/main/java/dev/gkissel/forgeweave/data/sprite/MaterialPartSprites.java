package dev.gkissel.forgeweave.data.sprite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The proof slice for the greyscale texture pipeline (issue #280): which greyscale part sprites
 * get recolored, and each material's color ramp. The full material x part cross product is M9's
 * mass conversion (#30); this slice exists to prove the pipeline end to end and to anchor the
 * regression test, so it stays deliberately small.
 *
 * <p>The palettes are the 1.20 upstream's hand-tuned ramps for the same materials, from
 * {@code tools/data/sprite/TinkerMaterialSpriteProvider.java} (NOTICE.md) -- six stops above
 * black at greys 63/102/140/178/216/255. They are per-material data, not code, which is why
 * they live here rather than in the provider: the JUnit regression test replays exactly this
 * slice without touching any Minecraft class.
 *
 * <p>Pure Java on purpose -- see {@link GreyToColorMapping}. The datagen side is
 * {@link MaterialPartTextureProvider}.
 */
public final class MaterialPartSprites {

    /**
     * Greyscale base sprites to recolor, by name under
     * {@code assets/forgeweave/textures/derived/item/}. One head and one handle cover the two
     * part-art shapes (solid silhouette vs. thin rod) without dragging in the whole part roster.
     */
    public static final List<String> PARTS = List.of("pickaxe_head", "tool_handle");

    /** Material name -> color ramp, insertion-ordered so generation and test order match. */
    public static final Map<String, GreyToColorMapping> PALETTES = palettes();

    private static Map<String, GreyToColorMapping> palettes() {
        Map<String, GreyToColorMapping> palettes = new LinkedHashMap<>();
        palettes.put("wood", GreyToColorMapping.builderFromBlack()
                .addARGB(63, 0xFF281E0B).addARGB(102, 0xFF493615).addARGB(140, 0xFF584014)
                .addARGB(178, 0xFF684E1E).addARGB(216, 0xFF785A22).addARGB(255, 0xFF896727).build());
        palettes.put("stone", GreyToColorMapping.builderFromBlack()
                .addARGB(63, 0xFF181818).addARGB(102, 0xFF494949).addARGB(140, 0xFF5A5A5A)
                .addARGB(178, 0xFF787777).addARGB(216, 0xFF95918D).addARGB(255, 0xFFB3B1AF).build());
        palettes.put("iron", GreyToColorMapping.builderFromBlack()
                .addARGB(63, 0xFF353535).addARGB(102, 0xFF5E5E5E).addARGB(140, 0xFF828282)
                .addARGB(178, 0xFFA8A8A8).addARGB(216, 0xFFD8D8D8).addARGB(255, 0xFFFFFFFF).build());
        palettes.put("cobalt", GreyToColorMapping.builderFromBlack()
                .addARGB(63, 0xFF001944).addARGB(102, 0xFF00296D).addARGB(140, 0xFF0043A5)
                .addARGB(178, 0xFF186ACE).addARGB(216, 0xFF338FEA).addARGB(255, 0xFF59A6EF).build());
        // Map.copyOf would lose insertion order; unmodifiable view keeps it.
        return java.util.Collections.unmodifiableMap(palettes);
    }

    /**
     * Texture name of a generated sprite, under {@code textures/staging/part/}. The staging
     * folder keeps pipeline output out of the live model-referenced tree until M9 flips parts
     * over material by material.
     */
    public static String outputName(String part, String material) {
        return "staging/part/" + part + "_" + material;
    }

    private MaterialPartSprites() {}
}
