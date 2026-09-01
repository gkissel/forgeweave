package dev.gkissel.forgeweave.trackb;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The roster of Track B's 12 ore-sourced materials (issue #839, epic #824's Track B: "self-contained
 * original materials"). Ids come from the naming scaffold the maintainer approved on #838/#861 --
 * {@code docs/research/m6-material-expansion-references.md} §7.3 ("Ore-sourced" table) -- so this
 * class adds no new naming vocabulary of its own, just the block/worldgen shape #839 needs to build
 * off it: which base rock/dimension each ore targets, its vein size and rate, and its height band.
 *
 * <p>Tier assignments were re-rung by issue #877 (the JC10 reversal, 2026-08-31 maintainer directive):
 * #838's original "collapse everything above diamond onto the shared netherite rung" mapping (§7.1 of
 * the research doc) is superseded -- see {@link Tier}'s own javadoc for the replacement ladder.
 *
 * <p>Every provider that needs to repeat the same six registrations per material (blocks, items,
 * loot, tags, models, lang) walks {@link #ALL} instead of listing each material by hand -- the same
 * anti-drift shape {@code ForgeweaveFluids#all}/{@code ForgeweaveBlocks#clearStainedGlassColors()}
 * already use for their own per-instance families.
 *
 * <p><b>Distribution table (this PR's own design decision, per #839 deliverable 4 -- JC11 already
 * answered the sourcing-mechanism question on #824/#838, this is only the height/rarity/dimension
 * shape; tier placement is #877's, see {@link Tier}):</b> to avoid trivialising cobalt/ardite's own
 * Nether niche only two entries (voltcinder, hardcinder -- both fire/cinder-flavored names) generate
 * in the Nether; the rest spread across Overworld deepslate at varying rarity. {@link #STARFALL_STONE}
 * and {@link #VOIDGLASS} are the reference ladder's former meteor-fall pair (JC11, #839's "meteor
 * question"): decided already on #824/#838 as ordinary rare ore veins/features rather than a new
 * falling-entity mechanic, executed here as the same {@code minecraft:ore} feature every other entry
 * uses, just tuned to a shallow near-surface band (starfall_stone, standing in for "rare surface
 * feature") and the deepest rare band (voidglass, "rare vein") respectively -- no bespoke feature type
 * needed for either.
 */
public record TrackBOre(String id, Tier tier, Host host, int veinSize, int ratePerChunk, int minY, int maxY, int color) {

    /**
     * The mining-capability ladder (issue #877, the JC10 reversal): three new rungs mint above
     * Forgeweave's previous top ({@code minecraft:incorrect_for_netherite_tool}), each named after the
     * Track B material that anchors it -- mirroring vanilla's own convention of naming a tier after
     * the material that defines it (iron pick, diamond pick, ...). The three new names are the
     * research doc §7.3 renamings of the reference ladder's own "duranite" (one step above its
     * cobalt-equivalent), "valyrium" and "vibranium" rungs (§2): {@code hardcinder}, {@code warspar},
     * {@code resonite}. Each rung's own tag lives on {@link #INCORRECT_FOR_HARDCINDER_TOOL} and
     * friends below, mirroring the vanilla/NeoForge {@code incorrect_for_<tier>_tool} pattern
     * ({@code ForgeweaveBlockTagsProvider} wires the block side, {@code ForgeweaveModifiers#TIER_TAGS}
     * the tool side).
     */
    public enum Tier {
        /** {@code minecraft:incorrect_for_wooden_tool}-adjacent -- mineable with any pickaxe, no needs_*_tool tag. */
        STONE,
        /** {@code minecraft:needs_iron_tool} -- an iron pickaxe or better. */
        DIAMOND,
        /** {@code minecraft:needs_diamond_tool} + {@code minecraft:incorrect_for_diamond_tool} -- netherite pickaxe only, same combo as cobalt/ardite. */
        NETHERITE,
        /** Needs a hardcinder-tier pick or better; denied to netherite-tier tools and below (#877). */
        HARDCINDER,
        /** Needs a warspar-tier pick or better; denied to hardcinder-tier tools and below (#877). */
        WARSPAR,
        /** Needs a resonite-tier pick or better -- the new top rung; denied to warspar-tier tools and below (#877). */
        RESONITE
    }

    /**
     * The three new tier boundary tags #877 mints, one per new rung -- the tag a tool built from a
     * material <em>at</em> that rung uses as its own {@code incorrect_for_tool} (what that rung's
     * tools cannot mine), populated by {@code ForgeweaveBlockTagsProvider} with every ore at a
     * <em>higher</em> rung. Empty until #877, exactly like {@code minecraft:incorrect_for_netherite_tool}
     * was before it.
     */
    public static final TagKey<Block> INCORRECT_FOR_HARDCINDER_TOOL = tag("incorrect_for_hardcinder_tool");
    public static final TagKey<Block> INCORRECT_FOR_WARSPAR_TOOL = tag("incorrect_for_warspar_tool");
    public static final TagKey<Block> INCORRECT_FOR_RESONITE_TOOL = tag("incorrect_for_resonite_tool");

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path));
    }

    /** Which base block and biome tag a material's ore feature targets. */
    public enum Host {
        OVERWORLD_STONE("minecraft:stone", "minecraft:is_overworld"),
        OVERWORLD_DEEPSLATE("minecraft:deepslate", "minecraft:is_overworld"),
        NETHER("minecraft:netherrack", "minecraft:is_nether");

        public final String targetBlock;
        public final String biomeTag;

        Host(String targetBlock, String biomeTag) {
            this.targetBlock = targetBlock;
            this.biomeTag = biomeTag;
        }
    }

    // Research doc §7.3 "Ore-sourced" table, in that table's own order. Colors are this PR's own
    // pick (materials/traits/stat curve are #841's deliverable, not #839's) -- distinct, roughly
    // themed hues for the recolor-from-vanilla art pipeline (scripts/generate_track_b_ore_textures.py).
    public static final TrackBOre CINDERSTONE =
            new TrackBOre("cinderstone", Tier.STONE, Host.OVERWORLD_STONE, 6, 12, 0, 128, 0x8A8A86);
    public static final TrackBOre FULMENITE =
            new TrackBOre("fulmenite", Tier.DIAMOND, Host.OVERWORLD_DEEPSLATE, 5, 6, -24, 32, 0xC8D94A);
    public static final TrackBOre DUSKSPAR =
            new TrackBOre("duskspar", Tier.NETHERITE, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0x8A5FD9);
    public static final TrackBOre VOLTCINDER =
            new TrackBOre("voltcinder", Tier.NETHERITE, Host.NETHER, 4, 6, 0, 127, 0x38D9D0);
    public static final TrackBOre MURKIRON =
            new TrackBOre("murkiron", Tier.HARDCINDER, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0x3A5C56);
    public static final TrackBOre HARDCINDER =
            new TrackBOre("hardcinder", Tier.HARDCINDER, Host.NETHER, 4, 6, 0, 127, 0xC23B2B);
    public static final TrackBOre NIGHTSHALE =
            new TrackBOre("nightshale", Tier.HARDCINDER, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0x3B3F7A);
    public static final TrackBOre WARSPAR =
            new TrackBOre("warspar", Tier.WARSPAR, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0xA4283F);
    public static final TrackBOre HOLLOWSTONE =
            new TrackBOre("hollowstone", Tier.WARSPAR, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0xD8D3C2);
    public static final TrackBOre RESONITE =
            new TrackBOre("resonite", Tier.RESONITE, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0x3FAE9E);
    // JC11 -- former meteor pair, now ordinary rare veins/surface feature (see class javadoc).
    public static final TrackBOre STARFALL_STONE =
            new TrackBOre("starfall_stone", Tier.NETHERITE, Host.OVERWORLD_STONE, 3, 1, 62, 90, 0xBCD6F2);
    public static final TrackBOre VOIDGLASS =
            new TrackBOre("voidglass", Tier.NETHERITE, Host.OVERWORLD_DEEPSLATE, 3, 1, -64, -48, 0x2A1740);

    public static final List<TrackBOre> ALL = List.of(CINDERSTONE, FULMENITE, DUSKSPAR, VOLTCINDER, MURKIRON,
            HARDCINDER, NIGHTSHALE, WARSPAR, HOLLOWSTONE, RESONITE, STARFALL_STONE, VOIDGLASS);

    public String oreBlockId() {
        return id + "_ore";
    }

    public String storageBlockId() {
        return id + "_block";
    }

    public String rawBlockId() {
        return "raw_" + id + "_block";
    }

    public String ingotId() {
        return id + "_ingot";
    }

    public String nuggetId() {
        return id + "_nugget";
    }

    public String rawItemId() {
        return "raw_" + id;
    }

    /** Title-cased display name for lang entries, e.g. {@code "starfall_stone"} -> {@code "Starfall Stone"}. */
    public String displayName() {
        String[] words = id.split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }
}
