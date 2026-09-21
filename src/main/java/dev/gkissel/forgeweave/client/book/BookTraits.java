package dev.gkissel.forgeweave.client.book;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.trait.TraitFamilies;

/**
 * The guide book's traits reference, read out of the material registry at book-open time (issue
 * #1104). Before this a trait name on a material page was inert text with a hover, so two materials
 * sharing a trait had no way to find each other and a graded trait's levels never appeared together
 * (review 05-book.md &sect;1).
 *
 * <p>Nothing here holds a list of trait ids. The reference is whatever the loaded materials
 * actually name, so it follows a datapack that adds traits, a partner mod that registers them
 * ({@code TraitRegistry}), and issue #1103's leveled families -- that merge shows up here as fewer
 * entries with more materials under each, and the families themselves come from
 * {@link TraitFamilies}, so a rung is grouped by what it declares rather than by how it is spelled.
 */
public final class BookTraits {

    /** One level of a family: the id a material names, and every material that names it. */
    public record Rung(ResourceLocation id, int level, List<ResourceLocation> materials) {

        public Rung {
            materials = List.copyOf(materials);
        }
    }

    /**
     * One family: the path its lang keys hang off and its levels in order. A trait with no levels
     * above one is a family of exactly one rung, which is most of them.
     */
    public record Family(String path, List<Rung> rungs) {

        public Family {
            rungs = List.copyOf(rungs);
        }

        /** The namespace-qualified id of the family's first level, which titles its page. */
        public ResourceLocation id() {
            return this.rungs.get(0).id();
        }

        /**
         * The family's display name. A declared family has one name key for all its rungs, which is
         * what {@link TraitFamilies#langBase} returns; an undeclared id keys off itself, so a family
         * whose lowest shipped level is level two still names a string that exists.
         */
        public String nameKey() {
            return TraitFamilies.langBase(id()) + ".name";
        }

        public int maxLevel() {
            return this.rungs.get(this.rungs.size() - 1).level();
        }
    }

    private BookTraits() {
    }

    /**
     * Every trait family the given materials grant, by family path, each family's levels in order
     * and each level's materials by id path. Deterministic: the map is sorted, so the reference
     * pages come out in the same order on every client.
     */
    public static List<Family> families(List<Map.Entry<ResourceLocation, Material>> materials) {
        // family path -> level -> materials that grant that level
        Map<String, Map<Integer, Rung>> byFamily = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Material> entry : materials) {
            for (ResourceLocation trait : entry.getValue().traits().all()) {
                int level = levelOf(trait);
                Map<Integer, Rung> levels = byFamily.computeIfAbsent(
                        trait.getNamespace() + ":" + familyOf(trait), path -> new TreeMap<>());
                Rung rung = levels.get(level);
                List<ResourceLocation> granted = rung == null ? new ArrayList<>()
                        : new ArrayList<>(rung.materials());
                granted.add(entry.getKey());
                granted.sort(Comparator.comparing(ResourceLocation::getPath));
                levels.put(level, new Rung(rung == null ? trait : rung.id(), level, granted));
            }
        }
        Map<String, Family> families = new LinkedHashMap<>();
        byFamily.forEach((path, levels) ->
                families.put(path, new Family(path.split(":", 2)[1], List.copyOf(levels.values()))));
        return List.copyOf(families.values());
    }

    /**
     * The family a trait id belongs to: the one it was declared in ({@link TraitFamilies#of}) where
     * there is one, otherwise its path with a trailing level number taken off.
     *
     * <p>Issue #1103 made the declaration the authority, which is what closes the gap the
     * string-derived version left: a family whose members do not share a stem now groups correctly,
     * and a rung that was renamed in place is found by its declared family rather than by its
     * spelling. The trailing-digit reading stays as the fallback because two shipped ladders never
     * declared themselves and do not need to -- {@code evolving}/{@code evolved}/{@code evolved2}/
     * {@code evolved3} is a gate read by id ({@code ForgeweaveDraconicCompat#evolvedLevel}) and
     * {@code alien}/{@code alien2} is a stateful pair -- and because the reference is built from
     * whatever the loaded materials name, which can include a datapack's own undeclared ladder.
     */
    public static String familyOf(ResourceLocation trait) {
        TraitFamilies.Rung declared = TraitFamilies.of(trait);
        return declared != null ? declared.family() : trailingDigitFamily(trait);
    }

    /** The level a trait id carries: its declared level, or its trailing number, or 1. */
    public static int levelOf(ResourceLocation trait) {
        TraitFamilies.Rung declared = TraitFamilies.of(trait);
        if (declared != null) {
            return declared.level();
        }
        String digits = trait.getPath().substring(trailingDigitFamily(trait).length());
        return digits.isEmpty() ? 1 : Integer.parseInt(digits);
    }

    private static String trailingDigitFamily(ResourceLocation trait) {
        String path = trait.getPath();
        int end = path.length();
        // At most two digits, so a level always parses and an id that simply ends in a year keeps
        // most of its name.
        while (end > 1 && end > path.length() - 2 && Character.isDigit(path.charAt(end - 1))) {
            end--;
        }
        return path.substring(0, end);
    }
}
