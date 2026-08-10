package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * ADR-0004's hard rule, pinned: a tool's modifiers serialize as nothing but {@code id + level}, and
 * that shape survives a save/load and a server-to-client sync -- including for an id this version of
 * Forgeweave doesn't implement, which is what keeps an old world (or a world whose datapack moved on)
 * from silently losing upgrades the player paid for.
 */
class ModifierEntryTest {

    private static final Codec<List<ModifierEntry>> CODEC = ModifierEntry.CODEC.listOf();
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<ModifierEntry>> STREAM_CODEC =
            ModifierEntry.STREAM_CODEC.apply(ByteBufCodecs.list());

    private static final ResourceLocation HASTE = ResourceLocation.fromNamespaceAndPath("forgeweave", "haste");
    private static final ResourceLocation FROM_THE_FUTURE =
            ResourceLocation.fromNamespaceAndPath("forgeweave", "not_implemented_here");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void codecRoundTripsAnOrderedModifierList() {
        List<ModifierEntry> entries = List.of(
                new ModifierEntry(HASTE, 51),
                new ModifierEntry(FROM_THE_FUTURE, 1));

        JsonElement encoded = CODEC.encodeStart(JsonOps.INSTANCE, entries).getOrThrow();
        List<ModifierEntry> decoded = CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(entries, decoded, "the component must round-trip, order included");
    }

    @Test
    void streamCodecRoundTripsAnOrderedModifierList() {
        List<ModifierEntry> entries = List.of(new ModifierEntry(HASTE, 51), new ModifierEntry(FROM_THE_FUTURE, 2));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        STREAM_CODEC.encode(buffer, entries);

        assertEquals(entries, STREAM_CODEC.decode(buffer));
    }

    /** The serialized shape is exactly two fields -- no class reference, ever (ADR-0004 decision 2). */
    @Test
    void entriesSerializeAsNothingButIdAndLevel() {
        JsonElement encoded = ModifierEntry.CODEC
                .encodeStart(JsonOps.INSTANCE, new ModifierEntry(HASTE, 51)).getOrThrow();

        assertEquals(JsonParser.parseString("{\"id\":\"forgeweave:haste\",\"level\":51}"), encoded);
    }

    @Test
    void rejectsANonPositiveLevel() {
        DataResult<ModifierEntry> result = ModifierEntry.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\":\"forgeweave:haste\",\"level\":0}"));

        assertTrue(result.isError(), "a modifier is either present at level 1 or absent");
    }

    /**
     * Unknown-id policy (issue #105): <b>log and keep</b>, not log and drop. The entry decodes, stays
     * on the tool, and keeps occupying its slot; it simply contributes nothing until some version
     * implements the id.
     */
    @Test
    void anUnimplementedIdIsKeptAndStillOccupiesItsSlot() {
        ItemStack tool = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        List<ModifierEntry> entries = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "[{\"id\":\"forgeweave:not_implemented_here\",\"level\":3}]")).getOrThrow();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), entries);

        assertEquals(entries, ForgeweaveModifiers.of(tool), "an unknown id must survive on the tool");
        assertEquals(ForgeweaveModifiers.DEFAULT_SLOTS - 1, ForgeweaveModifiers.freeSlots(tool),
                "an unknown modifier still holds the slot the player spent on it");
    }

    @Test
    void anUnmodifiedToolHasThreeSlots() {
        ItemStack tool = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());

        assertEquals(3, ForgeweaveModifiers.DEFAULT_SLOTS, "upstream ToolCore#DEFAULT_MODIFIERS");
        assertEquals(3, ForgeweaveModifiers.freeSlots(tool));
        assertTrue(ForgeweaveModifiers.of(tool).isEmpty());
    }

    /** Upstream's {@code MultiAspect} steps a level every {@code countPerLevel} applications. */
    @Test
    void displayLevelStepsEveryFiftyRedstone() {
        assertEquals(1, ForgeweaveModifiers.displayLevel(HASTE, 1));
        assertEquals(1, ForgeweaveModifiers.displayLevel(HASTE, 50));
        assertEquals(2, ForgeweaveModifiers.displayLevel(HASTE, 51));
        assertEquals(5, ForgeweaveModifiers.displayLevel(HASTE, 250));
        assertEquals(100, ForgeweaveModifiers.unitsForDisplayLevel(HASTE, 51),
                "the readout's denominator is where the current level ends");
        // A modifier this version doesn't implement has no unit scale, so its level is its level.
        assertEquals(3, ForgeweaveModifiers.displayLevel(FROM_THE_FUTURE, 3));
    }
}
