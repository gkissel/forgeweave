package dev.gkissel.forgeweave.tool;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Where an assembled tool's layer art lives, and in what order the layers stack. One place, because
 * three unrelated callers have to agree on it: {@code ForgeweaveItemModelProvider} writes the item
 * model, {@code ForgeweaveItemColors} tints layer <i>n</i> with the material of the part that layer
 * shows, and {@code ToolStationScreen} blits the same files by hand for its oversized preview and
 * its sidebar icons.
 *
 * <p>There is exactly one layer per part slot, and a layer's name comes from the <em>role</em> of the
 * slot it draws -- {@code handle}, {@code head}, {@code binding} for
 * {@link ToolConstants.Role#HANDLE}/{@code HEAD}/{@code EXTRA}, {@code limb}/{@code string} for
 * the bows' {@code LIMB}/{@code BOWSTRING} (M3.5 issue #394: {@code shortbow.tcon.json} is
 * layer0 = limb_top, layer1 = limb_bottom, layer2 = bowstring, so {@code limb}, {@code limb2},
 * {@code string} in that order, string on top), and {@code body} for the crossbow's
 * {@code CROSSBOW_BODY} ({@code crossbow.tcon.json}'s layer0).
 *
 * <p>M3.5 #395 moved {@code LIMB} ahead of {@code EXTRA} in {@link #LAYER_ORDER}, which no
 * pre-existing tool can notice (none has a limb): upstream's {@code longbow.tcon.json} draws its
 * grip <em>over</em> both limbs (layer0/1 = limbs, layer2 = grip, layer3 = bowstring) and its
 * {@code crossbow.tcon.json} draws body, limb, binding, bowstring.
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

    /**
     * Individual {@code <tool>_<layer>} files whose art is freshly authored rather than ported from
     * a clone, and so live under {@code textures/tools/} instead of {@code textures/derived/tools/}
     * (CLAUDE.md keeps the two trees apart so M9 can empty the derived one).
     *
     * <p>Issue #375 made this per *layer* rather than per tool. It used to hold {@code "katana"},
     * the whole tool, because #279 authored all three of the katana's layers from scratch. The
     * maintainer decision on #375 then re-sourced the katana's blade from Spartan Weaponry
     * (Apache-2.0 -- {@code licenses/APACHE-2.0-SpartanWeaponry.txt},
     * {@code scripts/derive_spartan_blade_art.py}), so {@code katana_head} became derived while the
     * two layers below stayed authored: Spartan Weaponry splits its art by render role rather than
     * by tool part, so it has no guard or grip layer to port -- its tsuba is three pixels of a
     * fused body layer and its grip is fixed-colour art that never gets tinted. Those two keep
     * {@code scripts/generate_katana_art.py} and carry no NOTICE.md row.
     */
    private static final Set<String> ORIGINAL_ART = Set.of("katana_binding", "katana_handle");

    /** Back-to-front drawing order of the roles; see the class javadoc. */
    private static final List<ToolConstants.Role> LAYER_ORDER =
            List.of(ToolConstants.Role.HANDLE, ToolConstants.Role.HEAD, ToolConstants.Role.CROSSBOW_BODY,
                    ToolConstants.Role.LIMB, ToolConstants.Role.EXTRA, ToolConstants.Role.BOWSTRING);

    /** The layer name each part role draws under; see the class javadoc. */
    private static final Map<ToolConstants.Role, String> ROLE_LAYERS = new EnumMap<>(Map.of(
            ToolConstants.Role.HANDLE, "handle",
            ToolConstants.Role.HEAD, "head",
            ToolConstants.Role.EXTRA, "binding",
            ToolConstants.Role.LIMB, "limb",
            ToolConstants.Role.BOWSTRING, "string",
            ToolConstants.Role.CROSSBOW_BODY, "body"));

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
        String file = tool + "_" + layer;
        return (ORIGINAL_ART.contains(file) ? "tools/" : "derived/tools/") + file;
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
            Map.entry("crossbow", "string"),
            Map.entry("longbow", "string"),
            Map.entry("shortbow", "string"),
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

    /**
     * How many pull stages a drawn bow has (M3.5 issue #400). Upstream 1.12 gives every bow exactly
     * three: {@code models/item/tools/<bow>.tcon.json} carries three {@code overrides} entries keyed
     * on the vanilla {@code pulling}/{@code pull} item properties, each re-pointing a subset of the
     * model's {@code layer<N>} textures.
     */
    public static final int DRAW_STAGES = 3;

    /**
     * The {@code pull} value each stage's model override tests, read straight off upstream's
     * {@code overrides} blocks. The shortbow's and longbow's first override carries no {@code pull}
     * key at all, which vanilla's "every predicate at or above its threshold" rule reads as the
     * {@code 0} here; the crossbow spells its own {@code 0} out.
     *
     * <p>The crossbow's are its own: it cranks to {@code 0.5} before its limb bends and only counts
     * as fully drawn at {@code 0.999} -- upstream's own threshold, not {@code 1}, so a float that
     * lands a hair under full still reads as full.
     */
    private static final Map<String, float[]> DRAW_THRESHOLDS = Map.of(
            "shortbow", new float[] {0.0f, 0.65f, 0.9f},
            "longbow", new float[] {0.0f, 0.65f, 0.9f},
            "crossbow", new float[] {0.0f, 0.5f, 0.999f});

    /** The stage whose art a loaded crossbow draws; see {@link #hasLoadedState}. */
    public static final int LOADED_STAGE = 3;

    /**
     * {@code tool}'s three {@code pull} thresholds in stage order, or {@code null} if it is not a bow
     * and never renders a draw. A copy: the array is this class's own state.
     */
    @Nullable
    public static float[] drawThresholds(String tool) {
        float[] thresholds = DRAW_THRESHOLDS.get(tool);
        return thresholds == null ? null : thresholds.clone();
    }

    /**
     * The stage {@code tool} draws at draw progress {@code pull} -- 1 to {@link #DRAW_STAGES}, or 0
     * for a tool with no draw art. The last threshold the progress clears wins, which is how vanilla
     * resolves the {@code overrides} list this mirrors, and is why a bow at progress 0 is already at
     * stage 1 (its first threshold is 0; a bow that is not being drawn never gets here, because its
     * {@code pulling} predicate is 0).
     *
     * <p>Its runtime caller is {@code ModifierOverlayModels}, which has to pick the matching staged
     * modifier overlay for whatever stage the model resolved to -- upstream's {@code modifier_suffix}.
     */
    public static int drawStage(String tool, float pull) {
        float[] thresholds = DRAW_THRESHOLDS.get(tool);
        if (thresholds == null) {
            return 0;
        }
        int stage = 0;
        for (int i = 0; i < thresholds.length; i++) {
            if (pull >= thresholds[i]) {
                stage = i + 1;
            }
        }
        return stage;
    }

    /**
     * The texture path one layer draws at pull stage {@code stage} -- {@link #layer} with a
     * {@code _draw<stage>} suffix where upstream re-points that layer, and the plain undrawn layer
     * where it does not.
     *
     * <p>Upstream's rule, uniform across all three bows: the <b>string</b> layer has art from stage 1
     * (it starts moving the instant you draw) and the <b>limb</b> layers only from stage 2 (they do
     * not visibly bend until the draw is well along). Every other layer -- the longbow's grip, the
     * crossbow's body and binding -- keeps its undrawn art at every stage. See
     * {@code scripts/derive_bow_draw_art.py} for the per-file table this was read off.
     */
    public static String drawLayer(String tool, String layer, int stage) {
        boolean staged = layer.startsWith("string") || (layer.startsWith("limb") && stage >= 2);
        return staged ? layer(tool, layer) + "_draw" + stage : layer(tool, layer);
    }

    /**
     * Whether {@code tool} renders a loaded state -- the crossbow alone, upstream's
     * {@code {"loaded": 1}} override ({@code CrossBow#PROPERTY_IS_LOADED}). It points at the
     * {@link #LOADED_STAGE} textures rather than at art of its own: a loaded crossbow is a crossbow
     * held at full crank.
     */
    public static boolean hasLoadedState(String tool) {
        return ToolConstants.CROSSBOW.id().equals(tool);
    }

    /**
     * Where a bow holds its nocked ammo at each <em>drawn</em> pull stage (1 to {@link #DRAW_STAGES},
     * so stage {@code n} is index {@code n - 1}): the {@code x} of {@code ammoPosition.pos}, {@code y}
     * being its negation and {@code z} {@link #BOW_AMMO_LIFT} throughout. Whole pixels
     * ({@code 1/16 = 0.0625}, upstream's own rounding), and they shrink as the draw progresses
     * because the arrow travels back with the string it rides.
     */
    private static final float[] BOW_AMMO_X = {-0.1880f, -0.1255f, -0.0630f};

    /** How far out of the bow's own plane the arrow sits, so the two do not z-fight. */
    private static final float BOW_AMMO_LIFT = 0.01f;

    /** {@code shortbow.tcon.json}/{@code longbow.tcon.json}'s root {@code rot}, shared by every stage. */
    private static final float[] BOW_AMMO_ROTATION = {0.0f, 180.0f, 0.0f};

    /** {@code crossbow.tcon.json}'s {@code {"loaded":1}} override: a bolt laid across the stock. */
    private static final float[] CROSSBOW_LOADED_AMMO = {0.0625f, -0.0625f, 0.0625f, 0.0f, 0.0f, 90.0f};

    /**
     * Where {@code tool}'s nocked ammo is drawn at pull stage {@code stage} (T52, issue #483):
     * {@code {x, y, z, rotX, rotY, rotZ}} -- offsets in block units, rotations in degrees, about the
     * item's own centre -- or {@code null} where that state draws no ammo at all. Upstream's
     * {@code ammoPosition} blocks, an override's combined with the model root's
     * ({@code AmmoPosition#combine}, which fills a missing entry from the root).
     *
     * <p>A bow carries one at each of its three <em>drawn</em> stages and none at stage 0, which is
     * "this holder is not drawing this bow" ({@code ForgeweaveItemProperties#pulling} 0). The
     * crossbow carries one only when loaded -- its three cranking overrides have no
     * {@code ammoPosition} key, so upstream bakes them as plain tool models, and its empty root
     * block never renders because {@code CrossBow#getAmmoToRender} is empty unless the flag is set.
     *
     * <p><b>Deviation from 1.12</b>, on the maintainer's call in playtest issue #600: upstream's bow
     * model roots carry an {@code ammoPosition} of their own -- the full-draw offset -- and
     * {@code BowCore#getAmmoToRender} is the found ammo whenever the bow is not Broken, so a 1.12
     * bow held by a player with arrows shows a nocked one before the draw starts, and in the
     * inventory icon. Playtest alpha.3 read that as a bug ("the nocked arrow renders at ALL times");
     * here the arrow appears only once the draw does.
     */
    @Nullable
    public static float[] ammoPosition(String tool, int stage) {
        if (hasLoadedState(tool)) {
            return stage == LOADED_STAGE ? CROSSBOW_LOADED_AMMO.clone() : null;
        }
        if (!DRAW_THRESHOLDS.containsKey(tool) || stage < 1 || stage > DRAW_STAGES) {
            return null;
        }
        float x = BOW_AMMO_X[stage - 1];
        return new float[] {x, -x, BOW_AMMO_LIFT,
                BOW_AMMO_ROTATION[0], BOW_AMMO_ROTATION[1], BOW_AMMO_ROTATION[2]};
    }

    private ToolArt() {}
}
