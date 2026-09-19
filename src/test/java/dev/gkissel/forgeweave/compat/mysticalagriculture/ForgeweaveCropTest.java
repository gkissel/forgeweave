package dev.gkissel.forgeweave.compat.mysticalagriculture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * The crop roster (issue #999): the eleven Track B ores plus brimspar, each with a crux that is a
 * block Forgeweave actually registers, and each at the tier the mining ladder gives it. Needs a
 * bootstrapped Minecraft only so the block registry is populated for the crux check.
 */
class ForgeweaveCropTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Eleven ores plus brimspar, and no hand-written duplicate of an ore row. */
    @Test
    void theRosterIsTheOreLadderPlusBrimspar() {
        assertEquals(TrackBOre.ALL.size() + 1, ForgeweaveCrop.ALL.size());
        Set<String> ids = new HashSet<>();
        for (ForgeweaveCrop crop : ForgeweaveCrop.ALL) {
            assertTrue(ids.add(crop.id()), crop.id() + " appears twice in the roster");
        }
        assertTrue(ids.contains("brimspar"), "brimspar is the one fuel material with an item form");
        for (TrackBOre ore : TrackBOre.ALL) {
            assertTrue(ids.contains(ore.id()), ore.id() + " has no crop");
        }
    }

    /**
     * #999's "every registered crop has a crux that is a registered storage block". Every Track B
     * ore's crux is its {@code <id>_block}; brimspar has no storage block -- it is a fuel with no
     * ingot -- so its crux is its ore block, the one deviation {@link ForgeweaveCrop}'s javadoc
     * records. Either way the crux has to be a block that exists.
     */
    @Test
    void everyCruxIsARegisteredForgeweaveBlock() {
        for (ForgeweaveCrop crop : ForgeweaveCrop.ALL) {
            ResourceLocation id = crop.crux().getId();
            assertNotNull(crop.crux(), crop.id() + " has no crux");
            assertTrue(BuiltInRegistries.BLOCK.containsKey(id),
                    crop.id() + "'s crux " + id + " is not a registered block");
            String expected = crop.id().equals("brimspar") ? "brimspar_ore" : crop.id() + "_block";
            assertEquals(expected, id.getPath(), crop.id() + "'s crux");
        }
    }

    /** No crop claims inferium, and each ore's tier is the one its mining rung maps to. */
    @Test
    void tiersComeOffTheMiningLadder() {
        for (TrackBOre ore : TrackBOre.ALL) {
            ForgeweaveCrop crop = byId(ore.id());
            assertEquals(EssenceTier.forOre(ore.tier()), crop.tier(), ore.id() + "'s essence tier");
        }
        // Brimspar ore rides cobalt/ardite's netherite gate (#903), so imperium.
        assertEquals(EssenceTier.IMPERIUM, byId("brimspar").tier());
    }

    /** Essence and seed ids are derived, never hand-written, so a rename cannot desync them. */
    @Test
    void essenceAndSeedIdsAreDerivedFromTheCropId() {
        for (ForgeweaveCrop crop : ForgeweaveCrop.ALL) {
            assertEquals(crop.id() + "_essence", crop.essenceItemId());
            assertEquals(crop.id() + "_seeds", crop.seedsItemId());
            assertEquals("forgeweave", crop.cropId().getNamespace());
            assertEquals(crop.id(), crop.cropId().getPath());
        }
    }

    /** A crop's tint is the material's own colour, so nothing here ships a second palette. */
    @Test
    void coloursComeFromTheMaterialRoster() {
        for (TrackBOre ore : TrackBOre.ALL) {
            assertEquals(ore.color(), byId(ore.id()).color(), ore.id() + "'s crop colour");
        }
    }

    private static ForgeweaveCrop byId(String id) {
        List<ForgeweaveCrop> matches = ForgeweaveCrop.ALL.stream().filter(crop -> crop.id().equals(id)).toList();
        assertEquals(1, matches.size(), id + " should appear once");
        return matches.getFirst();
    }
}
