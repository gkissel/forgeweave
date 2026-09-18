package dev.gkissel.forgeweave.material;

import java.util.List;

/**
 * The eight item forms issue #992 (M8-8, docs/SCOPE.md D-M8-6) adds on top of the ingot, nugget and
 * storage block every Forgeweave metal already had. One row per form, and every consumer walks this
 * enum instead of naming forms by hand: registration ({@code ForgeweaveItems}), models
 * ({@code ForgeweaveItemModelProvider}), lang ({@code ForgeweaveLanguageProvider}), {@code c:} tags
 * ({@code ForgeweaveItemTagsProvider}), crafting recipes ({@code ForgeweaveRecipeProvider}), the
 * creative tab, the sprite script ({@code scripts/generate_material_forms.py}) and the dust melting
 * rows that script writes.
 *
 * <p>{@link #tagFamily()} is the {@code c:} parent every form joins, spelled the way NeoForge's own
 * convention tags are ({@code c:dusts/<material>} under {@code c:dusts}). {@link #meltAmount()} is
 * the smeltery value in mB, and zero means the form has no melting row at all: D-M8-6 makes the
 * plate family output-only, so Forgeweave ships nothing that consumes a plate, rod, gear or wire,
 * melting included. The three dust amounts follow the existing per-form ladder -- a dust melts as
 * its ingot, a small dust as a third of one, a tiny dust as a nugget.
 */
public enum MaterialForm {
    DUST("dust", "dusts", "%s Dust", 144),
    SMALL_DUST("small_dust", "small_dusts", "Small %s Dust", 48),
    TINY_DUST("tiny_dust", "tiny_dusts", "Tiny %s Dust", 16),
    PLATE("plate", "plates", "%s Plate", 0),
    DOUBLE_PLATE("double_plate", "double_plates", "%s Double Plate", 0),
    ROD("rod", "rods", "%s Rod", 0),
    GEAR("gear", "gears", "%s Gear", 0),
    WIRE("wire", "wires", "%s Wire", 0);

    /** The three dusts, the only forms a gem-type material gets (D-M8-6: brimspar and fulmenite). */
    public static final List<MaterialForm> DUSTS = List.of(DUST, SMALL_DUST, TINY_DUST);

    /** The five output-only forms (D-M8-6): made for other mods' presses, never spent by Forgeweave. */
    public static final List<MaterialForm> PLATE_FAMILY = List.of(PLATE, DOUBLE_PLATE, ROD, GEAR, WIRE);

    /** Every form, dusts first -- what a material with an ingot gets in full. */
    public static final List<MaterialForm> ALL = List.of(DUST, SMALL_DUST, TINY_DUST, PLATE, DOUBLE_PLATE,
            ROD, GEAR, WIRE);

    private final String suffix;
    private final String tagFamily;
    private final String displayPattern;
    private final int meltAmount;

    MaterialForm(String suffix, String tagFamily, String displayPattern, int meltAmount) {
        this.suffix = suffix;
        this.tagFamily = tagFamily;
        this.displayPattern = displayPattern;
        this.meltAmount = meltAmount;
    }

    /** The registry path this form takes for a material, e.g. {@code "steel_double_plate"}. */
    public String itemId(String materialId) {
        return materialId + "_" + suffix;
    }

    /** The {@code c:} leaf tag path for a material, e.g. {@code "double_plates/steel"}. */
    public String tagPath(String materialId) {
        return tagFamily + "/" + materialId;
    }

    /** The {@code c:} parent tag path this form's leaf tags hang under, e.g. {@code "double_plates"}. */
    public String tagFamily() {
        return tagFamily;
    }

    /** The English name for a material's display name, e.g. {@code "Small Steel Dust"}. */
    public String displayName(String materialDisplayName) {
        return String.format(displayPattern, materialDisplayName);
    }

    /** Smeltery value in mB, or zero when this form has no melting row (see the class javadoc). */
    public int meltAmount() {
        return meltAmount;
    }
}
