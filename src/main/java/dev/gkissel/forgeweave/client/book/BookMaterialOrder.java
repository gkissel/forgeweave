package dev.gkissel.forgeweave.client.book;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialStage;

/**
 * What order the guide book's materials chapter lists materials in, and which stage page each one
 * lands on (issue #1104). Before this the chapter was one alphabetical run of up to 218 pages, so
 * {@code allthemodium} and {@code alpha_yeti_fur} sat next to each other for no reason a player
 * could use (review 05-book.md &sect;1).
 *
 * <p>Upstream never ships a flat run either: 1.12 walks its hand-written registration order, whose
 * inline comments are a category ladder with the cross-mod metals clustered at the end under "mod
 * integration", and 1.20 splits the chapter into tier-scoped sections through
 * {@code TierRangeMaterialSectionTransformer} and sorts inside each one. This is the same shape,
 * with the ladder derived rather than hand-written because the roster is too big to curate by hand.
 *
 * <p>The sort is three keys, in this order:
 *
 * <ol>
 *   <li>{@link MaterialStage}, the progression ladder.
 *   <li>the group inside the stage: Forgeweave's own and vanilla materials first, then one group
 *       per other mod that supplies the material, then the materials that only make bowstrings,
 *       shafts and fletchings -- they have no head, handle or armor stats to compare, so they read
 *       as noise anywhere else.
 *   <li>the material's own id path, which is the old order, now only a tiebreaker.
 * </ol>
 */
public final class BookMaterialOrder {

    /** A stage and the materials that landed in it, already in listing order. */
    public record Stage(MaterialStage stage, List<Map.Entry<ResourceLocation, Material>> materials) {

        public Stage {
            materials = List.copyOf(materials);
        }
    }

    private BookMaterialOrder() {
    }

    /**
     * Every stage that has at least one material, in ladder order, each holding its own materials
     * in listing order. A stage nobody has a material for gets no page rather than an empty one.
     */
    public static List<Stage> byStage(List<Map.Entry<ResourceLocation, Material>> materials,
            MaterialStage.Lookup lookup) {
        Map<MaterialStage, List<Map.Entry<ResourceLocation, Material>>> buckets =
                new EnumMap<>(MaterialStage.class);
        for (Map.Entry<ResourceLocation, Material> entry : materials) {
            buckets.computeIfAbsent(lookup.of(entry.getKey(), entry.getValue()), stage -> new ArrayList<>())
                    .add(entry);
        }
        List<Stage> stages = new ArrayList<>();
        for (MaterialStage stage : MaterialStage.values()) {
            List<Map.Entry<ResourceLocation, Material>> bucket = buckets.get(stage);
            if (bucket == null) {
                continue;
            }
            bucket.sort(insideStage());
            stages.add(new Stage(stage, bucket));
        }
        return List.copyOf(stages);
    }

    private static Comparator<Map.Entry<ResourceLocation, Material>> insideStage() {
        return Comparator
                .comparingInt((Map.Entry<ResourceLocation, Material> entry) ->
                        rangedOnly(entry.getValue()) ? 1 : 0)
                .thenComparing(entry -> sourceMod(entry.getValue()))
                .thenComparing(entry -> entry.getKey().getPath());
    }

    /**
     * The mod a material's own ingots come from, or {@code ""} for Forgeweave's own and vanilla
     * ones -- which sorts first, ahead of every named mod.
     *
     * <p>Read off the repair and Part Builder ingredients rather than off the material: the
     * {@code neoforge:conditions} block that actually gates a cross-mod material is evaluated while
     * the registry loads and is gone by the time anything can read it, so the item the Part Builder
     * accepts is the only runtime trace of where the material came from. An ingredient whose tag no
     * loaded mod fills resolves to nothing and the material simply reads as Forgeweave's own, which
     * is the same answer a material for an absent mod would get -- and a material for an absent mod
     * never reaches the book at all, because its conditions kept it out of the registry.
     */
    public static String sourceMod(Material material) {
        String fromRepair = namespaceOf(material.repairItem());
        if (!fromRepair.isEmpty()) {
            return fromRepair;
        }
        for (Material.CraftingItem item : material.craftingItems()) {
            String namespace = namespaceOf(item.ingredient());
            if (!namespace.isEmpty()) {
                return namespace;
            }
        }
        return "";
    }

    private static String namespaceOf(Ingredient ingredient) {
        for (ItemStack stack : ingredient.getItems()) {
            String namespace = stack.getItemHolder().unwrapKey()
                    .map(key -> key.location().getNamespace()).orElse("minecraft");
            if (!namespace.equals("minecraft") && !namespace.equals(Forgeweave.MODID)) {
                return namespace;
            }
        }
        return "";
    }

    /**
     * True for a material that makes nothing but bowstrings, shafts and fletchings -- eleven of
     * them today (string, feather, blaze rod, the slime leaves and vines), each of which still got
     * a full page in the middle of the alphabetical run.
     */
    public static boolean rangedOnly(Material material) {
        return !material.hasStatsFor(PartItem.Kind.HEAD)
                && !material.hasStatsFor(PartItem.Kind.HANDLE)
                && !material.hasStatsFor(PartItem.Kind.EXTRA)
                && !material.hasStatsFor(PartItem.Kind.PLATING)
                && !material.hasStatsFor(PartItem.Kind.MAILLE);
    }
}
