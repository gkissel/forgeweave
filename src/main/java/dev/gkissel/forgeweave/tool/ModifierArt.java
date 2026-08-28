package dev.gkissel.forgeweave.tool;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.modifier.Fortification;

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

    /**
     * The family sentinel {@link #overlay} normalizes every generated {@code fortification.<material>}
     * id down to (T70, issue #501) -- upstream ships one overlay file per tool for the whole
     * {@code ModFortify} family, tinted per material at render time
     * ({@code ModifierOverlayModels}'s fortification tint index), not one file per material. Distinct
     * from {@link Fortification#RECIPE_ID}, which happens to share this same {@code
     * forgeweave:fortification} value but is never a stored modifier id -- the two are read for
     * unrelated purposes and neither depends on the other's identity.
     */
    private static final ResourceLocation FORTIFICATION = id("fortification");

    /** The modifiers whose application draws an overlay layer; see the class javadoc. */
    public static final Set<ResourceLocation> OVERLAY_MODIFIERS = Set.of(
            id("haste"), id("sharpness"), id("diamond"), id("emerald"), id("reinforced"),
            id("silky"), id("luck"), id("mending_moss"), id("soulbound"), id("smite"),
            id("bane_of_arthropods"), id("fiery"), id("necrotic"), id("knockback"),
            id("beheading"), id("shulking"), id("webbed"), id("glowing"),
            id("blasting"), id("fins"), FORTIFICATION);

    /**
     * The texture path (no {@code .png}, no namespace) of {@code modifier}'s overlay on {@code
     * tool}, or {@code null} if that modifier draws nothing -- same contract as
     * {@link ToolArt#layer}.
     *
     * @param tool the tool item's registry path, e.g. {@code "broadsword"}
     */
    @Nullable
    public static String overlay(String tool, ResourceLocation modifier) {
        // Every forgeweave:fortification.<material> id shares one overlay file per tool; see
        // {@link #FORTIFICATION}.
        ResourceLocation family = Fortification.isFortification(modifier) ? FORTIFICATION : modifier;
        // #679: upstream 1.12 has no armor, so no armor piece has any modifier overlay art to derive
        // (M4's defense modifier family renders through the worn layers, not item overlays). #735's
        // heavy set is the same story -- no upstream counterpart at all.
        if (ToolConstants.ARMOR.stream().anyMatch(piece -> piece.id().equals(tool))
                || ToolConstants.HEAVY_ARMOR.stream().anyMatch(piece -> piece.id().equals(tool))) {
            return null;
        }
        if (!OVERLAY_MODIFIERS.contains(family) || NO_UPSTREAM_ART.contains(tool + "_" + family.getPath())) {
            return null;
        }
        return "derived/tools/mods/" + tool + "_" + family.getPath();
    }

    /**
     * {@code <tool>_<modifier>} pairs that draw no overlay on purpose. Luck refuses launchers (M3.5
     * #394, {@code ModLuck.java:35}), so {@code items/shortbow/} has no {@code mod_luck.png} to
     * derive -- nor does {@code items/longbow/} (#395); upstream's {@code items/crossbow/} does ship
     * one, inconsistently, and it is derived like any other.
     *
     * <p>Blasting (parity audit T24) is {@code ModifierAspect.harvestOnly}, and upstream ships
     * {@code mod_blasting.png} in exactly its nine {@code Category.HARVEST} tool folders. Every
     * Forgeweave tool outside that category is listed here: the twelve melee shapes and the three
     * bows have no art to derive, and the warmace -- whose donor is the hammer, which does have it --
     * is {@code Category.MELEE}, so the station never lets blasting onto one in the first place.
     * Mirrored by {@code scripts/derive_modifier_overlays.py}.
     *
     * <p>Fortification (parity audit T70, issue #501) is {@code ModifierAspect.harvestOnly} too, so
     * the same fourteen non-harvest tools are listed again below. Mattock is the one addition:
     * unlike blasting, upstream's {@code items/mattock/} folder ships every other harvest-only
     * modifier's overlay but genuinely has no {@code mod_fortified.png} -- an upstream art gap, not a
     * Forgeweave omission, verified against the pinned commit and mirrored rather than patched over.
     */
    private static final Set<String> NO_UPSTREAM_ART = Set.of(
            "shortbow_luck", "longbow_luck",
            // #653: the arrow, like the shuriken, ships no mod_haste.png upstream (haste refuses
            // NO_MELEE tools anyway -- ModHaste#canApplyCustom); blasting/fortification below, it is
            // Category.RANGED, not HARVEST.
            "arrow_haste", "arrow_blasting", "arrow_fortification",
            // #653: fins is ModifierAspect.projectileOnly, so upstream ships mod_fins.png in exactly
            // its three projectile folders (arrow, bolt, shuriken) and nowhere else -- every
            // non-projectile Forgeweave tool is listed here, mirroring the blasting/fortification
            // pattern.
            "pickaxe_fins", "shovel_fins", "hatchet_fins", "mattock_fins", "kama_fins",
            "hammer_fins", "excavator_fins", "lumberaxe_fins", "scythe_fins", "vein_hammer_fins",
            "broadsword_fins", "longsword_fins", "rapier_fins", "battlesign_fins", "frying_pan_fins",
            "battleaxe_fins", "cleaver_fins", "dagger_fins", "scimitar_fins", "katana_fins",
            "warmace_fins", "shortbow_fins", "longbow_fins", "crossbow_fins",
            // #448: items/shuriken/ ships every other applicable overlay but no mod_haste.png at the
            // pinned commit -- an upstream art absence, mirrored not patched. Blasting/fortification
            // below: the shuriken is Category.RANGED, not HARVEST, like the three bows.
            "shuriken_haste", "shuriken_blasting", "shuriken_fortification",
            "broadsword_blasting", "longsword_blasting", "rapier_blasting", "battlesign_blasting",
            "frying_pan_blasting", "battleaxe_blasting", "cleaver_blasting", "dagger_blasting",
            "scimitar_blasting", "katana_blasting", "warmace_blasting",
            "shortbow_blasting", "longbow_blasting", "crossbow_blasting",
            "broadsword_fortification", "longsword_fortification", "rapier_fortification",
            "battlesign_fortification", "frying_pan_fortification", "battleaxe_fortification",
            "cleaver_fortification", "dagger_fortification", "scimitar_fortification",
            "katana_fortification", "warmace_fortification",
            "shortbow_fortification", "longbow_fortification", "crossbow_fortification",
            "mattock_fortification");

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
