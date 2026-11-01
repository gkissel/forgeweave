package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import dev.gkissel.forgeweave.client.book.MiningLadder;

/**
 * Keeps the book's mining ladder diagram ({@code book/ladder/mining.json}) true to the data it
 * describes: the rungs climb the real tier ladder in order, every head listed under a rung is a
 * material on that rung, and every ore is a registered item.
 */
class BookLadderTest {

    private static final List<String> TIERS =
            List.of("wooden", "stone", "iron", "diamond", "netherite", "hardcinder", "warspar", "resonite");

    private static final MiningLadder LADDER = MiningLadder.load("ladder/mining.json");

    @Test
    void theRungsClimbTheWholeLadderInOrder() {
        assertEquals(TIERS, LADDER.rungs().stream().map(MiningLadder.Rung::tier).toList());
    }

    @Test
    void everyHeadSitsOnTheRungItIsListedUnder() throws IOException {
        for (MiningLadder.Rung rung : LADDER.rungs()) {
            assertFalse(rung.heads().isEmpty(), rung.tier() + " lists no head");
            for (var head : rung.heads()) {
                Path file = projectRoot().resolve(Path.of("src/main/resources/data", head.getNamespace(),
                        "forgeweave/material", head.getPath() + ".json"));
                String tag = JsonParser.parseString(Files.readString(file)).getAsJsonObject()
                        .get("incorrect_for_tool").getAsString();
                assertEquals("incorrect_for_" + rung.tier() + "_tool", tag.substring(tag.indexOf(':') + 1),
                        head + " is listed under " + rung.tier() + " but its material says " + tag);
            }
        }
    }

    @Test
    void everyOreIsARegisteredItem() {
        for (MiningLadder.Rung rung : LADDER.rungs()) {
            for (MiningLadder.Ore ore : rung.ores()) {
                assertNotEquals(Items.AIR, BuiltInRegistries.ITEM.getValue(ore.item()), ore.item() + " is not an item");
            }
        }
    }

    /** Same walk-up as {@code ApiSourceIsolationTest}: Gradle runs tests from the run directory. */
    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }
}
