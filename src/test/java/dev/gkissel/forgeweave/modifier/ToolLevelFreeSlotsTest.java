package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolLevel;

/**
 * Issue #921 (M7-4, docs/SCOPE.md D-M7-1): {@link ForgeweaveModifiers#freeSlots}'s third additive
 * term, {@code ToolLevel.of(stack).bonusSlots()}, is read straight off the {@code tool_level}
 * component -- no registry or world access needed -- so the arithmetic is pinned here as a plain
 * unit test rather than only through a GameTest ({@code gametest.ToolLevelSlotGameTests}, which
 * covers the same term through the real Tool Station).
 */
class ToolLevelFreeSlotsTest {

    private static final ResourceLocation SEARING = ResourceLocation.fromNamespaceAndPath("forgeweave", "searing");
    private static final ResourceLocation REINFORCED_CORE =
            ResourceLocation.fromNamespaceAndPath("forgeweave", "reinforced_core");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack pickaxe() {
        return new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
    }

    @Test
    void anUnleveledToolHasOnlyTheDefaultSlots() {
        assertEquals(ForgeweaveModifiers.DEFAULT_SLOTS, ForgeweaveModifiers.freeSlots(pickaxe()));
    }

    @Test
    void theLevelGrantedTermAddsOnTopOfTheDefault() {
        ItemStack stack = pickaxe();
        stack.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), new ToolLevel(2, 0, 2));
        assertEquals(ForgeweaveModifiers.DEFAULT_SLOTS + 2, ForgeweaveModifiers.freeSlots(stack));
    }

    /** A level-earned slot nets out against a modifier occupying one, the same as any other bonus source. */
    @Test
    void aSpentLevelSlotIsNetOutByOccupiedSlots() {
        ItemStack stack = pickaxe();
        stack.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(SEARING, 1)));
        stack.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), new ToolLevel(1, 0, 1));
        assertEquals(ForgeweaveModifiers.DEFAULT_SLOTS, ForgeweaveModifiers.freeSlots(stack));
    }

    /** Trait-granted, modifier-granted and level-granted bonuses all sum on the same tool. */
    @Test
    void traitModifierAndLevelBonusesAllSumTogether() {
        ItemStack stack = pickaxe();
        stack.set(ForgeweaveDataComponents.TRAITS.get(), List.of(REINFORCED_CORE));
        stack.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(SEARING, 1)));
        stack.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), new ToolLevel(3, 0, 3));

        // DEFAULT_SLOTS + trait(1) + level(3) - searing's one occupied slot.
        assertEquals(ForgeweaveModifiers.DEFAULT_SLOTS + 1 + 3 - 1, ForgeweaveModifiers.freeSlots(stack));
    }

    /** D-M7-1: the flag has no read here -- freeSlots never consults {@code toolLeveling}, only addXp does. */
    @Test
    void theLevelComponentIsReadVerbatimRegardlessOfAnyConfigFlag() {
        ItemStack stack = pickaxe();
        stack.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), new ToolLevel(5, 999, 5));
        assertEquals(ForgeweaveModifiers.DEFAULT_SLOTS + 5, ForgeweaveModifiers.freeSlots(stack));
    }
}
