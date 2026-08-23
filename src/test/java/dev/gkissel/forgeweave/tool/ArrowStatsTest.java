package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

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
 * Pins {@link ToolConstants#ARROW} against upstream 1.12's {@code tools/ranged/item/Arrow.java}
 * (issue #653, parity audit T17 -- the material arrow):
 *
 * <ul>
 *   <li>{@code buildTagData} runs {@code data.head(head)}, then {@code data.fletchings(fletching)}
 *       (durability x fletching modifier), then {@code data.shafts(this, shaft)} (durability x
 *       shaft modifier + {@code bonusAmmo * durabilityPerAmmo}), each step floored at 1
 *       ({@code ProjectileNBT});</li>
 *   <li>{@code data.attack += 2f} is the entry's flat attack bonus;</li>
 *   <li>{@code damagePotential() = 1f}, {@code attackSpeed() = 1};</li>
 *   <li>upstream's default {@code getRepairParts() = {1}} -- the head slot -- at factor 1;</li>
 *   <li>the arrow's accuracy is the fletching's, clamped to {@code [0, 1]}
 *       ({@code ProjectileNBT#fletchings}), stored as {@link ProjectileStats}.</li>
 * </ul>
 */
class ArrowStatsTest {

    private static final float DELTA = 0.0001f;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Wood's shipped numbers: head 35/2.0/2.0, shaft modifier 1.0 bonus 0. */
    private static final Material WOOD = material(
            Optional.of(new Material.Head(35, 2.0f, 2.0f)),
            Optional.of(new Material.Shaft(1.0f, 0)),
            Optional.empty());

    /** Reed's shipped shaft block: modifier 1.5, bonus ammo 20 (upstream {@code ArrowShaftMaterialStats}). */
    private static final Material REED = material(
            Optional.empty(),
            Optional.of(new Material.Shaft(1.5f, 20)),
            Optional.empty());

    /** Feather: accuracy 1.0, modifier 1.0. */
    private static final Material FEATHER = material(
            Optional.empty(),
            Optional.empty(),
            Optional.of(new Material.Fletching(1.0f, 1.0f)));

    /** Leaf: accuracy 0.5, modifier 1.5. */
    private static final Material LEAF = material(
            Optional.empty(),
            Optional.empty(),
            Optional.of(new Material.Fletching(0.5f, 1.5f)));

    @Test
    void woodWoodFeatherMatchesUpstreamMath() {
        // head 35 -> fletching x1.0 -> shaft x1.0 + 0 = 35 durability; attack 2 + 2 = 4.
        ToolStats.Stats stats = ToolConstants.compute(ToolConstants.ARROW, List.of(WOOD, WOOD, FEATHER));
        assertEquals(35, stats.durability());
        assertEquals(4.0f, stats.attackDamage(), DELTA);
        assertEquals(2.0f, stats.miningSpeed(), DELTA);
    }

    @Test
    void reedShaftAndLeafFletchingScaleDurabilityInUpstreamOrder() {
        // ProjectileNBT order: head 35, fletchings: round(35 * 1.5) = 53 (Math.round, half up),
        // shafts: round(53 * 1.5) = 80, + round(20 * 10 / 1) = 200 -> 280.
        ToolStats.Stats stats = ToolConstants.compute(ToolConstants.ARROW, List.of(REED, WOOD, LEAF));
        assertEquals(280, stats.durability());
        assertEquals(4.0f, stats.attackDamage(), DELTA);
    }

    @Test
    void constantsMatchUpstream() {
        assertEquals(1.0f, ToolConstants.ARROW.attackSpeed(), DELTA);
        assertEquals(1.0f, ToolConstants.ARROW.damagePotential(), DELTA);
        assertEquals(2.0f, ToolConstants.ARROW.flatAttackBonus(), DELTA);
        assertEquals(ToolConstants.DEFAULT_DAMAGE_CUTOFF, ToolConstants.ARROW.damageCutoff(), DELTA);
        assertEquals(ToolConstants.Category.RANGED, ToolConstants.ARROW.category());
        // Upstream Arrow's PartMaterialType order: arrowShaft, arrowHead, fletching.
        assertEquals(List.of(ToolConstants.Role.SHAFT, ToolConstants.Role.ARROW_HEAD,
                ToolConstants.Role.FLETCHING),
                ToolConstants.ARROW.parts().stream().map(ToolConstants.PartSlot::role).toList());
        assertEquals(List.of("arrow_shaft", "arrow_head", "fletching"),
                ToolConstants.ARROW.parts().stream().map(ToolConstants.PartSlot::partId).toList());
    }

    /** Upstream default {@code TinkersItem#getRepairParts() = {1}} -- the arrow head -- at factor 1. */
    @Test
    void headSlotRepairsAtFactorOne() {
        List<ToolConstants.RepairPart> slots = ToolConstants.ARROW.repairSlots();
        assertEquals(1, slots.size());
        assertEquals(1, slots.get(0).slot());
        assertEquals(1.0f, slots.get(0).modifier(), DELTA);
    }

    /**
     * {@code arrow.tcon.json}: layer0 shaft, layer1 head, layer2 fletching, {@code broken0} the
     * shaft -- the one upstream tool that breaks its shaft rather than its head.
     */
    @Test
    void artIsShaftHeadFletchingBreakingTheShaft() {
        assertEquals(List.of("shaft", "head", "fletching"), ToolArt.layers(ToolConstants.ARROW.parts()));
        assertEquals(List.of(0, 1, 2), ToolArt.layerSlots(ToolConstants.ARROW.parts()));
        assertEquals("shaft", ToolArt.brokenLayer("arrow"));
    }

    /** {@code ProjectileNBT#fletchings}: accuracy is the average, clamped into {@code [0, 1]}. */
    @Test
    void accuracyIsTheFletchingsClamped() {
        assertEquals(1.0f, ProjectileStats.of(ToolConstants.ARROW, List.of(WOOD, WOOD, FEATHER))
                .orElseThrow().accuracy(), DELTA);
        assertEquals(0.5f, ProjectileStats.of(ToolConstants.ARROW, List.of(WOOD, WOOD, LEAF))
                .orElseThrow().accuracy(), DELTA);
        // A tool with no FLETCHING slot has no projectile stats at all.
        assertTrue(ProjectileStats.of(ToolConstants.SHURIKEN, List.of()).isEmpty());
    }

    private static Material material(Optional<Material.Head> head, Optional<Material.Shaft> shaft,
            Optional<Material.Fletching> fletching) {
        return new Material(
                head,
                Optional.empty(),
                Optional.empty(),
                TagKey.create(Registries.BLOCK,
                        ResourceLocation.withDefaultNamespace("incorrect_for_wooden_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(new Material.CraftingItem(Ingredient.of(Items.STICK), 144)),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF),
                Optional.empty(),
                Optional.empty(),
                false,
                Material.DEFAULT_ENCHANTABILITY,
                shaft,
                fletching);
    }
}
