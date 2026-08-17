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
            id("beheading"), id("shulking"), id("webbed"));

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
     * {@code mod_luck.png} to derive. Mirrored by {@code scripts/derive_modifier_overlays.py}.
     */
    private static final Set<String> NO_UPSTREAM_ART = Set.of("shortbow_luck");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private ModifierArt() {}
}
