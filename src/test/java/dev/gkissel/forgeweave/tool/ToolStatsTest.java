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

    // ------------------------------------------------------------------- #593: enchantability

    /**
     * Issue #593: the assembled tool's enchantability is the plain rounded mean of every part's
     * material value, over every slot rather than the heads alone (see
     * {@link ToolStats#averageEnchantability}).
     */
    @Test
    void enchantabilityIsTheRoundedMeanOfEveryPartsMaterial() {
        Material paper = enchantable(22);
        Material stone = enchantable(5);

        assertEquals(22, ToolStats.averageEnchantability(List.of(paper, paper, paper)));
        assertEquals(5, ToolStats.averageEnchantability(List.of(stone, stone, stone)));
        // (22 + 5 + 5) / 3 = 10.67, rounded to 11 -- a better head does lift a stone-handled tool.
        assertEquals(11, ToolStats.averageEnchantability(List.of(paper, stone, stone)));
        // ... and the same three materials in any other slot order answer identically.
        assertEquals(11, ToolStats.averageEnchantability(List.of(stone, stone, paper)));
    }

    /**
     * A material that names no {@code enchantability} is worth {@link Material#DEFAULT_ENCHANTABILITY},
     * which is what keeps a pack written before #593 enchanting exactly as it did (the flat 14).
     */
    @Test
    void aMaterialWithNoEnchantabilityIsWorthTheDefault() {
        Material plain = material(120, 4.0f, 3.0f, 0.5f, -50, 20);

        assertEquals(Material.DEFAULT_ENCHANTABILITY, plain.enchantability());
        assertEquals(Material.DEFAULT_ENCHANTABILITY, ToolStats.averageEnchantability(List.of(plain, plain)));
    }

    /** Never zero: a zero enchantment value is how {@code ToolItem} says "the flag is off". */
    @Test
    void enchantabilityNeverRoundsDownToZero() {
        assertTrue(ToolStats.averageEnchantability(List.of(enchantable(1), enchantable(1))) >= 1,
                "an enchantability of 0 would read as allowVanillaEnchanting being off");
        assertEquals(Material.DEFAULT_ENCHANTABILITY, ToolStats.averageEnchantability(List.of()));
    }

    /** The same material as {@link #material}, differing only in the #593 field. */
    private static Material enchantable(int enchantability) {
        Material base = material(120, 4.0f, 3.0f, 0.5f, -50, 20);
        return new Material(base.head(), base.handle(), base.extraDurability(), base.incorrectForTool(),
                base.traits(), base.craftingItems(), base.repairItem(), base.color(), base.bow(),
                base.bowstring(), base.castOnly(), enchantability);
    }

    /**
     * Upstream's stone carries {@code cheapskate} on the head part only
     * ({@code TinkerMaterials}: {@code stone.addTrait(cheapskate, HEAD)}), whose
     * {@code onToolBuilding} does {@code max(1, durability * 80 / 100)} on the assembled tool; the
     * shipped {@code forgeweave:cheapskate} trait carries that penalty (issue #493 split it off the
     * general {@code forgeweave:cheap} repair-bonus trait, issue #79's original single-id carrier).
     * Only the head material triggers it: the same trait on the binding or handle changes nothing.
     */
    @Test
    void aCheapskateHeadTakesTwentyPercentOffTheAssembledDurability() {
        Material stone = cheapskateHeadMaterial(120, 0.5f, -50, 20);
        Material wood = material(35, 2.0f, 2.0f, 1.0f, 25, 15);
        Material cheapWood = material(35, 2.0f, 2.0f, 1.0f, 25, 15, "cheap");

        // Upstream's all-stone pickaxe: (120 + 20) * 0.5 - 50 = 20, * 80 / 100 = 16.
        assertEquals(16, ToolStats.compute(stone, stone, stone).durability());
        // Stone head, wood binding and handle: (120 + 15) * 1.0 + 25 = 160, * 80 / 100 = 128.
        assertEquals(128, ToolStats.compute(stone, wood, wood).durability());
        // Cheapskate off the head is inert: same 75 an all-wood tool gets, penalty or no penalty.
        assertEquals(75, ToolStats.compute(wood, wood, wood).durability());
        assertEquals(75, ToolStats.compute(wood, cheapWood, cheapWood).durability());
    }

    private static Material material(int headDurability, float miningSpeed, float attackDamage,
            float handleDurabilityModifier, int handleDurability, int extraDurability) {
        return material(headDurability, miningSpeed, attackDamage, handleDurabilityModifier, handleDurability,
                extraDurability, "test");
    }

    /**
     * The shipped stone material's durability-relevant stats, general {@code cheap} and head-scoped
     * {@code cheapskate} both -- {@link ToolStats#compute} only ever reads the head-scoped list, so
     * only {@code cheapskate} is exercised here, exactly as stone's own material JSON is shaped.
     */
    private static Material cheapskateHeadMaterial(int headDurability, float handleDurabilityModifier,
            int handleDurability, int extraDurability) {
        return new Material(
                new Material.Head(headDurability, 4.0f, 3.0f),
                new Material.Handle(handleDurabilityModifier, handleDurability),
                extraDurability,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(
                        List.of(ResourceLocation.fromNamespaceAndPath("forgeweave", "cheap")),
                        List.of(ResourceLocation.fromNamespaceAndPath("forgeweave", "cheapskate"))),
                List.of(new Material.CraftingItem(Ingredient.of(Items.STICK), 1)),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));
    }

    private static Material material(int headDurability, float miningSpeed, float attackDamage,
            float handleDurabilityModifier, int handleDurability, int extraDurability, String trait) {
        return new Material(
                new Material.Head(headDurability, miningSpeed, attackDamage),
                new Material.Handle(handleDurabilityModifier, handleDurability),
                extraDurability,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                Material.Traits.general(ResourceLocation.fromNamespaceAndPath("forgeweave", trait)),
                List.of(new Material.CraftingItem(Ingredient.of(Items.STICK), 1)),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));
    }
}
