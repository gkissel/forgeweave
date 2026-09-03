package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Pins M7-1's leveling foundation (issue #918) against Tinkers' Tool Leveling's
 * {@code ModToolLeveling#getXpForLevelup} and {@code Config#canLevelUp} (tool-leveling-1.12, pinned
 * commit in NOTICE.md).
 *
 * <p>The curve's shape at the bottom is the reason this class exists: it looks like a transcription
 * slip and is not one, so a later cleanup that "fixes" it fails here rather than in a playtest.
 */
class ToolLevelingTest {

    /** No server stands up in a unit test, so this is what the config helpers answer with. */
    private static final int BASE = ForgeweaveConfig.DEFAULT_BASE_XP_DEFAULT;
    private static final double MULTIPLIER = ForgeweaveConfig.LEVEL_MULTIPLIER_FLOOR;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Upstream: {@code getXpForLevelup(level) = level <= 1 ? base : previous * multiplier}. Levels
     * 0 to 1 and 1 to 2 both cost the base amount; only the third level starts multiplying.
     */
    @Test
    void firstTwoLevelsBothCostTheBaseAmount() {
        assertEquals(BASE, ToolLeveling.xpForLevelup(0, BASE, MULTIPLIER));
        assertEquals(BASE, ToolLeveling.xpForLevelup(1, BASE, MULTIPLIER));
        assertEquals(BASE * 2, ToolLeveling.xpForLevelup(2, BASE, MULTIPLIER));
        assertEquals(BASE * 4, ToolLeveling.xpForLevelup(3, BASE, MULTIPLIER));
        assertEquals(BASE * 8, ToolLeveling.xpForLevelup(4, BASE, MULTIPLIER));
    }

    /** A larger multiplier still leaves the first two levels alone, and truncates each step. */
    @Test
    void theMultiplierAppliesFromTheThirdLevelAndTruncates() {
        assertEquals(100, ToolLeveling.xpForLevelup(1, 100, 2.5D));
        assertEquals(250, ToolLeveling.xpForLevelup(2, 100, 2.5D));
        assertEquals(625, ToolLeveling.xpForLevelup(3, 100, 2.5D));
        // 625 * 2.5 = 1562.5, truncated the way upstream's per-call int cast truncates.
        assertEquals(1562, ToolLeveling.xpForLevelup(4, 100, 2.5D));
    }

    /**
     * The cost doubles per level forever at the default multiplier, so an uncapped tool eventually
     * runs a plain {@code int} negative. Clamping keeps a very high level expensive instead of free.
     */
    @Test
    void theCostClampsRatherThanOverflowing() {
        assertEquals(Integer.MAX_VALUE, ToolLeveling.xpForLevelup(60, BASE, MULTIPLIER));
    }

    /**
     * D-M7-9: upstream's {@code maximumLevels >= currentLevel} lets a cap of N reach N + 1.
     * Forgeweave reads {@code level < cap}, so a cap of 3 stops at exactly 3.
     */
    @Test
    void theCapStopsAtExactlyTheConfiguredLevel() {
        assertTrue(ToolLeveling.canLevelUp(2, 3));
        assertFalse(ToolLeveling.canLevelUp(3, 3));
        assertFalse(ToolLeveling.canLevelUp(4, 3));

        // Zero or negative is upstream's "no cap".
        assertTrue(ToolLeveling.canLevelUp(999, -1));
        assertTrue(ToolLeveling.canLevelUp(999, 0));
    }

    @Test
    void aCappedToolStopsLevellingAndBanksNothingFurther() {
        ToolLevel capped = ToolLevel.NONE.plusXp(BASE * 100, BASE, MULTIPLIER, 3);
        assertEquals(3, capped.level());
        assertEquals(3, capped.bonusSlots());

        // Past the cap the grant is discarded rather than banked -- upstream's early return.
        assertEquals(capped, capped.plusXp(BASE * 100, BASE, MULTIPLIER, 3));
    }

    /** One grant worth several levels rolls them all, one modifier slot per level. */
    @Test
    void levelsRollWhileTheThresholdIsStillMet() {
        // 500 + 500 + 1000 = the first three levels, with 1 left over toward the fourth.
        ToolLevel level = ToolLevel.NONE.plusXp(2001, BASE, MULTIPLIER, -1);
        assertEquals(3, level.level());
        assertEquals(1, level.xp());
        assertEquals(3, level.bonusSlots());
    }

