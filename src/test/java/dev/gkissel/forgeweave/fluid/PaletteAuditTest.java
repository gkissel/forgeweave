package dev.gkissel.forgeweave.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.block.UnstableOreBlock;
import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * Keeps Forgeweave's own colors far enough apart to tell apart (issue #928). The 2026-09-02 playtest
 * found two olive-yellow fuel rows, three teal ingots in one creative-tab row, three purples and
 * three reds; this test is the guard that stops the next hex from landing on top of a neighbour.
 *
 * <p>Same rules and same numbers as {@code scripts/audit_palette.py}, which reports them for a human
 * instead of failing a build. The script parses the Java tables out of the source files; this test
 * reads them straight off {@link ForgeweaveFluids}, {@link TrackBOre} and {@link TrackBAlloy}, and
 * only goes to disk for the two sprite scripts and the material JSONs, whose hexes have to agree
 * with the Java roster.
 *
 * <p>Distance is Euclidean in OKLab (dEok below). dEok 0.02 is roughly one just-noticeable step on a
 * large flat area, and a 16x16 sprite or a fluid tile needs a good deal more than that:
 *
 * <ul>
 *   <li>{@link #FLOOR} 0.030 for every pair of Forgeweave-owned tints -- the "these are the same
 *       hex" line.</li>
 *   <li>{@link #TRACK_B_MIN} 0.085 inside the Track B roster, which the creative tab shows as one
 *       block of ingot/nugget/block rows.</li>
 *   <li>{@link #FUEL_MIN} 0.20 between smeltery fuels, each of which also owns its own hue band --
 *       blazing blood is the only yellow, lava keeps vanilla orange (maintainer comment on #928).</li>
 *   <li>{@link #PLAYTEST_MIN} 0.080 for the pairs the playtest called out by name, which are all
     *       cross-group and so covered by nothing else here.</li>
 * </ul>
 *
 * <p>Out of scope, per the issue: vanilla fluids and compat-mod fluids whose color comes from the
 * other mod's own item. Vanilla lava is in the palette only as a fixed reference point, so the four
 * fuels Forgeweave does own have to stay clear of it.
 */
class PaletteAuditTest {

    private static final double FLOOR = 0.030;
    private static final double TRACK_B_MIN = 0.085;
    private static final double FUEL_MIN = 0.20;
    private static final double PLAYTEST_MIN = 0.080;

    /** The orange vanilla's lava still texture averages out to. Fixed: the fuel rule keeps clear of it. */
    private static final int LAVA_REFERENCE = 0xD45A12;

    /** OKLab hue arcs, one fuel each. Start inclusive, end exclusive, wrapping past 360. */
    private static final Map<String, double[]> HUE_BANDS = Map.of(
            "red", new double[] {348, 40},
            "orange", new double[] {40, 82},
            "yellow", new double[] {82, 122},
            "green", new double[] {122, 178},
            "blue", new double[] {178, 282},
            "violet", new double[] {282, 330},
            "pink", new double[] {330, 348});

    /**
     * Pairs where both tints are ported 1:1 from the 1.12 clone and cited by hex in NOTICE.md (gold
     * and electrum off upstream's {@code materialTextColor}, glass and silver likewise). Upstream
     * shipped them this close; moving either side is a parity deviation, which CLAUDE.md reserves
     * for an explicit maintainer decision, so the floor skips them instead of failing over someone
     * else's palette. {@code scripts/audit_palette.py} still prints them.
     */
    private static final Set<Set<String>> PARITY_LOCKED_PAIRS = Set.of(
            Set.of("molten_gold", "molten_electrum"),
            Set.of("molten_glass", "molten_silver"));

    private static final Path MATERIAL_DIR =
            Path.of("src/main/resources/data/forgeweave/forgeweave/material");
    private static final Path ORE_SCRIPT = Path.of("scripts/generate_track_b_ore_textures.py");
    private static final Path ALLOY_SCRIPT = Path.of("scripts/generate_track_b_alloy_textures.py");

    private static final Pattern SCRIPT_ENTRY = Pattern.compile("\\(\"([a-z_]+)\",\\s*0x([0-9A-Fa-f]{6})");
    private static final Pattern MATERIAL_COLOR = Pattern.compile("\"color\"\\s*:\\s*\"#([0-9A-Fa-f]{6})\"");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyForgeweaveTintClearsTheGlobalFloor() {
        Map<String, Integer> palette = palette();
        List<String> offenders = closerThan(palette, List.copyOf(palette.keySet()), FLOOR);

        assertTrue(offenders.isEmpty(), "tints closer than dEok " + FLOOR + " read as one color -- "
                + "retune one of each pair (scripts/audit_palette.py reports the same list):\n"
                + String.join("\n", offenders));
    }

    @Test
    void trackBRosterStaysReadableRowByRow() {
        Map<String, Integer> palette = palette();
        List<String> offenders = closerThan(palette, trackBIds(), TRACK_B_MIN);

        assertTrue(offenders.isEmpty(), "Track B materials closer than dEok " + TRACK_B_MIN
                + " blur into one another in the creative tab's ingot/nugget/block rows:\n"
                + String.join("\n", offenders));
    }

    @Test
    void eachFuelOwnsOneHue() {
        Map<String, Integer> palette = palette();
        List<String> fuels = List.of("lava", "blazing_blood", "molten_magma", "molten_brimspar",
                "molten_pyrealloy");

        Map<String, String> bands = new LinkedHashMap<>();
        for (String fuel : fuels) {
            bands.put(fuel, hueBand(palette.get(fuel)));
        }

        assertEquals(fuels.size(), Set.copyOf(bands.values()).size(),
                "every smeltery fuel needs a hue of its own, but these share one: " + bands);
        assertEquals("yellow", bands.get("blazing_blood"), "blazing blood is the only yellow fuel");
        assertEquals("orange", bands.get("lava"), "lava keeps vanilla orange");

        List<String> offenders = closerThan(palette, fuels, FUEL_MIN);
        assertTrue(offenders.isEmpty(), "fuel rows sit next to each other in JEI, so they need dEok "
                + FUEL_MIN + " between them:\n" + String.join("\n", offenders));
    }

    @Test
    void pairsThePlaytestNamedStayApart() {
        // The 2026-09-02 playtest listed these by hand. They cross groups, so no other rule here
        // covers them; keep them apart whatever else moves around them.
        List<ForgeweaveFluids.MoltenMetal[]> pairs = List.of(
                new ForgeweaveFluids.MoltenMetal[] {ForgeweaveFluids.DRACONIUM, ForgeweaveFluids.PSIMETAL},
                new ForgeweaveFluids.MoltenMetal[] {ForgeweaveFluids.DRACONIUM, ForgeweaveFluids.EMERALD},
                new ForgeweaveFluids.MoltenMetal[] {ForgeweaveFluids.PSIMETAL, ForgeweaveFluids.EMERALD},
                new ForgeweaveFluids.MoltenMetal[] {ForgeweaveFluids.COBALT, ForgeweaveFluids.OSMIUM},
                new ForgeweaveFluids.MoltenMetal[] {ForgeweaveFluids.MANYULLYN, ForgeweaveFluids.DRACONIUM_AWAKENED},
                new ForgeweaveFluids.MoltenMetal[] {ForgeweaveFluids.MANYULLYN, ForgeweaveFluids.PULSATING_ALLOY},
                new ForgeweaveFluids.MoltenMetal[] {ForgeweaveFluids.DRACONIUM_AWAKENED, ForgeweaveFluids.PULSATING_ALLOY});

        List<String> offenders = new ArrayList<>();
        for (ForgeweaveFluids.MoltenMetal[] pair : pairs) {
            double gap = distance(pair[0].color(), pair[1].color());
            if (gap < PLAYTEST_MIN) {
                offenders.add(String.format("%.4f  %s / %s", gap, pair[0].name(), pair[1].name()));
            }
        }

        assertTrue(offenders.isEmpty(), "these pairs were flagged by hand in the #928 playtest and "
                + "have to stay at least dEok " + PLAYTEST_MIN + " apart:\n" + String.join("\n", offenders));
    }

    @Test
    void trackBMaterialsAreOneColorEverywhere() throws IOException {
        Path root = projectRoot();
        Map<String, Integer> spriteHexes = new HashMap<>();
        spriteHexes.putAll(scriptHexes(root.resolve(ORE_SCRIPT)));
        spriteHexes.putAll(scriptHexes(root.resolve(ALLOY_SCRIPT)));

        Map<String, Integer> tints = fluidTints();
        List<String> offenders = new ArrayList<>();

        for (Map.Entry<String, Integer> material : trackBColors().entrySet()) {
            String id = material.getKey();
            int color = material.getValue();
            record Source(String label, Integer color) {}
            List<Source> sources = new ArrayList<>(List.of(
                    new Source("sprite script", spriteHexes.get(id)),
                    new Source("fluid tint", tints.get("molten_" + id)),
                    new Source("material JSON", materialColor(root, id))));
            if (id.equals("brimspar")) {
                sources.add(new Source("crystal tint", UnstableOreBlock.BRIMSPAR_CRYSTAL_COLOR));
            }
            for (Source source : sources) {
                if (source.color() != null && source.color() != color) {
                    offenders.add(String.format("%s: %s is #%06X, roster says #%06X",
                            id, source.label(), source.color(), color));
                }
            }
        }

        assertTrue(offenders.isEmpty(), "a material's fluid, ingot, nugget, block, ore and crystal "
                + "are one family and share one hex:\n" + String.join("\n", offenders));
    }

    // ------------------------------------------------------------------ palette sources

    private static Map<String, Integer> fluidTints() {
        Map<String, Integer> tints = new LinkedHashMap<>();
        for (ForgeweaveFluids.MoltenMetal fluid : ForgeweaveFluids.all()) {
            tints.put(fluid.name(), fluid.color());
        }
        return tints;
    }

    /** Every Forgeweave-owned tint, plus vanilla lava as a fixed reference point. */
    private static Map<String, Integer> palette() {
        Map<String, Integer> palette = fluidTints();
        palette.put("lava", LAVA_REFERENCE);
        return palette;
    }

    /** The Track B roster: 11 ores, brimspar (the standalone fuel ore), 21 alloys. */
    private static Map<String, Integer> trackBColors() {
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (TrackBOre ore : TrackBOre.ALL) {
            colors.put(ore.id(), ore.color());
        }
        colors.put("brimspar", ForgeweaveFluids.BRIMSPAR.color());
        for (TrackBAlloy alloy : TrackBAlloy.ALL) {
            colors.put(alloy.id(), alloy.color());
        }
        return colors;
    }

    private static List<String> trackBIds() {
        return trackBColors().keySet().stream().map(id -> "molten_" + id).toList();
    }

    private static Map<String, Integer> scriptHexes(Path script) throws IOException {
        Map<String, Integer> hexes = new LinkedHashMap<>();
        Matcher matcher = SCRIPT_ENTRY.matcher(Files.readString(script, StandardCharsets.UTF_8));
        while (matcher.find()) {
            hexes.put(matcher.group(1), Integer.parseInt(matcher.group(2), 16));
        }
        return hexes;
    }

    private static Integer materialColor(Path root, String id) throws IOException {
        Path path = root.resolve(MATERIAL_DIR).resolve(id + ".json");
        if (!Files.isRegularFile(path)) {
            return null;
        }
        Matcher matcher = MATERIAL_COLOR.matcher(Files.readString(path, StandardCharsets.UTF_8));
        return matcher.find() ? Integer.parseInt(matcher.group(1), 16) : null;
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle above " + dir + ")");
    }

    // ------------------------------------------------------------------ OKLab

    private static List<String> closerThan(Map<String, Integer> palette, List<String> names, double minimum) {
        List<String> offenders = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                if (PARITY_LOCKED_PAIRS.contains(Set.of(names.get(i), names.get(j)))) {
                    continue;
                }
                double gap = distance(palette.get(names.get(i)), palette.get(names.get(j)));
                if (gap < minimum) {
                    offenders.add(String.format("%.4f  %s / %s", gap, names.get(i), names.get(j)));
                }
            }
        }
        offenders.sort(null);
        return offenders;
    }

    private static double distance(int one, int other) {
        double[] a = oklab(one);
        double[] b = oklab(other);
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }

    /** OKLab (L, a, b) of an 0xRRGGBB color, via Bjorn Ottosson's published matrices. */
    private static double[] oklab(int color) {
        double r = linear((color >> 16) & 0xFF);
        double g = linear((color >> 8) & 0xFF);
        double b = linear(color & 0xFF);

        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);

        return new double[] {
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    private static double linear(int channel) {
        double u = channel / 255.0;
        return u <= 0.04045 ? u / 12.92 : Math.pow((u + 0.055) / 1.055, 2.4);
    }

    private static String hueBand(int color) {
        double[] lab = oklab(color);
        double hue = (Math.toDegrees(Math.atan2(lab[2], lab[1])) + 360) % 360;
        for (Map.Entry<String, double[]> band : HUE_BANDS.entrySet()) {
            double start = band.getValue()[0];
            double end = band.getValue()[1];
            boolean inside = start <= end ? hue >= start && hue < end : hue >= start || hue < end;
            if (inside) {
                return band.getKey();
            }
        }
        throw new AssertionError(String.format("no hue band covers #%06X", color));
    }
}
