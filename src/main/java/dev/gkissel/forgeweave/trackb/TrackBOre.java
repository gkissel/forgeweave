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
 * <p><b>Distribution table (per #839 deliverable 4 -- JC11 already answered the sourcing-mechanism
 * question on #824/#838, this is only the height/rarity/dimension shape; tier placement is #877's,
 * see {@link Tier}):</b> {@link #STARFALL_STONE} and {@link #VOIDGLASS} are the reference ladder's
 * former meteor-fall pair (JC11, #839's "meteor question"): decided already on #824/#838 as ordinary
 * rare ore veins/features rather than a new falling-entity mechanic, executed here as the same
 * {@code minecraft:ore} feature every other entry uses, just tuned to a shallow near-surface band
 * (starfall_stone, standing in for "rare surface feature").
 *
 * <p><b>#883 (maintainer directive, 2026-08-31):</b> {@link #VOIDGLASS} moves from the Overworld
 * deepslate deepest band to {@link Host#END} ({@code minecraft:end_stone}, generated only in the
 * End's outer-island biomes via {@code track_b_end_ores.json} -- see {@link Host#END}'s own javadoc),
 * making it the game's uniquely rarest ore; {@link #STARFALL_STONE} bumps from count 1 to 2 so
 * voidglass alone keeps count 1.
 *
 * <p><b>#909 (maintainer directive, 2026-09-02, out of the reference-ladder parity audit) supersedes
 * #839's own dimension split.</b> #839 had kept nine of the eleven in Overworld deepslate and sent
 * only voltcinder/hardcinder to the Nether, on a "don't trivialise cobalt/ardite's Nether niche"
 * reading; the maintainer's answer is that each ore should generate where its reference counterpart
 * does. The roster is now <b>Nether</b> ({@link Host#NETHER}, netherrack): {@link #FULMENITE},
 * {@link #MURKIRON}, {@link #WARSPAR} -- joining brimspar, the standalone fuel ore, which keeps its
 * own biome modifier; <b>End</b> ({@link Host#END}, end stone, outer islands only):
 * {@link #DUSKSPAR}, {@link #NIGHTSHALE}, {@link #HOLLOWSTONE}, {@link #VOIDGLASS};
 * <b>Overworld</b>: {@link #HARDCINDER}, {@link #VOLTCINDER} and {@link #RESONITE} in deepslate,
 * {@link #STARFALL_STONE} in surface stone. voidglass is the one deliberate departure from the
 * reference (which puts it in the Overworld): #883's directive above stands. Each ore's height band
 * and rate are re-picked for the column it now lives in -- the reference's own 1.12 y ranges are a
 * guide, not a target, since 1.21's Nether and End columns differ. The per-ore rationale lives with
 * the table in {@code scripts/generate_track_b_worldgen.py}, which emits the actual worldgen JSON;
 * this record's own fields must be kept in step with it by hand (see that script's docstring), and
 * {@code TrackBOreGameTests} fails the build if an ore's host block or biome modifier disagree.
 */
public record TrackBOre(String id, Tier tier, Host host, int veinSize, int ratePerChunk, int minY, int maxY,
        int color, boolean dropsCrystal) {

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
        NETHER("minecraft:netherrack", "minecraft:is_nether"),
        /**
         * #883 -- the End's own outer-island biomes only (end_highlands, end_midlands, end_barrens,
         * small_end_islands), not the blanket {@code minecraft:is_end} tag, which also covers
         * {@code minecraft:the_end} (the small central island the dragon fight uses). No single
         * vanilla tag already excludes the central island, so {@code biomeTag} here is a
         * comma-separated explicit biome id list rather than one tag reference, and
         * {@code track_b_end_ores.json} lists those four ids directly.
         */
        END("minecraft:end_stone",
                "minecraft:end_highlands,minecraft:end_midlands,minecraft:end_barrens,minecraft:small_end_islands");

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
    //
    // Issue #884 (1): the stone-tier entry this table used to anchor here, "cinderstone", is retired
    // -- the maintainer wants the stone-tier Basalt-flavored material to BE vanilla basalt, not a
    // custom Track B ore. See dev.gkissel.forgeweave.material's basalt.json (Part-Builder-only, no
    // ore/worldgen presence) and ForgeweaveTraits#EARTHMEND for its trait. No replacement TrackBOre
    // entry was added; Track B's ore-sourced roster is 11 materials as of #884, not 12.
    //
    // #929 -- the trailing boolean is dropsCrystal: true for fulmenite alone, the maintainer's
    // "materials that are crystals in the reference ladder stay crystals" directive (2026-09-02).
    // Fulmenite's ore block drops fulmenite_crystal instead of a raw ore item -- brimspar's own shape
    // (#903) -- while keeping its ingot/nugget/storage block, unlike brimspar (a fuel-only material
    // with no tool form at all). See ForgeweaveBlocks/ForgeweaveItems' Track B loops, which read this
    // flag instead of special-casing FULMENITE by identity the way the unstable-ore block switch still
    // does below (a separate axis -- whether harvesting can detonate the vein, not what it drops).
    public static final TrackBOre FULMENITE =
            new TrackBOre("fulmenite", Tier.DIAMOND, Host.NETHER, 5, 6, 10, 108, 0xC8D94A, true);
    public static final TrackBOre DUSKSPAR =
            new TrackBOre("duskspar", Tier.NETHERITE, Host.END, 4, 3, 32, 56, 0x8A5FD9, false);
    public static final TrackBOre VOLTCINDER =
            new TrackBOre("voltcinder", Tier.NETHERITE, Host.OVERWORLD_DEEPSLATE, 4, 2, -64, -48, 0x38D9D0, false);
    public static final TrackBOre MURKIRON =
            new TrackBOre("murkiron", Tier.HARDCINDER, Host.NETHER, 4, 3, 8, 64, 0x3A5C56, false);
    public static final TrackBOre HARDCINDER =
            new TrackBOre("hardcinder", Tier.HARDCINDER, Host.OVERWORLD_DEEPSLATE, 4, 4, -48, 16, 0xC23B2B, false);
    public static final TrackBOre NIGHTSHALE =
            new TrackBOre("nightshale", Tier.HARDCINDER, Host.END, 4, 3, 44, 72, 0x3B3F7A, false);
    public static final TrackBOre WARSPAR =
            new TrackBOre("warspar", Tier.WARSPAR, Host.NETHER, 4, 2, 0, 120, 0xA4283F, false);
    public static final TrackBOre HOLLOWSTONE =
            new TrackBOre("hollowstone", Tier.WARSPAR, Host.END, 4, 2, 0, 96, 0xD8D3C2, false);
    public static final TrackBOre RESONITE =
            new TrackBOre("resonite", Tier.RESONITE, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0x3FAE9E, false);
    // JC11 -- former meteor pair, now ordinary rare veins/surface feature (see class javadoc). Counts
    // rebalanced by #883 (2026-08-31): voidglass alone keeps count 1, moved to the End so it's the
    // game's uniquely rarest ore; starfall_stone bumps to 2 so it's no longer tied with voidglass.
    public static final TrackBOre STARFALL_STONE =
            new TrackBOre("starfall_stone", Tier.NETHERITE, Host.OVERWORLD_STONE, 3, 2, 62, 90, 0xBCD6F2, false);
    public static final TrackBOre VOIDGLASS =
            new TrackBOre("voidglass", Tier.NETHERITE, Host.END, 3, 1, 0, 255, 0x2A1740, false);

    public static final List<TrackBOre> ALL = List.of(FULMENITE, DUSKSPAR, VOLTCINDER, MURKIRON,
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

    /** #929 -- the crystal item {@link #dropsCrystal} ores drop instead of {@link #rawItemId()}. */
    public String crystalItemId() {
        return id + "_crystal";
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
