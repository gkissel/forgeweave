package dev.gkissel.forgeweave.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.menu.PartBuilderRecipes.CostResult;

/**
 * Pins issue #45's value math: how many whole material items it takes to cover a part's
 * shard-unit cost, and how much shard-unit change is left over. Pure integer math, no registry or
 * game bootstrap needed -- {@link PartBuilderRecipes#resolve} is the registry-dependent wrapper
 * around this and is covered by {@code PartBuilderGameTests} instead.
 */
class PartBuilderRecipesTest {

    @Test
    void exactValueMatchLeavesNoChange() {
        // 2 cobblestone (2 shard-units each) exactly covers a 4-unit head cost.
        CostResult result = PartBuilderRecipes.computeCost(4, 2);

        assertEquals(2, result.itemsNeeded());
        assertEquals(0, result.changeUnits());
    }

    @Test
    void oversizedItemLeavesChange() {
        // 1 log (8 shard-units) covers a 4-unit head cost, 4 units left over.
        CostResult result = PartBuilderRecipes.computeCost(4, 8);

        assertEquals(1, result.itemsNeeded());
        assertEquals(4, result.changeUnits());
    }

    @Test
    void unitValueItemsNeedExactCount() {
        // 4 sticks/shards (1 shard-unit each) exactly cover a 4-unit head cost.
        CostResult result = PartBuilderRecipes.computeCost(4, 1);

        assertEquals(4, result.itemsNeeded());
        assertEquals(0, result.changeUnits());
    }

    @Test
    void shardsExactlyCoverASmallPartCost() {
        // 2 shards (1 shard-unit each) exactly cover a 2-unit handle/binding cost.
        CostResult result = PartBuilderRecipes.computeCost(2, PartBuilderRecipes.SHARD_VALUE);

        assertEquals(2, result.itemsNeeded());
        assertEquals(0, result.changeUnits());
    }

    @Test
    void roundsUpToTheNextWholeItemWhenValueDoesNotDivideEvenly() {
        // Boundary case: a 3-unit item against a 4-unit cost needs 2 items (6 units), 2 left over.
        CostResult result = PartBuilderRecipes.computeCost(4, 3);

        assertEquals(2, result.itemsNeeded());
        assertEquals(2, result.changeUnits());
    }
}
