package dev.gkissel.forgeweave.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.menu.PartBuilderRecipes.CostResult;

/**
 * Pins issue #45's value math: how many whole material items it takes to cover a part's cost, and
 * how much change is left over. Pure integer math, no registry or game bootstrap needed -- {@link
 * PartBuilderRecipes#resolve} is the registry-dependent wrapper around this and is covered by {@code
 * PartBuilderGameTests} instead.
 *
 * <p>Parity audit T58 (issue #489): the unit is upstream 1.12's own {@code Material.VALUE_*} scale
 * ({@code VALUE_Ingot = 144}), not the coarser "shard-unit" it used to be, so nuggets (16),
 * fragments/bonemeal/paper (36) and shards (72) are all whole numbers.
 */
class PartBuilderRecipesTest {

    @Test
    void valueConstantsAreUpstreamsMaterialValueTable() {
        // Material.java:45-51 of the 1.12 clone.
        assertEquals(144, PartBuilderRecipes.INGOT_VALUE, "VALUE_Ingot");
        assertEquals(144 / 9, PartBuilderRecipes.NUGGET_VALUE, "VALUE_Nugget = VALUE_Ingot / 9");
        assertEquals(144 / 4, PartBuilderRecipes.FRAGMENT_VALUE, "VALUE_Fragment = VALUE_Ingot / 4");
        assertEquals(144 / 2, PartBuilderRecipes.SHARD_VALUE, "VALUE_Shard = VALUE_Ingot / 2");
    }

    @Test
    void partCostsAreWholeIngots() {
        // TinkerTools#registerToolParts: heads VALUE_Ingot * 2, rods/bindings * 1, tough * 3, large * 8.
        assertEquals(2 * 144, PartBuilderRecipes.HEAD_COST);
        assertEquals(144, PartBuilderRecipes.SMALL_PART_COST);
        assertEquals(3 * 144, PartBuilderRecipes.MEDIUM_PART_COST);
        assertEquals(8 * 144, PartBuilderRecipes.LARGE_HEAD_COST);
    }

    @Test
    void exactValueMatchLeavesNoChange() {
        CostResult result = PartBuilderRecipes.computeCost(PartBuilderRecipes.HEAD_COST, PartBuilderRecipes.INGOT_VALUE);

        assertEquals(2, result.itemsNeeded());
        assertEquals(0, result.changeUnits());
    }

    @Test
    void oversizedItemLeavesChange() {
        // A log (4 ingots) covers a head (2 ingots) with 2 ingots = 4 shards left over.
        CostResult result = PartBuilderRecipes.computeCost(PartBuilderRecipes.HEAD_COST, 4 * PartBuilderRecipes.INGOT_VALUE);

        assertEquals(1, result.itemsNeeded());
        assertEquals(4 * PartBuilderRecipes.SHARD_VALUE, result.changeUnits());
    }

    @Test
    void shardsExactlyCoverASmallPartCost() {
        CostResult result = PartBuilderRecipes.computeCost(PartBuilderRecipes.SMALL_PART_COST, PartBuilderRecipes.SHARD_VALUE);

        assertEquals(2, result.itemsNeeded());
        assertEquals(0, result.changeUnits());
    }

    @Test
    void nineNuggetsAreAnIngotAndEighteenAHead() {
        assertEquals(new CostResult(9, 0),
                PartBuilderRecipes.computeCost(PartBuilderRecipes.SMALL_PART_COST, PartBuilderRecipes.NUGGET_VALUE));
        assertEquals(new CostResult(18, 0),
                PartBuilderRecipes.computeCost(PartBuilderRecipes.HEAD_COST, PartBuilderRecipes.NUGGET_VALUE));
    }

    @Test
    void fourFragmentsAreAnIngot() {
        // bonemeal, paper and prismarine shards are all VALUE_Fragment upstream (TinkerMaterials.java:243,269,276).
        assertEquals(new CostResult(4, 0),
                PartBuilderRecipes.computeCost(PartBuilderRecipes.SMALL_PART_COST, PartBuilderRecipes.FRAGMENT_VALUE));
        assertEquals(new CostResult(8, 0),
                PartBuilderRecipes.computeCost(PartBuilderRecipes.HEAD_COST, PartBuilderRecipes.FRAGMENT_VALUE));
    }

    @Test
    void roundsUpToTheNextWholeItemWhenValueDoesNotDivideEvenly() {
        // A prismarine brick block is VALUE_Fragment * 9 = 324: one overpays a 288 head by 36,
        // which is less than a shard and so (upstream ToolBuilder#tryBuildToolPart, integer
        // division by VALUE_Shard) comes back as no change at all.
        CostResult result = PartBuilderRecipes.computeCost(PartBuilderRecipes.HEAD_COST, 9 * PartBuilderRecipes.FRAGMENT_VALUE);

        assertEquals(1, result.itemsNeeded());
        assertEquals(36, result.changeUnits());
        assertEquals(0, PartBuilderRecipes.shardChange(result.changeUnits()));
        assertEquals(4, PartBuilderRecipes.shardChange(4 * PartBuilderRecipes.SHARD_VALUE));
    }
}
