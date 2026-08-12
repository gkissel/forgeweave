package dev.gkissel.forgeweave.tool;

import java.util.List;
import java.util.Set;

/**
 * Where an assembled tool's layer art lives, and in what order the layers stack. One place, because
 * three unrelated callers have to agree on it: {@code ForgeweaveItemModelProvider} writes the item
 * model, {@code ForgeweaveItemColors} tints layer <i>n</i> with the material of the part that layer
 * shows, and {@code ToolStationScreen} blits the same files by hand for its oversized preview and
 * its sidebar icons.
 *
 * <p>Layer order is upstream 1.12's own ({@code models/item/tools/*.tcon.json}: layer0 = handle,
 * layer1 = head, layer2 = the extra part) and is the same for every tool, which is what lets the
 * tint mapping be a single table rather than a per-tool one. A tool with no extra part -- battlesign,
 * frying pan, dagger (issue #155) -- simply has no layer2, exactly as upstream's own two-layer
 * {@code battlesign.tcon.json}/{@code frypan.tcon.json} do.
 */
public final class ToolArt {

    /** Layer suffixes in model order; a tool uses the first {@code n} that its parts cover. */
    public static final List<String> LAYERS = List.of("handle", "head", "binding");

    /**
     * Tools whose layer art is freshly authored rather than derived from the clone, so it lives in
     * {@code textures/tools/} instead of {@code textures/derived/tools/} (CLAUDE.md's derived-art
     * rule; M9 empties the derived tree). The dagger is a shape from upstream's modern branch with
     * no 1.12 art to derive and a recorded no-copy deviation on issue #155, so it is drawn here.
     */
    private static final Set<String> ORIGINAL_ART = Set.of("dagger");

    /**
     * The texture path (no {@code .png}, no namespace) of one layer of one tool.
     *
     * @param tool the tool item's registry path, e.g. {@code "broadsword"}
     * @param layer one of {@link #LAYERS}
     */
    public static String layer(String tool, String layer) {
        return (ORIGINAL_ART.contains(tool) ? "tools/" : "derived/tools/") + tool + "_" + layer;
    }

    private ToolArt() {}
}
