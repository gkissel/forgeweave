package dev.gkissel.forgeweave.trait;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Which trait ids are rungs of one leveled family, and which retired ids a stored one now means
 * (issue #1103).
 *
 * <p>Two tables, one purpose. A <em>family</em> is several registered trait ids that are the same
 * promise at different magnitudes: {@code keen_edge}, {@code keen_edge2}, {@code keen_edge3} are
 * one name and one sentence, told apart by a roman numeral. An <em>alias</em> is an id Forgeweave
 * used to ship and no longer registers: a tool built before this version still carries the old
 * string in its {@code forgeweave:traits} component, so every read of a stored id runs through
 * {@link #canonical} first and lands on the rung that replaced it.
 *
 * <p>Why the tables and not the ids carry this: a rung stays its own registered {@link
 * dev.gkissel.forgeweave.api.trait.Trait}, so nothing about the hooks, the resolve order or the
 * stored component changes. That is upstream 1.12's shape too -- {@code AbstractTraitLeveled}
 * registers one instance per level under {@code identifier + level} and overrides only the display,
 * appending the numeral past level 1 and resolving the description against the family's own key.
 * Forgeweave adds one thing to it: a rung also carries the numbers its sentence quotes, so the
 * description can say "2.5" on one rung and "4.0" on the next from a single lang entry.
 *
 * <p>A datapack declares its own families in the {@code trait_definition} file, next to the
 * behaviour parameters: {@code "family": "keen_edge", "level": 2, "max_level": 3,
 * "description_args": ["3.0"]}. Leave all four out and the trait is a standalone name, which is
 * what every unmerged trait is.
 */
public final class TraitFamilies {

    /**
     * One rung of one family.
     *
     * @param family the family's lang path, so the keys are {@code trait.<namespace>.<family>.name}
     *     and {@code .description}; blank for a trait that is not part of a family
     * @param level this rung's place in the ladder, 1-based; the roman numeral past 1
     * @param maxLevel how many rungs the family has, so a renderer can say "II of III"
     * @param descriptionArgs the numbers this rung's sentence quotes, in the order the lang string
     *     interpolates them
     */
    public record Rung(String family, int level, int maxLevel, List<String> descriptionArgs) {

        /** Not a family: a trait with its own name key, which is most of them. */
        public static final Rung NONE = new Rung("", 1, 1, List.of());

        public Rung {
            descriptionArgs = List.copyOf(descriptionArgs);
            if (maxLevel < level) {
                maxLevel = level;
            }
        }

        /** Flat fields on a {@code trait_definition}, all four optional; absent means {@link #NONE}. */
        public static final MapCodec<Rung> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("family", "").forGetter(Rung::family),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("level", 1).forGetter(Rung::level),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_level", 1).forGetter(Rung::maxLevel),
                Codec.STRING.listOf().optionalFieldOf("description_args", List.of())
                        .forGetter(Rung::descriptionArgs))
                .apply(instance, Rung::new));

        boolean present() {
            return !family.isEmpty();
        }
    }

    private static Rung rung(String family, int level, int maxLevel, String... args) {
        return new Rung(family, level, maxLevel, List.of(args));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /**
     * The families whose rungs are Java-registered traits. Datapack rungs declare themselves in
     * their own file instead ({@link Rung#CODEC}) and arrive through {@link #datapack}.
     */
    private static final Map<ResourceLocation, Rung> JAVA = Map.ofEntries(
            // Ported 1.12 ladders, which upstream already renders as one name plus a numeral. The
            // numbers are issue #1102's: it had written one description per rung to tell the tiers
            // apart, and a rung's arguments say the same thing from one key.
            Map.entry(id("crude"), rung("crude", 1, 2, "5")),
            Map.entry(id("crude2"), rung("crude", 2, 2, "10")),
            Map.entry(id("magnetic"), rung("magnetic", 1, 2)),
            Map.entry(id("magnetic2"), rung("magnetic", 2, 2)),
            Map.entry(id("writable"), rung("writable", 1, 2, "1")),
            Map.entry(id("writable2"), rung("writable", 2, 2, "2")),
            // Forgeweave ladders that were already several ids of one mechanic.
            Map.entry(id("surging"), rung("surging", 1, 3, "1.5")),
            Map.entry(id("surging2"), rung("surging", 2, 3, "3")),
            Map.entry(id("surging3"), rung("surging", 3, 3, "4.5")),
            Map.entry(id("unraveling"), rung("unraveling", 1, 3, "25")),
            Map.entry(id("unraveling2"), rung("unraveling", 2, 3, "50")),
            Map.entry(id("unraveling3"), rung("unraveling", 3, 3, "75")),
            // Energized I is a Java trait and its three deeper rungs are trait_definition files, so
            // this family is declared from both sides; TraitFamilies.of reads both.
            Map.entry(id("energized"), rung("energized", 1, 4, "12,000")),
            // #1103 merges whose surviving rungs are Java traits.
            Map.entry(id("armor_breaker"), rung("armor_breaker", 1, 2, "2.5")),
            Map.entry(id("armor_breaker2"), rung("armor_breaker", 2, 2, "4.0")),
            Map.entry(id("kinetic"), rung("kinetic", 1, 2, "6")),
            Map.entry(id("kinetic2"), rung("kinetic", 2, 2, "9")),
            Map.entry(id("ecological"), rung("ecological", 1, 3, "10")),
            Map.entry(id("ecological2"), rung("ecological", 2, 3, "6.5")),
            Map.entry(id("ecological3"), rung("ecological", 3, 3, "5")),
            Map.entry(id("bracingplate"), rung("bracingplate", 1, 3, "4")),
            Map.entry(id("bracingplate2"), rung("bracingplate", 2, 3, "6")),
            Map.entry(id("bracingplate3"), rung("bracingplate", 3, 3, "10")));

    /** Retired ids a stored trait list may still carry, each pointing at the rung that replaced it. */
    private static final Map<ResourceLocation, ResourceLocation> ALIASES = Map.ofEntries(
            // Fire immunity: six names, one sentence (the report's worst group).
            Map.entry(id("blaze_gold_cinder"), id("fireward")),
            Map.entry(id("blazegold_ember"), id("fireward")),
            Map.entry(id("crimson_steel_temper"), id("fireward")),
            Map.entry(id("dragonsteel_fire_ward"), id("fireward")),
            Map.entry(id("fiery_ember"), id("fireward")),
            Map.entry(id("ignitium_blaze"), id("fireward")),
            Map.entry(id("stormrind"), id("stormward")),
            // Knockback resistance, twelve ids over eight magnitudes.
            Map.entry(id("ironwood_grip"), id("heft")),
            Map.entry(id("rubberize"), id("heft")),
            Map.entry(id("compressed_iron_heft"), id("heft")),
            Map.entry(id("ferricore_grip"), id("heft")),
            Map.entry(id("prismward"), id("heft")),
            Map.entry(id("verdant_ward"), id("heft2")),
            Map.entry(id("crystalline_ward"), id("heft2")),
            Map.entry(id("deadweight"), id("heft2")),
            Map.entry(id("ballast"), id("heft2")),
            Map.entry(id("gravitic"), id("heft3")),
            Map.entry(id("empowered_emeradic_bulwark"), id("heft3")),
            // Energy buffers.
            Map.entry(id("blazing_charge"), id("energized")),
            Map.entry(id("fluorite_focus"), id("energized")),
            Map.entry(id("kinetic_reserve"), id("energized")),
            Map.entry(id("niotic_charge"), id("energized2")),
            Map.entry(id("celestigem_charge"), id("energized2")),
            Map.entry(id("spirited_charge"), id("energized2")),
            Map.entry(id("nitro_charge"), id("energized3")),
            Map.entry(id("eclipsealloy_charge"), id("energized3")),
            Map.entry(id("infused"), id("energized4")),
            // Flat bonus damage on every hit.
            Map.entry(id("inferium_edge"), id("keen_edge")),
            Map.entry(id("prudentium_edge"), id("keen_edge")),
            Map.entry(id("quartz_enriched_edge"), id("keen_edge")),
            Map.entry(id("tertium_edge"), id("keen_edge")),
            Map.entry(id("empowered_void_maw"), id("keen_edge2")),
            Map.entry(id("imperium_edge"), id("keen_edge2")),
            Map.entry(id("supremium_edge"), id("keen_edge2")),
            Map.entry(id("awakened_supremium_edge"), id("keen_edge3")),
            Map.entry(id("insanium_edge"), id("keen_edge4")),
            // Always-on self repair.
            Map.entry(id("troll_regeneration_forest"), id("ecological2")),
            Map.entry(id("troll_regeneration_frost"), id("ecological2")),
            Map.entry(id("troll_regeneration_mountain"), id("ecological2")),
            Map.entry(id("slimevine_snap"), id("ecological")),
            Map.entry(id("tinseeker"), id("ecological")),
            Map.entry(id("vine_weave"), id("ecological")),
            Map.entry(id("smokehouse"), id("ecological")),
            Map.entry(id("coremend"), id("ecological3")),
            // Conditional self repair.
            Map.entry(id("smolderveil"), id("duskmend")),
            Map.entry(id("spiritmend"), id("duskmend")),
            Map.entry(id("duskbloom"), id("duskmend")),
            Map.entry(id("ashenbond"), id("sunmend")),
            Map.entry(id("matrixbloom"), id("sunmend")),
            // Magic protection, the eight Mystical Agriculture wards.
            Map.entry(id("inferium_ward"), id("magic_protection")),
            Map.entry(id("prudentium_ward"), id("magic_protection")),
            Map.entry(id("tertium_ward"), id("magic_protection")),
            Map.entry(id("imperium_ward"), id("magic_protection2")),
            Map.entry(id("supremium_ward"), id("magic_protection2")),
            Map.entry(id("awakened_supremium_ward"), id("magic_protection3")),
            Map.entry(id("insanium_ward"), id("magic_protection3")),
            // Lifesteal.
            Map.entry(id("soulwick"), id("soulrend")),
            Map.entry(id("mendreach"), id("soulrend")),
            Map.entry(id("soulium_reap"), id("soulrend")),
            Map.entry(id("leeching"), id("soulrend2")),
            // Stacking resistance.
            Map.entry(id("arctic_insulation"), id("bracingplate")),
            Map.entry(id("naga_ward"), id("bracingplate")),
            Map.entry(id("dragonsteel_ice_calm"), id("bracingplate")),
            Map.entry(id("deorum_temper"), id("bracingplate3")),
            // Extra modifier slots.
            Map.entry(id("silicon_lattice"), id("writable")),
            Map.entry(id("reinforced_core"), id("writable2")),
            // Impact-velocity damage.
            Map.entry(id("azure_electrum_rush"), id("kinetic")),
            Map.entry(id("stormglass"), id("kinetic")),
            Map.entry(id("gravitite_dive"), id("kinetic")),
            Map.entry(id("empowered_palis_tempest"), id("kinetic2")),
            // Dodge.
            Map.entry(id("unravelward"), id("voidward")),
            Map.entry(id("windstep"), id("voidward")),
            Map.entry(id("carminite_flicker"), id("voidward2")),
            // Crits.
            Map.entry(id("azure_silver_plunge"), id("ruthless")),
            Map.entry(id("steeleaf_precision"), id("ruthless")),
            Map.entry(id("dragonbone_edge"), id("ruthless")),
            // Poison, weakness, glowing, wither.
            Map.entry(id("deathworm_venom_red"), id("poisonous")),
            Map.entry(id("deathworm_venom_white"), id("poisonous")),
            Map.entry(id("deathworm_venom_yellow"), id("poisonous")),
            Map.entry(id("iesnium_rite"), id("enfeebling")),
            Map.entry(id("empowered_enori_radiance"), id("revealing")),
            Map.entry(id("hexward"), id("blightward")),
            Map.entry(id("uraninite_sickness"), id("blightward")),
            Map.entry(id("blutonium_fallout"), id("witherward")),
            Map.entry(id("ludicrite_meltdown"), id("witherward")),
            // Charged strikes.
            Map.entry(id("batteredge"), id("surging2")),
            Map.entry(id("radiant_edge"), id("surging2")),
            Map.entry(id("dragonsteel_lightning_surge"), id("surging2")),
            Map.entry(id("empowered_diamatine_prism"), id("surging3")),
            // The rest of the exact-duplicate groups and small ladders.
            Map.entry(id("mendbond"), id("mendward")),
            Map.entry(id("nightveil"), id("duskward")),
            Map.entry(id("sapmend"), id("bloodward")),
            Map.entry(id("ambrosium_glow"), id("bloodward")),
            Map.entry(id("shattermail"), id("armor_breaker")),
            Map.entry(id("brittleforce"), id("armor_breaker")),
            Map.entry(id("knightmetal_breach"), id("armor_breaker2")),
            Map.entry(id("warbond"), id("dominant")),
            Map.entry(id("obsidian_heart"), id("dominant")),
            Map.entry(id("cursium_blight"), id("grievous")),
            Map.entry(id("uraninite_decay"), id("grievous")),
            Map.entry(id("tyrian_steel_ward"), id("lastbreath")),
            Map.entry(id("eclipsealloy_ward"), id("lastbreath")),
            Map.entry(id("bloodgem"), id("colossal")),
            Map.entry(id("empowered_restonia_bloodsurge"), id("colossal")),
            Map.entry(id("keenedge"), id("pristine")),
            Map.entry(id("unyielding"), id("pristine")),
            Map.entry(id("coilcharge"), id("seismic")),
            Map.entry(id("avalanche"), id("seismic")),
            Map.entry(id("landslide"), id("steadfast")),
            Map.entry(id("corebound"), id("steadfast")),
            Map.entry(id("swiftstride"), id("swiftward")),
            Map.entry(id("azure_electrum_swift"), id("swiftward")),
            Map.entry(id("ironwood_footing"), id("sure_footing")),
            Map.entry(id("ferricore_footing"), id("sure_footing")),
            Map.entry(id("zanite_growth"), id("stonebound")),
            Map.entry(id("fluix_arc"), id("arcing")),
            Map.entry(id("ludicrite_surge"), id("arcing")),
            Map.entry(id("brasswind"), id("skyborne")),
            Map.entry(id("buoyant"), id("steelfast")),
            Map.entry(id("voidtouched"), id("fractured")),
            Map.entry(id("elektronbond"), id("fractured")),
            Map.entry(id("voidwoven"), id("fractured")),
            Map.entry(id("alpha_yeti_resilience"), id("surgeward")),
            Map.entry(id("aegispulse"), id("surgeward")));

    /** The loaded {@code trait_definition} families, re-snapshotted with the registry. */
    private static volatile Map<ResourceLocation, Rung> DATAPACK = Map.of();

    /** Called from {@link ForgeweaveTraits#onTagsUpdated} with the rungs the loaded definitions declared. */
    static void datapack(Map<ResourceLocation, Rung> rungs) {
        DATAPACK = Map.copyOf(rungs);
    }

    /** Every id this version retired, for the guards and the save-compat GameTest. */
    public static Map<ResourceLocation, ResourceLocation> aliases() {
        return ALIASES;
    }

    /**
     * What {@code id} means now: itself for a live id, the rung that replaced it for one this
     * version retired. Aliases do not chain -- a retired id always points straight at a live rung,
     * which the guard test checks.
     */
    public static ResourceLocation canonical(ResourceLocation id) {
        ResourceLocation replacement = ALIASES.get(id);
        return replacement == null ? id : replacement;
    }

    /**
     * {@link #canonical} over a stored trait list, dropping the duplicates a merge creates: a tool
     * whose head and handle materials used to grant two names for one mechanic ({@code voidward} and
     * {@code unravelward}, say) now names the same rung twice, and every hook would run twice.
     */
    public static List<ResourceLocation> canonical(List<ResourceLocation> ids) {
        LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>(ids.size());
        for (ResourceLocation id : ids) {
            unique.add(canonical(id));
        }
        return List.copyOf(unique);
    }

    /** The family rung {@code id} is, or {@code null} for a standalone trait. */
    @Nullable
    public static Rung of(ResourceLocation id) {
        Rung rung = JAVA.get(id);
        if (rung == null) {
            rung = DATAPACK.get(id);
        }
        return rung != null && rung.present() ? rung : null;
    }

    /**
     * The lang key prefix {@code id}'s name and description live under: the family's where it is a
     * rung, its own otherwise.
     */
    public static String langBase(ResourceLocation id) {
        Rung rung = of(id);
        String path = rung == null ? id.getPath() : rung.family();
        return "trait." + id.getNamespace() + "." + path;
    }

    /**
     * {@code Keen Edge II} -- the family name plus vanilla's roman numeral past level 1, upstream's
     * {@code AbstractTraitLeveled#getLocalizedName}. A standalone trait is just its own name.
     */
    public static MutableComponent name(ResourceLocation id) {
        MutableComponent name = Component.translatable(langBase(id) + ".name");
        Rung rung = of(id);
        if (rung != null && rung.level() > 1) {
            name.append(CommonComponents.SPACE)
                    .append(Component.translatable("enchantment.level." + rung.level()));
        }
        return name;
    }

    /**
     * The family's one sentence, with this rung's own numbers in it -- which is how one lang entry
     * reads "2.5" on one rung and "4.0" on the next.
     */
    public static MutableComponent description(ResourceLocation id) {
        Rung rung = of(id);
        if (rung == null || rung.descriptionArgs().isEmpty()) {
            return Component.translatable(langBase(id) + ".description");
        }
        List<Object> args = new ArrayList<>(rung.descriptionArgs());
        return Component.translatable(langBase(id) + ".description", args.toArray());
    }

    private TraitFamilies() {}
}
