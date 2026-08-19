package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.menu.StencilTableMenu;

/**
 * Pins issue #605's finding: upstream 1.12 registers the shard as a stencil-craftable tool part
 * like any other, and it is the only part whose cost is not a whole number of ingots -- which is
 * what makes shard change reachable from a plain ingot/plank rather than only from an oversized
 * input (a log, a metal block).
 *
 * <pre>
 *   // tools/TinkerTools.java:138,142,154
 *   shard = registerItem(registry, new Shard(), "shard");
 *   TinkerRegistry.registerToolPart(shard);
 *   TinkerRegistry.registerStencilTableCrafting(Pattern.setTagForPart(new ItemStack(pattern), shard));
 *
 *   // library/tools/Shard.java:18,38
 *   public Shard() { super(Material.VALUE_Shard); }
 *   public boolean canUseMaterial(Material mat) { return true; }
 * </pre>
 *
 * <p>{@code Shard#canUseMaterial} being unconditionally true is why the part carries
 * {@link PartItem.Kind#NONE} here: {@code Material#hasStatsFor} answers {@code true} for it, so a
 * shard can be stamped from any craftable material, stat blocks or not.
 */
class ShardPatternTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void shardIsAPartCostingHalfAnIngot() {
        assertEquals(PartItem.Kind.NONE, ForgeweaveItems.SHARD.get().kind());
        assertEquals(Optional.of(PartBuilderRecipes.INGOT_VALUE / 2),
                PartBuilderRecipes.patternCost(new ItemStack(ForgeweaveItems.PATTERN_SHARD.get())));
        assertEquals(Optional.of(ForgeweaveItems.SHARD.get()),
                PartBuilderRecipes.patternPart(new ItemStack(ForgeweaveItems.PATTERN_SHARD.get())));
    }

    @Test
    void shardPatternIsStencilTableSelectable() {
        assertTrue(StencilTableMenu.PATTERNS.contains(ForgeweaveItems.PATTERN_SHARD),
                "the stencil table must offer the shard pattern");
    }

    /**
     * The reachability the playtest could not find (issue #605, playtest 0.3.5-alpha.3 item 7.a):
     * one ingot-valued item covers the shard's 72 cost with 72 left over, i.e. exactly one shard of
     * change. Every other part costs a whole number of ingots, so a plain ingot/plank/cobblestone
     * never leaves a remainder at any of them.
     */
    @Test
    void oneIngotValuedItemPaysAShardAndLeavesOneShardOfChange() {
        PartBuilderRecipes.CostResult result =
                PartBuilderRecipes.computeCost(PartBuilderRecipes.SHARD_VALUE, PartBuilderRecipes.INGOT_VALUE);

        assertEquals(1, result.itemsNeeded());
        assertEquals(1, PartBuilderRecipes.shardChange(result.changeUnits()));
    }
}
