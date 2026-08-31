package dev.gkissel.forgeweave.trackb;

import java.util.List;

/**
 * The roster of Track B's 12 ore-sourced materials (issue #839, epic #824's Track B: "self-contained
 * original materials"). Ids and tier assignments come straight from the naming scaffold the
 * maintainer approved on #838/#861 -- {@code docs/research/m6-material-expansion-references.md} §7.3
 * ("Ore-sourced" table) and §7.1 (tier-to-rung mapping) -- so this class adds no new vocabulary of
 * its own, just the block/worldgen shape #839 needs to build off it: which base rock/dimension each
 * ore targets, its vein size and rate, and its height band.
 *
 * <p>Every provider that needs to repeat the same six registrations per material (blocks, items,
 * loot, tags, models, lang) walks {@link #ALL} instead of listing each material by hand -- the same
 * anti-drift shape {@code ForgeweaveFluids#all}/{@code ForgeweaveBlocks#clearStainedGlassColors()}
 * already use for their own per-instance families.
 *
 * <p><b>Distribution table (this PR's own design decision, per #839 deliverable 4 -- JC10/JC11
 * already answered the tier and sourcing-mechanism questions on #824/#838, this is only the
 * height/rarity/dimension shape):</b> ten of the twelve sit on Forgeweave's existing top mining rung
 * (matching cobalt/ardite/manyullyn/netherite/obsidian/ancient, per the M6 tier scaffold), so to avoid
 * trivialising cobalt/ardite's own Nether niche only two of the ten (voltcinder, hardcinder --
 * both fire/cinder-flavored names) generate in the Nether; the rest spread across Overworld deepslate
 * at varying rarity. {@link #STARFALL_STONE} and {@link #VOIDGLASS} are the reference ladder's former
 * meteor-fall pair (JC11, #839's "meteor question"): decided already on #824/#838 as ordinary rare
 * ore veins/features rather than a new falling-entity mechanic, executed here as the same
 * {@code minecraft:ore} feature every other entry uses, just tuned to a shallow near-surface band
 * (starfall_stone, standing in for "rare surface feature") and the deepest rare band (voidglass,
 * "rare vein") respectively -- no bespoke feature type needed for either.
 */
public record TrackBOre(String id, Tier tier, Host host, int veinSize, int ratePerChunk, int minY, int maxY, int color) {

    /** Maps onto the M6 tier scaffold's existing rungs (research doc §7.1) -- no new block tags. */
    public enum Tier {
        /** {@code minecraft:incorrect_for_wooden_tool}-adjacent -- mineable with any pickaxe, no needs_*_tool tag. */
        STONE,
        /** {@code minecraft:needs_iron_tool} -- an iron pickaxe or better. */
        DIAMOND,
        /** {@code minecraft:needs_diamond_tool} + {@code minecraft:incorrect_for_diamond_tool} -- netherite pickaxe only, same combo as cobalt/ardite. */
        NETHERITE
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
            new TrackBOre("murkiron", Tier.NETHERITE, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0x3A5C56);
    public static final TrackBOre HARDCINDER =
            new TrackBOre("hardcinder", Tier.NETHERITE, Host.NETHER, 4, 6, 0, 127, 0xC23B2B);
    public static final TrackBOre NIGHTSHALE =
            new TrackBOre("nightshale", Tier.NETHERITE, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0x3B3F7A);
    public static final TrackBOre WARSPAR =
            new TrackBOre("warspar", Tier.NETHERITE, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0xA4283F);
    public static final TrackBOre HOLLOWSTONE =
            new TrackBOre("hollowstone", Tier.NETHERITE, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0xD8D3C2);
    public static final TrackBOre RESONITE =
            new TrackBOre("resonite", Tier.NETHERITE, Host.OVERWORLD_DEEPSLATE, 4, 3, -64, -16, 0x3FAE9E);
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
