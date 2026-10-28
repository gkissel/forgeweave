package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Pins the melee damage ledger's serialization (issue #919). The ledger rides a data attachment on
 * the damaged entity, so this codec is the only thing standing between a mob that was hurt before a
 * chunk unloaded and the XP it still owes -- upstream's {@code DamageXpHandler#serializeNBT} /
 * {@code #deserializeNBT} in one round trip.
 */
class DamageXpLedgerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID TOOL_1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID TOOL_2 = UUID.fromString("00000000-0000-0000-0000-000000000022");

    @Test
    void aLedgerSurvivesAnNbtRoundTrip() {
        DamageXpLedger ledger = new DamageXpLedger();
        ledger.add(PLAYER_A, TOOL_1, 3.5F);
        ledger.add(PLAYER_A, TOOL_2, 1.0F);
        ledger.add(PLAYER_B, TOOL_1, 7.25F);

        assertEquals(ledger, roundTrip(ledger));
    }

    /** An empty ledger is what an entity nothing has hit carries, and it must survive too. */
    @Test
    void anEmptyLedgerRoundTripsAsEmpty() {
        assertTrue(roundTrip(new DamageXpLedger()).isEmpty());
    }

    /** Upstream's {@code addDamageFromTool} accumulates rather than overwrites, per player and tool. */
    @Test
    void damageAccumulatesPerPlayerAndTool() {
        DamageXpLedger ledger = new DamageXpLedger();
        ledger.add(PLAYER_A, TOOL_1, 2.0F);
        ledger.add(PLAYER_A, TOOL_1, 3.0F);
        ledger.add(PLAYER_A, TOOL_2, 1.0F);
        ledger.add(PLAYER_B, TOOL_1, 4.0F);

        assertFalse(ledger.isEmpty());
        assertEquals(5.0F, ledger.damage(PLAYER_A, TOOL_1));
        assertEquals(1.0F, ledger.damage(PLAYER_A, TOOL_2));
        assertEquals(4.0F, ledger.damage(PLAYER_B, TOOL_1));
        assertEquals(0.0F, ledger.damage(PLAYER_B, TOOL_2), "a tool that never hit owes nothing");
    }

    /**
     * A ledger a 1.21.1 server saved (captured from its region file) still loads on 26.1, through the same {@code ValueInput#read}
     * NeoForge's codec attachment serializer calls, and is written back as the same bytes. Losing it
     * would be quiet: NeoForge logs a failed attachment and skips it, and the mob just stops owing XP.
     */
    @Test
    void aLedgerSavedOn1_21_1LoadsAndIsWrittenBackUnchanged() throws Exception {
        CompoundTag saved = fixture("/fixtures/attachment/m919_damage_xp_ledger.snbt").getCompoundOrEmpty("nbt");
        RegistryAccess.Frozen registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        DamageXpLedger loaded = TagValueInput.create(ProblemReporter.DISCARDING, registries, saved)
                .read(DamageXpLedger.MAP_CODEC).orElseThrow();

        UUID dev = UUID.fromString("380df991-f603-344c-a090-369bad2a924a");
        UUID pickaxe = UUID.fromString("0bcd6e74-9820-4eb8-b656-c9fb9a231210");
        assertEquals(3.5F, loaded.damage(dev, pickaxe));
        assertEquals(1.0F, loaded.damage(dev, TOOL_2));
        assertEquals(7.25F, loaded.damage(PLAYER_B, pickaxe));

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.store(DamageXpLedger.MAP_CODEC, loaded);
        assertEquals(saved, output.buildResult(), "26.1 should write the ledger in the layout 1.21.1 wrote");
    }

    private static CompoundTag fixture(String path) throws IOException, CommandSyntaxException {
        try (InputStream in = DamageXpLedgerTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing fixture " + path);
            return TagParser.parseCompoundFully(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static DamageXpLedger roundTrip(DamageXpLedger ledger) {
        Codec<DamageXpLedger> codec = DamageXpLedger.CODEC;
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, ledger).getOrThrow();
        return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }
}
