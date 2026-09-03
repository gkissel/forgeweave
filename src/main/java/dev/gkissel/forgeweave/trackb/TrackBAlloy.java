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
    public static final TrackBAlloy IRONBRAND = new TrackBAlloy("ironbrand", 0xBA6749, 1000);
    public static final TrackBAlloy QUAKESTONE = new TrackBAlloy("quakestone", 0x7A9B44, 1050);
    public static final TrackBAlloy SHARDLINE = new TrackBAlloy("shardline", 0xADE5FF, 1130);
    public static final TrackBAlloy EMBERCAST = new TrackBAlloy("embercast", 0xFFA800, 1150);
    public static final TrackBAlloy RIFTALLOY = new TrackBAlloy("riftalloy", 0xA04C9A, 1250);
    public static final TrackBAlloy TIDEIRON = new TrackBAlloy("tideiron", 0x007F98, 1080);
    public static final TrackBAlloy CINDERFORGE = new TrackBAlloy("cinderforge", 0xE07B00, 1090);
    public static final TrackBAlloy DREADALLOY = new TrackBAlloy("dreadalloy", 0x1E3E23, 1220);
    public static final TrackBAlloy SUNSTEEL = new TrackBAlloy("sunsteel", 0xDED26D, 1380);
    public static final TrackBAlloy HOLLOWSTEEL = new TrackBAlloy("hollowsteel", 0x83A3C3, 1420);
    public static final TrackBAlloy TRUESTEEL = new TrackBAlloy("truesteel", 0xDCFFFF, 1440);
    public static final TrackBAlloy STORMALLOY = new TrackBAlloy("stormalloy", 0x706DA4, 1260);
    public static final TrackBAlloy GLOWVEIL = new TrackBAlloy("glowveil", 0x17F3C5, 1330);
    public static final TrackBAlloy DAYBRASS = new TrackBAlloy("daybrass", 0xB6A643, 1230);
    public static final TrackBAlloy FAULTSTEEL = new TrackBAlloy("faultsteel", 0x836F40, 1180);
    public static final TrackBAlloy SKIPALLOY = new TrackBAlloy("skipalloy", 0x76D9D3, 1160);
    public static final TrackBAlloy MENDALLOY = new TrackBAlloy("mendalloy", 0x87D780, 1240);
    public static final TrackBAlloy MENDSTONE = new TrackBAlloy("mendstone", 0xE2B288, 1280);

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
    public static final TrackBAlloy ALUMITE = new TrackBAlloy("alumite", 0xF5C7F8, 1120);
    public static final TrackBAlloy OSGLOGLAS = new TrackBAlloy("osgloglas", 0x638D76, 1180);
    public static final TrackBAlloy OSMIRIDIUM = new TrackBAlloy("osmiridium", 0xC0A4D5, 1260);

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
