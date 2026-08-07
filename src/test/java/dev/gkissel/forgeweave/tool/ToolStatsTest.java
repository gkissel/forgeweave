package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static Material material(int headDurability, float miningSpeed, float attackDamage,
            float handleDurabilityModifier, int handleDurability, int extraDurability) {
        return new Material(
                new Material.Head(headDurability, miningSpeed, attackDamage),
                new Material.Handle(handleDurabilityModifier, handleDurability),
                extraDurability,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_wooden_tool")),
                ResourceLocation.fromNamespaceAndPath("forgeweave", "test"),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));
    }
}