    @Test
    void xpShortOfTheThresholdJustBanks() {
        ToolLevel level = ToolLevel.NONE.plusXp(BASE - 1, BASE, MULTIPLIER, -1);
        assertEquals(0, level.level());
        assertEquals(BASE - 1, level.xp());
        assertEquals(0, level.bonusSlots());
    }

    /** D-M7-5: the five area-of-effect shapes cost nine times the default, everything else once. */
    @Test
    void areaOfEffectToolsCostNineTimesTheBase() {
        assertEquals(9 * BASE, ToolLeveling.baseXp(new ItemStack(ForgeweaveItems.TOOL_HAMMER.get())));
        assertEquals(9 * BASE, ToolLeveling.baseXp(new ItemStack(ForgeweaveItems.TOOL_EXCAVATOR.get())));
        assertEquals(9 * BASE, ToolLeveling.baseXp(new ItemStack(ForgeweaveItems.TOOL_LUMBERAXE.get())));
        assertEquals(9 * BASE, ToolLeveling.baseXp(new ItemStack(ForgeweaveItems.TOOL_SCYTHE.get())));
        assertEquals(9 * BASE, ToolLeveling.baseXp(new ItemStack(ForgeweaveItems.TOOL_VEIN_HAMMER.get())));

        assertEquals(BASE, ToolLeveling.baseXp(new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get())));
        assertEquals(BASE, ToolLeveling.baseXp(new ItemStack(ForgeweaveItems.TOOL_BROADSWORD.get())));
        // An item no assembly row builds falls back to the plain default too.
        assertEquals(BASE, ToolLeveling.baseXp(new ItemStack(Items.STICK)));
    }

    /** An absent component reads as level 0, with no XP and no earned slots. */
    @Test
    void anAbsentComponentReadsAsUnleveled() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        assertNull(stack.get(ForgeweaveDataComponents.TOOL_LEVEL.get()));
        assertEquals(ToolLevel.NONE, ToolLevel.of(stack));
        assertEquals(0, ToolLevel.of(stack).level());
    }

    @Test
    void theComponentRoundTripsThroughItsCodecs() {
        ToolLevel level = new ToolLevel(7, 1234, 7);

        ToolLevel viaCodec = ToolLevel.CODEC
                .parse(JsonOps.INSTANCE, ToolLevel.CODEC.encodeStart(JsonOps.INSTANCE, level).getOrThrow())
                .getOrThrow();
        assertEquals(level, viaCodec);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ToolLevel.STREAM_CODEC.encode(buf, level);
        assertEquals(level, ToolLevel.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    /** The persisted field names are part of the save-compat promise. */
    @Test
    void theCodecUsesTheDocumentedFieldNames() {
        String json = ToolLevel.CODEC.encodeStart(JsonOps.INSTANCE, new ToolLevel(2, 30, 2)).getOrThrow().toString();
        assertEquals("{\"level\":2,\"xp\":30,\"bonus_slots\":2}", json);
    }

    /** D-M7-3: with the flag off nothing accrues, and the stack is left untouched. */
    @Test
    void theFlagOffMakesAddXpInert() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());

        assertFalse(ToolLeveling.addXp(stack, 10_000, null, false));
        assertNull(stack.get(ForgeweaveDataComponents.TOOL_LEVEL.get()));

        // A tool that already earned levels keeps them: the flag never revokes a slot.
        ItemStack leveled = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        leveled.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), new ToolLevel(2, 40, 2));
        assertFalse(ToolLeveling.addXp(leveled, 10_000, null, false));
        assertEquals(new ToolLevel(2, 40, 2), ToolLevel.of(leveled));
    }

    @Test
    void addXpReportsWhetherALevelWasCrossed() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());

        assertFalse(ToolLeveling.addXp(stack, BASE - 1, null, true));
        assertEquals(new ToolLevel(0, BASE - 1, 0), ToolLevel.of(stack));

        assertTrue(ToolLeveling.addXp(stack, 1, null, true));
        assertEquals(new ToolLevel(1, 0, 1), ToolLevel.of(stack));
    }

    /** A grant of zero changes nothing, so no needless component write lands on the stack. */
    @Test
    void aZeroGrantWritesNothing() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        assertFalse(ToolLeveling.addXp(stack, 0, null, true));
        assertNull(stack.get(ForgeweaveDataComponents.TOOL_LEVEL.get()));
    }
}
