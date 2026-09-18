package dev.gkissel.forgeweave.compat.occultism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * Issue #997's generator half: the {@code occultism:crushing} and {@code occultism:miner} rows
 * {@code ForgeweaveOccultismRecipeProvider} writes, read back off {@code src/generated/resources/}
 * the way {@code BookLangCoverageTest} and friends already read generated output.
 *
 * <p>What this pins is the two things the issue calls out as the failure modes worth a test: the
 * generator being <b>total</b> over the eleven Track B ores rather than covering the ones someone
 * remembered, and the tier tables being <b>monotone</b>, so a higher rung is never easier to crush
 * or more common to mine than a lower one. Both are properties of the table rather than of one row,
 * which is why they are cheap to pin and expensive to notice by hand.
 *
 * <p>Names no Occultism type on purpose, so it runs whether or not the jar resolved --
 * {@code OccultismSourceIsolationTest} counts the shipped classes that do.
 */
class OccultismRecipeTest {

    private static final int ORES = 11;

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static JsonObject generated(String kind, String name) throws IOException {
        Path file = projectRoot().resolve("src/generated/resources/data/forgeweave/recipe/compat/occultism")
                .resolve(kind).resolve(name + ".json");
        assertTrue(Files.isRegularFile(file), "expected a generated row at " + file
                + " -- ForgeweaveOccultismRecipeProvider must cover every Track B ore, and runData must have run");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static List<TrackBOre> ores() {
        return TrackBOre.ALL;
    }

    /** The roster the generator walks is the eleven #839/#884 left, not a hand-kept copy of it. */
    @Test
    void theRosterIsTheElevenTrackBOres() {
        assertEquals(ORES, TrackBOre.ALL.size(),
                "Track B's ore-sourced roster changed size; the crushing and miner tables walk it, so this"
                        + " test's own count is the only thing that needs updating");
    }

    @ParameterizedTest
    @MethodSource("ores")
    void everyOreCrushesFromItsOreBlockAtItsOwnTier(TrackBOre ore) throws IOException {
        JsonObject json = generated("crushing", ore.id() + "_from_ore");

        assertEquals("occultism:crushing", json.get("type").getAsString());
        assertEquals("c:ores/" + ore.id(), json.getAsJsonObject("ingredient").get("tag").getAsString());
        assertEquals(ForgeweaveOccultismCompat.crusherTier(ore.tier()), json.get("min_tier").getAsInt(),
                ore.id() + "'s min_tier must be its own tier's crusher rank, not a shared default");

        JsonObject result = json.getAsJsonObject("result");
        assertEquals("occultism:tag", result.get("type").getAsString(),
                "Occultism's result codec is registry-dispatched, so the nested type is required");
        assertEquals("c:ingots/" + ore.id(), result.get("tag").getAsString());
        assertFalse(json.get("ignore_crushing_multiplier").getAsBoolean(),
                "an ore block is where a crusher spirit's output multiplier is supposed to pay off");
        assertTrue(modLoadedGated(json), ore.id() + "'s ore row must be gated on occultism");
    }

    @ParameterizedTest
    @MethodSource("ores")
    void everyOreCrushesFromItsRawFormAtTheSameTier(TrackBOre ore) throws IOException {
        JsonObject json = generated("crushing", ore.id() + "_from_raw");

        // #929: fulmenite's ore drops a crystal rather than a raw item, so its second row keys on
        // c:gems/<id>. Every other ore keys on c:raw_materials/<id>.
        String expected = ore.dropsCrystal() ? "c:gems/" + ore.id() : "c:raw_materials/" + ore.id();
        assertEquals(expected, json.getAsJsonObject("ingredient").get("tag").getAsString());
        assertEquals(ForgeweaveOccultismCompat.crusherTier(ore.tier()), json.get("min_tier").getAsInt(),
                "the raw row must gate on the same crusher rank as the ore row; a cheaper raw route"
                        + " would let a foliot reach an ore its own rank cannot crush");
        assertTrue(json.get("ignore_crushing_multiplier").getAsBoolean(),
                "a raw-to-ingot row with the multiplier on is a duplication loop against the smeltery");
        assertTrue(modLoadedGated(json), ore.id() + "'s raw row must be gated on occultism");
    }

    @ParameterizedTest
    @MethodSource("ores")
    void everyOreIsFindableByAMiningSpiritAtItsTiersWeight(TrackBOre ore) throws IOException {
        JsonObject json = generated("miner", ore.id());

        assertEquals("occultism:miner", json.get("type").getAsString());
        assertEquals("occultism:miners/ores", json.getAsJsonObject("ingredient").get("tag").getAsString(),
                "the ingredient of a miner row is the spirit item the mineshaft holds, not a block");

        JsonObject result = json.getAsJsonObject("result");
        assertEquals("occultism:weighted_tag", result.get("type").getAsString());
        assertEquals("c:ores/" + ore.id(), result.get("tag").getAsString());
        assertEquals(ForgeweaveOccultismCompat.minerWeight(ore.tier()), result.get("weight").getAsInt(),
                ore.id() + "'s weight must come off its own tier");
        assertTrue(modLoadedGated(json), ore.id() + "'s miner row must be gated on occultism");
    }

    /**
     * The property the whole Track B ladder depends on. Not a spot check of the table's numbers: it
     * walks every pair of adjacent rungs, so a later edit that swaps two of them fails here rather
     * than in a playtest.
     */
    @Test
    void minerWeightsAreStrictlyDecreasingInTier() {
        TrackBOre.Tier[] rungs = TrackBOre.Tier.values();
        for (int i = 1; i < rungs.length; i++) {
            int lower = ForgeweaveOccultismCompat.minerWeight(rungs[i - 1]);
            int higher = ForgeweaveOccultismCompat.minerWeight(rungs[i]);
            assertTrue(higher < lower, "a mining spirit must never return " + rungs[i] + " (weight " + higher
                    + ") at least as often as " + rungs[i - 1] + " (weight " + lower + ")");
            assertTrue(higher > 0, rungs[i] + " must stay reachable -- a weight of 0 is not a rare ore,"
                    + " it is an ore a mining spirit can never hand back");
        }
    }

    /** The same shape for the crusher table: a higher rung never asks for a weaker spirit. */
    @Test
    void crusherTiersAreNonDecreasingInTierAndStayInOccultismsRange() {
        TrackBOre.Tier[] rungs = TrackBOre.Tier.values();
        for (int i = 1; i < rungs.length; i++) {
            assertTrue(ForgeweaveOccultismCompat.crusherTier(rungs[i])
                            >= ForgeweaveOccultismCompat.crusherTier(rungs[i - 1]),
                    rungs[i] + " must not be crushable by a weaker spirit than " + rungs[i - 1]);
        }
        for (TrackBOre.Tier rung : rungs) {
            int tier = ForgeweaveOccultismCompat.crusherTier(rung);
            assertTrue(tier >= 1 && tier <= 4, rung + " maps to crusher tier " + tier
                    + ", outside Occultism's own foliot-to-marid range of 1 to 4");
        }
    }

    /** Every Track B ore's tier really is one the two tables answer for. */
    @ParameterizedTest
    @MethodSource("ores")
    void bothTablesAnswerForEveryOresOwnTier(TrackBOre ore) {
        assertTrue(ForgeweaveOccultismCompat.crusherTier(ore.tier()) >= 1);
        assertTrue(ForgeweaveOccultismCompat.minerWeight(ore.tier()) > 0);
    }

    private static boolean modLoadedGated(JsonObject json) {
        return json.has("neoforge:conditions")
                && json.getAsJsonArray("neoforge:conditions").asList().stream()
                        .map(element -> element.getAsJsonObject())
                        .anyMatch(condition -> "neoforge:mod_loaded".equals(condition.get("type").getAsString())
                                && ForgeweaveOccultismCompat.MODID.equals(condition.get("modid").getAsString()));
    }
}
