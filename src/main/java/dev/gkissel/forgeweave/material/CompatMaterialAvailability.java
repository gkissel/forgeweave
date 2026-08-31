package dev.gkissel.forgeweave.material;

import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * "Is this compat material's provider mod actually present" -- issue #873 (M6 epic #824's JC3
 * reversal), the runtime mirror of the {@code neoforge:conditions} gate each compat material's own
 * JSON already carries. Molten fluids and their buckets are registered unconditionally in Java (the
 * NeoForge platform constraint every fluid in {@link dev.gkissel.forgeweave.fluid.ForgeweaveFluids}
 * lives under); this class is what lets {@code ForgeweaveCreativeTab} hide a bucket -- and, for the
 * three PlusTiC-inspiration alloys, an ingot/nugget/block -- from creative and JEI when the material
 * it backs would fail to register.
 *
 * <p>Table entries mirror each material's own {@code neoforge:conditions} exactly (same item ids the
 * material JSON, its melting recipes and its casting recipes key on): {@link #ANY_OF} for a plain
 * {@code neoforge:item_exists} or an {@code neoforge:or} across providers (lead, uranium -- JC2's
 * "gate by material name, not by mod"), {@link #ALL_OF} for the three alloys' AND-of-inputs gate
 * (deliverable 4). A material id absent from both tables is unconditional (every native and Track B
 * material, plus the two new gem materials) and always reports available.
 *
 * <p>Checked against the live item registry rather than re-evaluating the datapack condition tree:
 * by the time the creative tab or JEI builds its listing, every mod's items are already registered,
 * so {@code BuiltInRegistries.ITEM.containsKey} answers exactly what {@code neoforge:item_exists}
 * itself answers at registry-sync time -- without this class needing its own JSON parser.
 */
public final class CompatMaterialAvailability {

    // Mirrors each material JSON's own `neoforge:conditions`. Multi-entry lists are an
    // `neoforge:or` (any one provider is enough); every other entry is a plain `item_exists`.
    private static final Map<String, List<ResourceLocation>> ANY_OF = Map.ofEntries(
            entry("aluminium", "immersiveengineering:ingot_aluminum"),
            entry("bronze", "mekanism:ingot_bronze"),
            entry("conductive_alloy", "enderio:conductive_alloy_ingot"),
            entry("constantan", "immersiveengineering:ingot_constantan"),
            entry("dark_steel", "enderio:dark_steel_ingot"),
            entry("draconium_awakened", "draconicevolution:awakened_draconium_ingot"),
            entry("draconium", "draconicevolution:draconium_ingot"),
            entry("electrum", "immersiveengineering:ingot_electrum"),
            entry("end_steel", "enderio:end_steel_ingot"),
            entry("energetic_alloy", "enderio:energetic_alloy_ingot"),
            entry("iesnium", "occultism:iesnium_ingot"),
            entry("invar", "modern_industrialization:invar_ingot"),
            entry("iridium", "modern_industrialization:iridium_ingot"),
            entry("lead", "mekanism:ingot_lead", "immersiveengineering:ingot_lead"),
            entry("nickel", "immersiveengineering:ingot_nickel"),
            entry("osmium", "mekanism:ingot_osmium"),
            entry("platinum", "modern_industrialization:platinum_ingot"),
            entry("psimetal", "psi:psimetal"),
            entry("ebony_psimetal", "psi:ebony_psimetal"),
            entry("ivory_psimetal", "psi:ivory_psimetal"),
            entry("pulsating_alloy", "enderio:pulsating_alloy_ingot"),
            entry("redstone_alloy", "enderio:redstone_alloy_ingot"),
            entry("refined_glowstone", "mekanism:ingot_refined_glowstone"),
            entry("refined_obsidian", "mekanism:ingot_refined_obsidian"),
            entry("silver", "immersiveengineering:ingot_silver"),
            entry("soularium", "enderio:soularium_ingot"),
            entry("tin", "mekanism:ingot_tin"),
            entry("titanium", "modern_industrialization:titanium_ingot"),
            entry("tungsten", "modern_industrialization:tungsten_ingot"),
            entry("uranium", "mekanism:ingot_uranium", "immersiveengineering:ingot_uranium", "bigreactors:yellorium_ingot"),
            entry("vibrant_alloy", "enderio:vibrant_alloy_ingot"),
            entry("pink_slime", "industrialforegoing:pink_slime_ingot"),
            entry("graphite", "bigreactors:graphite_ingot"),
            entry("dark_matter", "projecte:dark_matter"),
            entry("red_matter", "projecte:red_matter"),
            entry("cosmic_neutronium", "avaritia:neutronium_ingot"),
            entry("crystal_matrix", "avaritia:crystal_matrix_ingot"),
            entry("infinity", "avaritia:infinity_ingot"),
            entry("chaotic", "draconicevolution:chaotic_core"),
            entry("wyvern", "draconicevolution:wyvern_core"),
            entry("quartz_enriched_iron", "refinedstorage:quartz_enriched_iron"),
            entry("silicon", "refinedstorage:silicon"),
            entry("energised_steel", "powah:steel_energized"),
            entry("blutonium", "bigreactors:blutonium_ingot"),
            entry("cyanite", "bigreactors:cyanite_ingot"),
            entry("ludicrite", "bigreactors:ludicrite_ingot"),
            entry("uraninite", "powah:uraninite_raw"));

    // The three PlusTiC-inspiration alloys (issue #873 deliverable 4): condition is the AND of their
    // compat inputs' own providers (native inputs -- iron, obsidian, glass -- need no entry).
    private static final Map<String, List<ResourceLocation>> ALL_OF = Map.of(
            "alumite", ids("immersiveengineering:ingot_aluminum"),
            "osgloglas", ids("mekanism:ingot_osmium", "mekanism:ingot_refined_obsidian"),
            "osmiridium", ids("mekanism:ingot_osmium", "modern_industrialization:iridium_ingot"));

    private static Map.Entry<String, List<ResourceLocation>> entry(String materialId, String... items) {
        return Map.entry(materialId, ids(items));
    }

    private static List<ResourceLocation> ids(String... items) {
        return List.of(items).stream().map(ResourceLocation::parse).toList();
    }

    /**
     * Whether {@code materialId}'s provider is present -- {@code true} for every id not in either
     * table (unconditional materials never hide).
     */
    public static boolean isAvailable(String materialId) {
        List<ResourceLocation> anyOf = ANY_OF.get(materialId);
        if (anyOf != null) {
            return anyOf.stream().anyMatch(CompatMaterialAvailability::itemRegistered);
        }
        List<ResourceLocation> allOf = ALL_OF.get(materialId);
        if (allOf != null) {
            return allOf.stream().allMatch(CompatMaterialAvailability::itemRegistered);
        }
        return true;
    }

    private static boolean itemRegistered(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id);
    }

    private CompatMaterialAvailability() {}
}
