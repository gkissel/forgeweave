package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.mojang.serialization.Codec;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;

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

    private static DamageXpLedger roundTrip(DamageXpLedger ledger) {
        Codec<DamageXpLedger> codec = DamageXpLedger.CODEC;
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, ledger).getOrThrow();
        return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }
}
