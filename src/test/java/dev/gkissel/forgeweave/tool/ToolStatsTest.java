package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.material.Material;

/**
 * Pins {@link ToolStats#compute} against upstream 1.12's {@code ToolNBT#head}/{@code #extra}/
 * {@code #handle} formula (see {@link ToolStats}'s javadoc), using the shipped stone/wood material
 * stats -- the same numbers {@code ToolStationGameTests}'s pickaxe uses.
 */
class ToolStatsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void computesDurabilityMiningSpeedAndAttackFromTheThreeMaterials() {
        Material stone = material(120, 4.0f, 3.0f, 0.5f, -50, 20);
        Material wood = material(35, 2.0f, 2.0f, 1.0f, 25, 15);

        ToolStats.Stats stats = ToolStats.compute(stone, wood, wood);

        // (headDurability 120 + bindingExtraDurability 15) * handleDurabilityModifier 1.0
        //   + handleDurability 25 = 160
        assertEquals(160, stats.durability());
        assertEquals(4.0f, stats.miningSpeed());
        assertEquals(3.0f, stats.attackDamage());
    }

    @Test
    void durabilityNeverDropsBelowOne() {
        Material fragileHead = material(1, 1.0f, 1.0f, 0.1f, 0, -50);
        Material noExtra = material(1, 1.0f, 1.0f, 0.1f, 0, 0);

        ToolStats.Stats stats = ToolStats.compute(fragileHead, noExtra, fragileHead);

        assertTrue(stats.durability() >= 1, "durability must never drop to zero or below");
    }

    /**
     * Upstream's stone carries {@code cheapskate} on the head part
     * ({@code TinkerMaterials}: {@code stone.addTrait(cheapskate, HEAD)}), whose
     * {@code onToolBuilding} does {@code max(1, durability * 80 / 100)} on the assembled tool; the
     * shipped {@code forgeweave:cheap} trait carries that penalty (issue #79). Only the head material
     * triggers it: the same trait on the binding or handle changes nothing.
     */
    @Test
    void aCheapHeadTakesTwentyPercentOffTheAssembledDurability() {
        Material stone = cheapMaterial(120, 0.5f, -50, 20);
        Material wood = material(35, 2.0f, 2.0f, 1.0f, 25, 15);
        Material cheapWood = material(35, 2.0f, 2.0f, 1.0f, 25, 15, "cheap");

        // Upstream's all-stone pickaxe: (120 + 20) * 0.5 - 50 = 20, * 80 / 100 = 16.
        assertEquals(16, ToolStats.compute(stone, stone, stone).durability());
        // Stone head, wood binding and handle: (120 + 15) * 1.0 + 25 = 160, * 80 / 100 = 128.
        assertEquals(128, ToolStats.compute(stone, wood, wood).durability());
        // Cheap off the head is inert: same 75 an all-wood tool gets, penalty or no penalty.
        assertEquals(75, ToolStats.compute(wood, wood, wood).durability());
        assertEquals(75, ToolStats.compute(wood, cheapWood, cheapWood).durability());
    }

    private static Material material(int headDurability, float miningSpeed, float attackDamage,
            float handleDurabilityModifier, int handleDurability, int extraDurability) {
        return material(headDurability, miningSpeed, attackDamage, handleDurabilityModifier, handleDurability,
                extraDurability, "test");
    }

    /** The shipped stone material's durability-relevant stats, trait and all. */
    private static Material cheapMaterial(int headDurability, float handleDurabilityModifier, int handleDurability,
            int extraDurability) {
        return material(headDurability, 4.0f, 3.0f, handleDurabilityModifier, handleDurability, extraDurability,
                "cheap");
    }

    private static Material material(int headDurability, float miningSpeed, float attackDamage,
            float handleDurabilityModifier, int handleDurability, int extraDurability, String trait) {
        return new Material(
                new Material.Head(headDurability, miningSpeed, attackDamage),
                new Material.Handle(handleDurabilityModifier, handleDurability),
                extraDurability,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                ResourceLocation.fromNamespaceAndPath("forgeweave", trait),
                List.of(new Material.CraftingItem(Ingredient.of(Items.STICK), 1)),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));
    }
}
