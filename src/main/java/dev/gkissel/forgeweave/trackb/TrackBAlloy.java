package dev.gkissel.forgeweave.trackb;

import java.util.List;

/**
 * The roster of Track B's 18 alloy tool materials (issue #840, epic #824's Track B) -- the "upper
 * rungs" the ore-sourced roster ({@link TrackBOre}) feeds into at the smeltery's alloy table. Ids come
 * from the naming scaffold the maintainer approved on #838/#861 -- {@code
 * docs/research/m6-material-expansion-references.md} §7.3's "Alloy" table -- so this class mints no
 * vocabulary of its own.
 *
 * <p>Unlike {@link TrackBOre}, every entry here is alloy-only: no ore block, no raw drop, the same
 * "ingot/nugget/block only" shape {@code ForgeweaveItems}' pig iron and knightslime already use. Color
 * and temperature are this issue's own design decision (deliverable 4, "the alloy table itself"), not
 * a port of anything -- ADR-0003 keeps TAIGA's alloy *graph* to inspiration-only, so ratios, inputs and
 * these fluid properties are Forgeweave's own numbers. Temperatures increase with dependency depth
 * (alloys run hotter than their own inputs, the same shape manyullyn already follows over
 * cobalt/ardite), and stay below {@code ForgeweaveFluids#BLAZING_BLOOD}'s 1500 -- the deliberate
 * "hottest fluid in the game" headroom that constant's own javadoc documents.
 *
 * <p>Only the fluid/item/casting/melting/alloy-table concerns of #840 live here. Tool stats and
 * traits are #841's roster deliverable, blocked on nothing this class defines -- these materials have
 * no {@code Material} JSON yet, so (like every other alloy-only Forgeweave metal today) they are
 * plain items with no stat/trait linkage until #841 lands one.
 */
public record TrackBAlloy(String id, int color, int temperature) {

    // Research doc §7.3 "Alloy" table, in that table's own order.
    public static final TrackBAlloy IRONBRAND = new TrackBAlloy("ironbrand", 0xB5502C, 1000);
    public static final TrackBAlloy QUAKESTONE = new TrackBAlloy("quakestone", 0x8FA35E, 1050);
    public static final TrackBAlloy SHARDLINE = new TrackBAlloy("shardline", 0xA9D8E0, 1130);
    public static final TrackBAlloy EMBERCAST = new TrackBAlloy("embercast", 0xE0611A, 1150);
    public static final TrackBAlloy RIFTALLOY = new TrackBAlloy("riftalloy", 0x7A3FA0, 1250);
    public static final TrackBAlloy TIDEIRON = new TrackBAlloy("tideiron", 0x2F7A7A, 1080);
    public static final TrackBAlloy CINDERFORGE = new TrackBAlloy("cinderforge", 0xD1350B, 1090);
    public static final TrackBAlloy DREADALLOY = new TrackBAlloy("dreadalloy", 0x2B3B2B, 1220);
    public static final TrackBAlloy SUNSTEEL = new TrackBAlloy("sunsteel", 0xE6C64A, 1380);
    public static final TrackBAlloy HOLLOWSTEEL = new TrackBAlloy("hollowsteel", 0x9FB6C2, 1420);
    public static final TrackBAlloy TRUESTEEL = new TrackBAlloy("truesteel", 0xC7D6E8, 1440);
    public static final TrackBAlloy STORMALLOY = new TrackBAlloy("stormalloy", 0x5C5B7A, 1260);
    public static final TrackBAlloy GLOWVEIL = new TrackBAlloy("glowveil", 0x4AE6C6, 1330);
    public static final TrackBAlloy DAYBRASS = new TrackBAlloy("daybrass", 0xC9A227, 1230);
    public static final TrackBAlloy FAULTSTEEL = new TrackBAlloy("faultsteel", 0x7A6852, 1180);
    public static final TrackBAlloy SKIPALLOY = new TrackBAlloy("skipalloy", 0x6FD1D1, 1160);
    public static final TrackBAlloy MENDALLOY = new TrackBAlloy("mendalloy", 0x7FBF6B, 1240);
    public static final TrackBAlloy MENDSTONE = new TrackBAlloy("mendstone", 0xC2A878, 1280);

    // #873 -- the three PlusTiC-inspiration alloys (M6 epic #824's JC3-reversal deliverable 5):
    // alumite (aluminium + iron + obsidian), osgloglas (osmium + refined obsidian + glass), osmiridium
    // (osmium + iridium). Unlike every entry above, these are Track A-adjacent -- at least one input
    // is a compat metal, so the {@code alloy_recipe} and {@code material} JSON that back them carry a
    // {@code neoforge:conditions} gate on that input's provider (deliverable 4 of #873's issue text).
    // They still reuse this class and every wiring loop keyed off {@link #ALL}: item/block
    // registration is unconditional Java either way (the same NeoForge platform constraint every
    // other fluid/item in this file lives under), so nothing about *this* roster's shape changes --
    // only the datapack recipes gate. Colors are a blend of each alloy's own inputs' existing material
    // colors (aluminium/iron/obsidian; osmium/refined_obsidian/glass; osmium/iridium); temperatures
    // continue this class's own "alloys run hotter than their inputs" scale.
    public static final TrackBAlloy ALUMITE = new TrackBAlloy("alumite", 0xB8B2C9, 1120);
    public static final TrackBAlloy OSGLOGLAS = new TrackBAlloy("osgloglas", 0x7FA8A3, 1180);
    public static final TrackBAlloy OSMIRIDIUM = new TrackBAlloy("osmiridium", 0xC9C2D6, 1260);

    public static final List<TrackBAlloy> ALL = List.of(IRONBRAND, QUAKESTONE, SHARDLINE, EMBERCAST,
            RIFTALLOY, TIDEIRON, CINDERFORGE, DREADALLOY, SUNSTEEL, HOLLOWSTEEL, TRUESTEEL, STORMALLOY,
            GLOWVEIL, DAYBRASS, FAULTSTEEL, SKIPALLOY, MENDALLOY, MENDSTONE,
            ALUMITE, OSGLOGLAS, OSMIRIDIUM);

    public String ingotId() {
        return id + "_ingot";
    }

    public String nuggetId() {
        return id + "_nugget";
    }

    public String blockId() {
        return id + "_block";
    }

    /** Title-cased display name for lang entries, e.g. {@code "ironbrand"} -&gt; {@code "Ironbrand"}. */
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
