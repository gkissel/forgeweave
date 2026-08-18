package dev.gkissel.forgeweave.tool;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Which modifiers render as an overlay layer on an assembled tool, and where that art lives
 * (issue #257) -- the modifier-side sibling of {@link ToolArt}. One place for the same reason
 * {@code ToolArt} is: the client renderer ({@code ModifierOverlayModels}) resolves these paths per
 * stack, and {@code ModifierArtTest} walks the same set against the files on disk so a future
 * modifier cannot point at art that was never derived.
 *
 * <p>Upstream 1.12 ships one overlay texture per tool per modifier ({@code
 * items/<tool>/mod_<modifier>.png}, declared in {@code models/item/modifiers/*.json}); the derived
 * copies live at {@code textures/derived/tools/mods/<tool>_<modifier>.png}
 * (scripts/derive_modifier_overlays.py, NOTICE.md).
 *
 * <p>The set below is exactly the Forgeweave modifiers with an upstream overlay. The Forgeweave
 * originals (searing, magnetic_pull, aquadynamic, resonant, far_reach, extra_slot, wind_burst) and
 * the generated embossment ids deliberately have <b>no</b> overlay: upstream itself ships
 * overlay-less modifiers (its creative modifier renders nothing), and a freshly-authored
 * approximation is what the 1.12-parity default forbids -- recorded per modifier in issue #257's PR
 * for maintainer review.
 */
public final class ModifierArt {

    /** The modifiers whose application draws an overlay layer; see the class javadoc. */
    public static final Set<ResourceLocation> OVERLAY_MODIFIERS = Set.of(
            id("haste"), id("sharpness"), id("diamond"), id("emerald"), id("reinforced"),
            id("silky"), id("luck"), id("mending_moss"), id("soulbound"), id("smite"),
            id("bane_of_arthropods"), id("fiery"), id("necrotic"), id("knockback"),
            id("beheading"), id("shulking"), id("webbed"), id("glowing"));

    /**
     * The texture path (no {@code .png}, no namespace) of {@code modifier}'s overlay on {@code
     * tool}, or {@code null} if that modifier draws nothing -- same contract as
     * {@link ToolArt#layer}.
     *
     * @param tool the tool item's registry path, e.g. {@code "broadsword"}
     */
    @Nullable
    public static String overlay(String tool, ResourceLocation modifier) {
        if (!OVERLAY_MODIFIERS.contains(modifier) || NO_UPSTREAM_ART.contains(tool + "_" + modifier.getPath())) {
            return null;
        }
        return "derived/tools/mods/" + tool + "_" + modifier.getPath();
    }

    /**
     * {@code <tool>_<modifier>} pairs upstream ships no overlay for on purpose (M3.5 #394): luck
     * refuses launchers ({@code ModLuck.java:35}), so {@code items/shortbow/} has no
     * {@code mod_luck.png} to derive -- nor does {@code items/longbow/} (#395). Upstream's
     * {@code items/crossbow/} does ship one, inconsistently, and it is derived like any other.
     * Mirrored by {@code scripts/derive_modifier_overlays.py}.
     */
    private static final Set<String> NO_UPSTREAM_ART = Set.of("shortbow_luck", "longbow_luck");

    /**
     * As {@link #overlay(String, ResourceLocation)}, for a bow rendered at pull stage {@code stage}
     * (M3.5 issue #400); {@code stage} 0 is "not being drawn" and is the plain overlay.
     *
     * <p>Upstream's mechanism: each pull override in {@code <bow>.tcon.json} carries a
     * {@code modifier_suffix} of {@code 1}/{@code 2}/{@code 3}, and {@code ToolModelLoader} uses it
     * to look up the {@code <tool><N>} texture key in every {@code models/item/modifiers/*.json} --
     * filling the base map in first and letting the staged keys overwrite it. So a modifier with no
     * art for a stage keeps its undrawn overlay there, which is what the {@link #STAGED_OVERLAYS}
     * miss below does.
     */
    @Nullable
    public static String overlay(String tool, ResourceLocation modifier, int stage) {
        String base = overlay(tool, modifier);
        if (base == null || stage < 1) {
            return base;
        }
        String suffix = "_draw" + stage;
        return STAGED_OVERLAYS.contains(tool + "_" + modifier.getPath() + suffix) ? base + suffix : base;
    }

    /**
     * The {@code <tool>_<modifier>_draw<stage>} files upstream actually ships staged art for, read
     * off {@code items/<bow>/mod_*_<N>.png} at the pinned commit. Only the three bow folders have
     * any, and none of them has a full set -- the shortbow's sharpness bends only at stage 3, the
     * crossbow's emerald only at stage 2, and haste is the one modifier with all three stages on all
     * three bows. Mirrored by {@code scripts/derive_modifier_overlays.py}; {@code ModifierArtTest}
     * pins this set to the files on disk in both directions.
     */
    private static final Set<String> STAGED_OVERLAYS = Set.of(
            "shortbow_bane_of_arthropods_draw2", "shortbow_bane_of_arthropods_draw3",
            "shortbow_fiery_draw2", "shortbow_fiery_draw3",
            "shortbow_haste_draw1", "shortbow_haste_draw2", "shortbow_haste_draw3",
            "shortbow_sharpness_draw3", "shortbow_shulking_draw3",
            "shortbow_silky_draw2", "shortbow_silky_draw3",
            "shortbow_webbed_draw2", "shortbow_webbed_draw3",
            "shortbow_glowing_draw1", "shortbow_glowing_draw2", "shortbow_glowing_draw3",
            "longbow_bane_of_arthropods_draw2", "longbow_bane_of_arthropods_draw3",
            "longbow_fiery_draw2", "longbow_fiery_draw3",
            "longbow_haste_draw1", "longbow_haste_draw2", "longbow_haste_draw3",
            "longbow_sharpness_draw2", "longbow_sharpness_draw3",
            "longbow_shulking_draw2", "longbow_shulking_draw3",
            "longbow_silky_draw2", "longbow_silky_draw3",
            "longbow_webbed_draw2", "longbow_webbed_draw3",
            "longbow_glowing_draw1", "longbow_glowing_draw2", "longbow_glowing_draw3",
            "crossbow_emerald_draw2",
            "crossbow_fiery_draw2", "crossbow_fiery_draw3",
            "crossbow_haste_draw1", "crossbow_haste_draw2", "crossbow_haste_draw3",
            "crossbow_sharpness_draw2", "crossbow_sharpness_draw3");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private ModifierArt() {}
}
