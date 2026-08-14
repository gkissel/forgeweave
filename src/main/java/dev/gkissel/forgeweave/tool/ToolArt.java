package dev.gkissel.forgeweave.tool;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where an assembled tool's layer art lives, and in what order the layers stack. One place, because
 * three unrelated callers have to agree on it: {@code ForgeweaveItemModelProvider} writes the item
 * model, {@code ForgeweaveItemColors} tints layer <i>n</i> with the material of the part that layer
 * shows, and {@code ToolStationScreen} blits the same files by hand for its oversized preview and
 * its sidebar icons.
 *
 * <p>There is exactly one layer per part slot, and a layer's name comes from the <em>role</em> of the
 * slot it draws -- {@code handle}, {@code head}, {@code binding} for
 * {@link ToolConstants.Role#HANDLE}/{@code HEAD}/{@code EXTRA}.
 *
 * <p>The layers stack in {@link #LAYER_ORDER}: every handle first, then every head, then the extra
 * part. That is upstream 1.12's own order ({@code models/item/tools/*.tcon.json}: layer0 = handle,
 * layer1 = head, layer2 = the extra part) and it is a <em>drawing</em> order -- the handle has to be
 * painted behind the head whatever slot it happens to occupy. So it is deliberately not the tool's
 * own part order, which several M3 tools do not share: the vein hammer's parts arrive head, handle,
 * binding, head (issue #157), and drawing them in that order would put its haft over its head.
 * {@link #layerSlots} is the mapping between the two, and every caller that has to line a layer up
 * with the part it shows goes through it.
 *
 * <p>A role that appears more than once gets a numeric suffix from the second occurrence on:
 * {@code head}, {@code head2}, {@code head3}. Upstream does the same thing under different names --
 * its battleaxe is {@code handle/backhead/fronthead/binding} and its hammer {@code handle/head/
 * frontplate/backplate} -- and a name derived from the slot's position could not express either,
 * since the second head of a battleaxe and the binding of a broadsword both sit at slot index 2.
 *
 * <p>A tool with no extra part -- battlesign, frying pan, dagger (issue #155) -- simply has no
 * {@code binding} layer, exactly as upstream's own two-layer {@code battlesign.tcon.json}/{@code
 * frypan.tcon.json} do.
 */
public final class ToolArt {

    /** Back-to-front drawing order of the roles; see the class javadoc. */
    private static final List<ToolConstants.Role> LAYER_ORDER =
            List.of(ToolConstants.Role.HANDLE, ToolConstants.Role.HEAD, ToolConstants.Role.EXTRA);

    /** The layer name each part role draws under; see the class javadoc. */
    private static final Map<ToolConstants.Role, String> ROLE_LAYERS = new EnumMap<>(Map.of(
            ToolConstants.Role.HANDLE, "handle",
            ToolConstants.Role.HEAD, "head",
            ToolConstants.Role.EXTRA, "binding"));

    /**
     * The part slot each model layer draws, in layer order -- the one place the art's back-to-front
     * order and the tool's own part order are reconciled. {@code layerSlots(parts).get(n)} is the
     * index into {@link ToolConstants.Entry#parts()} (and so into {@code ToolMaterials#parts()}) of
     * the part that {@code layer<n>} shows, which is what tints it.
     */
    public static List<Integer> layerSlots(List<ToolConstants.PartSlot> parts) {
        List<Integer> slots = new ArrayList<>(parts.size());
        for (ToolConstants.Role role : LAYER_ORDER) {
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).role() == role) {
                    slots.add(i);
                }
            }
        }
        return List.copyOf(slots);
    }

    /**
     * One layer name per model layer, in {@link #layerSlots} order -- what {@link #layer} takes as
     * its second argument, and the list whose size is the tool's model layer count.
     */
    public static List<String> layers(List<ToolConstants.PartSlot> parts) {
        Map<ToolConstants.Role, Integer> seen = new EnumMap<>(ToolConstants.Role.class);
        List<String> names = new ArrayList<>(parts.size());
        for (int slot : layerSlots(parts)) {
            ToolConstants.Role role = parts.get(slot).role();
            int occurrence = seen.merge(role, 1, Integer::sum);
            names.add(occurrence == 1 ? ROLE_LAYERS.get(role) : ROLE_LAYERS.get(role) + occurrence);
        }
        return List.copyOf(names);
    }

    /**
     * The texture path (no {@code .png}, no namespace) of one layer of one tool.
     *
     * @param tool the tool item's registry path, e.g. {@code "broadsword"}
     * @param layer one of the names {@link #layers} produced for that tool
     */
    public static String layer(String tool, String layer) {
        return "derived/tools/" + tool + "_" + layer;
    }

    /**
     * The one layer each tool swaps for broken art once its {@code BROKEN} component is set (issue
     * #284) -- upstream 1.12's own choice, tool for tool. Its {@code models/item/tools/*.tcon.json}
     * declare a {@code broken<N>} texture beside their {@code layer<N>} keys and {@code
     * BakedToolModel#getOverrides} swaps exactly that one layer on {@code ToolHelper#isBroken}; every
     * upstream tool names its head/blade layer there except the hammer, which names its handle. The
     * five tools with no 1.12 counterpart -- dagger, katana, scimitar (#159/#198), vein hammer (#157)
     * and war mace (#161) -- follow that dominant rule and break their head.
     *
     * <p>Deliberately keyed by layer <em>name</em> rather than index: upstream's {@code broken1} is
     * its battleaxe's backhead, its scythe's head and its pickaxe's head, but Forgeweave's scythe
     * draws its second handle (upstream's "accessory") at layer1, so only the name survives the
     * re-ordering {@link #layerSlots} does.
     */
    private static final Map<String, String> BROKEN_LAYERS = Map.ofEntries(
            Map.entry("battleaxe", "head"),
            Map.entry("battlesign", "head"),
            Map.entry("broadsword", "head"),
            Map.entry("cleaver", "head"),
            Map.entry("dagger", "head"),
            Map.entry("excavator", "head"),
            Map.entry("frying_pan", "head"),
            Map.entry("hammer", "handle"),
            Map.entry("hatchet", "head"),
            Map.entry("kama", "head"),
            Map.entry("katana", "head"),
            Map.entry("longsword", "head"),
            Map.entry("lumberaxe", "head"),
            Map.entry("mattock", "head"),
            Map.entry("pickaxe", "head"),
            Map.entry("rapier", "head"),
            Map.entry("scimitar", "head"),
            Map.entry("scythe", "head"),
            Map.entry("shovel", "head"),
            Map.entry("vein_hammer", "head"),
            Map.entry("warmace", "head"));

    /**
     * The layer name {@code tool} draws broken art for, or {@code null} if it has none; see
     * {@link #BROKEN_LAYERS}.
     */
    public static String brokenLayer(String tool) {
        return BROKEN_LAYERS.get(tool);
    }

    /** The texture path of a layer's broken variant -- {@link #layer} with a {@code _broken} suffix. */
    public static String brokenLayerTexture(String tool, String layer) {
        return layer(tool, layer) + "_broken";
    }

    private ToolArt() {}
}
