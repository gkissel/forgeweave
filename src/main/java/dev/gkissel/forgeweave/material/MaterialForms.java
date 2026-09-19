package dev.gkissel.forgeweave.material;

import java.util.ArrayList;
import java.util.List;

import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * Which Forgeweave materials get which item forms (issue #992, M8-8; docs/SCOPE.md D-M8-6 and
 * D-M8-7). This is the one table behind every per-form registration, model, lang line, {@code c:}
 * tag, crafting recipe and sprite -- add a material here and it inherits the whole set with no other
 * code change, which is what the roster-walking test in {@code MaterialFormsTest} pins.
 *
 * <p>D-M8-6's rule is "every material with an ingot gets the full form set". Rather than spell the
 * roster out, {@link #ALL} walks {@link TrackBOre#ALL} and {@link TrackBAlloy#ALL} -- 37 materials
 * with an ingot between them, 11 ores and 26 alloys (the 26 include the four Draconic fusion welds
 * and #993's atomic matter alloy, which live on the alloy roster because "a Forgeweave metal with an
 * ingot, a nugget, a storage block and a molten fluid, and no ore" is exactly their shape; see
 * {@code TrackBAlloy}'s own javadoc). The planning session that wrote D-M8-6 had counted 30; the
 * roster is the source of truth and all of them get the forms (maintainer confirm, 2026-09-18).
 * {@link #OWN_ITEM_METALS} is the other half of D-M8-6:
 * the ten Forgeweave metals that own an ingot item outside the Track B rosters.
 *
 * <p>Gem-type materials get {@link MaterialForm#DUSTS} and nothing else: brimspar, which has no ingot
 * at all (it is a fuel ore, #903), and fulmenite, whose ore drops a crystal ({@link
 * TrackBOre#dropsCrystal}, #929). Fulmenite keeps its shipped ingot, nugget and storage block exactly
 * as they are -- those ids do not move and nothing here duplicates them -- and gains only the three
 * dusts.
 *
 * <p>Track A materials (other mods' metals, {@code ForgeweaveFluids#compatMetalFluid}) are
 * deliberately absent: D-M8-6 leaves their item forms to the mods that own them.
 */
public final class MaterialForms {

    /**
     * The ten Forgeweave metals with an ingot item of their own, outside the Track B rosters
     * (D-M8-7). Display names match the shipped {@code item.forgeweave.<id>_ingot} lang lines.
     */
    public static final List<FormedMaterial> OWN_ITEM_METALS = List.of(
            new FormedMaterial("cobalt", "Cobalt", MaterialForm.ALL),
            new FormedMaterial("ardite", "Ardite", MaterialForm.ALL),
            new FormedMaterial("manyullyn", "Manyullyn", MaterialForm.ALL),
            new FormedMaterial("rose_gold", "Rose Gold", MaterialForm.ALL),
            new FormedMaterial("steel", "Steel", MaterialForm.ALL),
            new FormedMaterial("knightslime", "Knightslime", MaterialForm.ALL),
            new FormedMaterial("pig_iron", "Pig Iron", MaterialForm.ALL),
            new FormedMaterial("amethyst_bronze", "Amethyst Bronze", MaterialForm.ALL),
            new FormedMaterial("queens_slime", "Queen's Slime", MaterialForm.ALL),
            new FormedMaterial("hepatizon", "Hepatizon", MaterialForm.ALL));

    /**
     * Brimspar, the one gem-type material outside the Track B ore roster (#903): a Nether fuel ore
     * with no ingot, so dusts only. Fulmenite is the other gem-type case and comes off
     * {@link TrackBOre#dropsCrystal} inside the ore loop below.
     */
    private static final FormedMaterial BRIMSPAR =
            new FormedMaterial("brimspar", "Brimspar", MaterialForm.DUSTS);

    /** Every material that gets forms, in a stable order: Track B ores, Track B alloys, own-item metals, brimspar. */
    public static final List<FormedMaterial> ALL = build();

    /** One material's form set. {@code displayName} feeds the lang lines, {@code id} everything else. */
    public record FormedMaterial(String id, String displayName, List<MaterialForm> forms) {

        /** True when this material gets the output-only plate family, i.e. it has an ingot. */
        public boolean hasIngot() {
            return forms.contains(MaterialForm.PLATE);
        }
    }

    private static List<FormedMaterial> build() {
        List<FormedMaterial> materials = new ArrayList<>();
        for (TrackBOre ore : TrackBOre.ALL) {
            // #929 -- a crystal-dropping ore is gem-type for D-M8-6's purposes: dusts only.
            materials.add(new FormedMaterial(ore.id(), ore.displayName(),
                    ore.dropsCrystal() ? MaterialForm.DUSTS : MaterialForm.ALL));
        }
        for (TrackBAlloy alloy : TrackBAlloy.ALL) {
            materials.add(new FormedMaterial(alloy.id(), alloy.displayName(), MaterialForm.ALL));
        }
        materials.addAll(OWN_ITEM_METALS);
        materials.add(BRIMSPAR);
        return List.copyOf(materials);
    }

    private MaterialForms() {}
}
