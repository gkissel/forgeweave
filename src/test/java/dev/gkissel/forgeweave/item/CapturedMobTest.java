package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * A mob caged on 1.21.1 is released in today's entity format ({@link CapturedMob#upgradedData}):
 * the 1.21.1 world-upgrade check (2026-10-28) found the caged tag skips vanilla's data fixer.
 */
class CapturedMobTest {

    private static final Identifier ZOMBIE = Identifier.parse("minecraft:zombie");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aZombieCagedOn1_21_1KeepsItsSwordAndName() throws Exception {
        CompoundTag caged = TagParser.parseCompoundFully("""
                {Health: 2.5f, HandItems: [{count: 1, id: "minecraft:iron_sword"}, {}], ArmorItems: [{}, {}, {}, {}],
                 HandDropChances: [0.085f, 0.085f], ArmorDropChances: [0.085f, 0.085f, 0.085f, 0.085f],
                 CustomName: '{"text":"Bob"}'}""");

        CompoundTag released = new CapturedMob(ZOMBIE, caged).upgradedData();

        assertEquals("minecraft:iron_sword", released.getCompoundOrEmpty("equipment").getCompoundOrEmpty("mainhand").getStringOr("id", ""));
        assertEquals(TagParser.parseCompoundFully("{text: \"Bob\"}"), released.get("CustomName"), "a text component, not its JSON");
        assertEquals(2.5f, released.getFloatOr("Health", 0));
        assertFalse(released.contains("id"), "the id the fixer needed is not left in the tag");
    }

    @Test
    void aMobCagedOnThisVersionIsReleasedAsSaved() {
        CompoundTag caged = new CompoundTag();
        caged.putInt(SharedConstants.DATA_VERSION_TAG, SharedConstants.getCurrentVersion().dataVersion().version());
        caged.putString("CustomName", "{not json");

        CapturedMob mob = new CapturedMob(ZOMBIE, caged);

        assertSame(mob.data(), mob.upgradedData());
    }
}
