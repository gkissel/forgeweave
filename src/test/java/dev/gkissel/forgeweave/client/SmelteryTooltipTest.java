package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.menu.SmelteryMenu;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Guards the smeltery GUI's two pieces of arithmetic (issue #101), both of which upstream 1.12 ships
 * as loops that are easy to get subtly wrong and impossible to notice in a screenshot.
 *
 * <h2>Why the amount cascade is worth pinning</h2>
 *
 * <p>Issue #96 made non-multiples of 144 reachable: melting yields per-item amounts read off loot
 * tables, so vanilla copper ore melts at 504 mB (3.5 ingots) and nether gold ore at 64 mB (4
 * nuggets). Before that, every amount in a smeltery was a whole number of ingots and a cascade that
 * silently dropped its remainder would have looked correct forever. The invariant that actually
 * matters is that the units emitted always add back up to the amount -- a tooltip that loses
 * millibuckets is telling the player they have less metal than they do.
 */
class SmelteryTooltipTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        // FluidStack touches the fluid registry; same two lines ForgeweaveItemColorsTest needs.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Upstream {@code Material.VALUE_*}, mirrored here so a change to either side fails loudly. */
    private static final int INGOT = 144;
    private static final int NUGGET = 16;
    private static final int BLOCK = 1296;
    private static final int GEM = 666;

    private static List<Component> breakdown(int amount, boolean bucketsOnly) {
        return breakdown(amount, bucketsOnly, false);
    }

    private static List<Component> breakdown(int amount, boolean bucketsOnly, boolean gemValued) {
        List<Component> tooltip = new ArrayList<>();
        SmelteryScreen.addAmount(tooltip, amount, bucketsOnly, gemValued);
        return tooltip;
    }

    private static Component unit(int count, String key) {
        return Component.literal(count + " ")
                .append(Component.translatable("gui.forgeweave.smeltery.liquid." + key))
                .withStyle(ChatFormatting.GRAY);
    }

    @Test
    void copperOresFiveHundredAndFourBreaksIntoIngotsNuggetsAndMillibuckets() {
        // 504 = 3*144 + 4*16 + 8. The 8mb remainder is the part a rounding bug would eat.
        assertEquals(List.of(unit(3, "ingot"), unit(4, "nugget"), unit(8, "millibucket")),
                breakdown(504, false));
    }

    @Test
    void netherGoldOresSixtyFourIsWholeNuggetsAndNothingElse() {
        // A unit that divides exactly must not also emit a trailing "0 mb" line.
        assertEquals(List.of(unit(4, "nugget")), breakdown(64, false));
    }

    @Test
    void aWholeIngotEmitsOnlyTheIngotLine() {
        assertEquals(List.of(unit(1, "ingot")), breakdown(INGOT, false));
    }

    @Test
    void blocksAreTakenBeforeIngots() {
        // 1296 is nine ingots, but upstream reports the largest unit that fits first.
        assertEquals(List.of(unit(1, "block")), breakdown(BLOCK, false));
    }

    @Test
    void shiftDropsTheMetalUnitsAndShowsBuckets() {
        // Upstream's `stringFn = isShiftKeyDown ? amountToString : amountToIngotString`.
        assertEquals(List.of(unit(1, "bucket"), unit(504, "millibucket")), breakdown(1504, true));
    }

    @Test
    void anEmptyAmountEmitsNoUnitLines() {
        // Upstream's calcLiquidText only emits a line when the count is non-zero, so an empty tank's
        // "Used:" heading stands alone rather than being followed by a redundant "0 mb".
        assertEquals(List.of(), breakdown(0, false));
    }

    /* Gem-valued fluids (#377: molten emerald, live since #361) */

    @Test
    void aGemFluidIsCountedInGemsNotIngots() {
        // Upstream Material.VALUE_Gem: one emerald is 666 mB, and the metal cascade has no unit that
        // divides it -- 666 through blocks/ingots/nuggets reads "4 Ingots, 5 Nuggets, 10 mb".
        assertEquals(List.of(unit(1, "gem")), breakdown(GEM, false, true));
    }

    @Test
    void aGemFluidNeverReportsBlocksOrIngots() {
        // The regression a flat "add 666 to the shared cascade" would have shipped, from the other
        // side: nothing casts molten emerald into a block or an ingot, so neither unit may appear.
        for (int gems = 1; gems <= 20; gems++) {
            for (Component line : breakdown(gems * GEM, false, true)) {
                String key = line.getSiblings().get(0).getContents().toString();
                assertTrue(!key.contains("liquid.block") && !key.contains("liquid.ingot")
                                && !key.contains("liquid.nugget"),
                        gems + " gems reported a metal unit: " + key);
            }
        }
    }

    @Test
    void aMetalFluidIsNeverBrokenIntoGems() {
        // The regression the gem unit could have shipped: 720 mB of iron is five ingots, and must
        // not become "1 Gems, 3 Nuggets, 6 mb" just because a gem-valued fluid now exists.
        assertEquals(List.of(unit(5, "ingot")), breakdown(5 * INGOT, false));
    }

    @Test
    void shiftDropsTheGemUnitTheSameWayItDropsTheMetalOnes() {
        assertEquals(List.of(unit(1, "bucket"), unit(332, "millibucket")), breakdown(2 * GEM, true, true));
    }

    /**
     * The invariant the individual cases above are examples of: whatever units come out, they add
     * back up to what went in. Swept across every amount a smeltery can hold rather than spot-checked,
     * because the failure mode is a remainder lost at one specific magnitude.
     */
    @Test
    void everyAmountDecomposesWithoutLosingOrInventingMillibuckets() {
        for (boolean bucketsOnly : new boolean[] {false, true}) {
            for (boolean gemValued : new boolean[] {false, true}) {
                for (int amount = 0; amount <= 20_000; amount++) {
                    int total = 0;
                    for (Component line : breakdown(amount, bucketsOnly, gemValued)) {
                        total += valueOf(line, bucketsOnly);
                    }
                    assertEquals(amount, total, "breakdown of " + amount + " mb (bucketsOnly="
                            + bucketsOnly + ", gemValued=" + gemValued + ") does not add back up");
                }
            }
        }
    }

    /** Re-multiplies one emitted line back into millibuckets. */
    private static int valueOf(Component line, boolean bucketsOnly) {
        String count = line.getContents().toString();
        // The line is literal("<n> ") + translatable(key); pull both back out of the component tree.
        int n = Integer.parseInt(count.replaceAll("\\D", ""));
        String key = line.getSiblings().get(0).getContents().toString();
        int unit = key.contains("liquid.block") ? BLOCK
                : key.contains("liquid.gem") ? GEM
                : key.contains("liquid.ingot") ? INGOT
                : key.contains("liquid.nugget") ? NUGGET
                : key.contains("liquid.kilobucket") ? 1_000_000
                : key.contains("liquid.bucket") ? 1_000
                : 1;
        assertTrue(!bucketsOnly || (unit != BLOCK && unit != GEM && unit != INGOT && unit != NUGGET),
                "shift is meant to drop the material units, but got " + key);
        return n * unit;
    }

    /* Fluid column band heights */

    @Test
    void aSingleFluidFillingHalfTheTankGetsHalfTheHeight() {
        int[] heights = SmelteryScreen.fluidHeights(List.of(stack(1152)), 2304, 52);
        assertEquals(1, heights.length);
        assertEquals(26, heights[0]);
    }

    @Test
    void bandsNeverExceedTheTankAndLeaveHeadroomWhilePartFull() {
        // The screenshot-verified case: iron 1152 + copper 576 in a 2304 tank, 52px tall.
        int[] heights = SmelteryScreen.fluidHeights(List.of(stack(1152), stack(576)), 2304, 52);
        assertEquals(26, heights[0], "iron band");
        assertEquals(13, heights[1], "copper band");
        assertTrue(heights[0] + heights[1] <= 52 - 3,
                "a part-full tank must keep upstream's 3px of visible headroom");
    }

    @Test
    void aTraceAmountStillGetsAClickableBand() {
        // Upstream's 3px floor: 1mb in a full-size smeltery must still be visible and hittable.
        int[] heights = SmelteryScreen.fluidHeights(List.of(stack(1)), 93_312, 52);
        assertEquals(3, heights[0]);
    }

    @Test
    void aFullTankOfManyFluidsIsShavedDownToFit() {
        // Twenty fluids at the 3px floor is 60px of demand for 52px of tank; the shave-the-tallest
        // loop has to bring that back under without going negative.
        List<FluidStack> fluids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            fluids.add(stack(100));
        }
        int[] heights = SmelteryScreen.fluidHeights(fluids, 2000, 52);
        int total = 0;
        for (int height : heights) {
            assertTrue(height >= 0, "a band was shaved past zero");
            total += height;
        }
        assertTrue(total <= 52, "bands overflow the tank: " + total + "px in 52px");
    }

    @Test
    void theBandUnderTheCursorIsFoundFromTheBottomUp() {
        List<FluidStack> fluids = List.of(stack(1152), stack(576)); // 26px iron, then 13px copper
        // Rows are measured from the top of the 52px tank; iron occupies the bottom 26.
        assertEquals(0, SmelteryScreen.fluidAt(fluids, 2304, 52, 51), "bottom row is the bottom fluid");
        assertEquals(0, SmelteryScreen.fluidAt(fluids, 2304, 52, 26), "top row of the iron band");
        assertEquals(1, SmelteryScreen.fluidAt(fluids, 2304, 52, 25), "bottom row of the copper band");
        assertEquals(1, SmelteryScreen.fluidAt(fluids, 2304, 52, 13), "top row of the copper band");
        assertEquals(-1, SmelteryScreen.fluidAt(fluids, 2304, 52, 12), "empty headroom is not a fluid");
        assertEquals(-1, SmelteryScreen.fluidAt(fluids, 2304, 52, 0), "the very top is not a fluid");
    }

    @Test
    void anEmptyTankHasNoBandAnywhere() {
        for (int row = 0; row < 52; row++) {
            assertEquals(-1, SmelteryScreen.fluidAt(List.of(), 2304, 52, row),
                    "empty tank reported a fluid at row " + row);
        }
    }

    /* Tank band top edge (issue #308: JEI ingredient-under-mouse) */

    @Test
    void theBottomBandsTopEdgeIsTheTankHeightMinusItsOwnHeight() {
        List<FluidStack> fluids = List.of(stack(1152), stack(576)); // 26px iron, then 13px copper
        assertEquals(52 - 26, SmelteryScreen.tankBandTop(fluids, 2304, 52, 0), "iron band starts above the floor");
    }

    @Test
    void theTopBandsTopEdgeSitsAboveEveryBandBelowIt() {
        List<FluidStack> fluids = List.of(stack(1152), stack(576)); // 26px iron, then 13px copper
        assertEquals(52 - 26 - 13, SmelteryScreen.tankBandTop(fluids, 2304, 52, 1), "copper band starts above the iron band");
    }

    @Test
    void everyBandsTopEdgePlusItsHeightReachesTheBandBelow() {
        // The invariant tankBandTop and fluidHeights must never disagree on: band i's top edge is
        // exactly where band i-1 (or the tank floor, for band 0) begins.
        List<FluidStack> fluids = List.of(stack(1152), stack(576), stack(100));
        int capacity = 2304;
        int[] heights = SmelteryScreen.fluidHeights(fluids, capacity, 52);
        int expectedBottom = 52;
        for (int i = 0; i < heights.length; i++) {
            int top = SmelteryScreen.tankBandTop(fluids, capacity, 52, i);
            assertEquals(expectedBottom, top + heights[i], "band " + i + "'s top+height must reach the band below it");
            expectedBottom = top;
        }
    }

    /** A fluid stack of the given amount; which fluid it is never reaches the height maths. */
    private static FluidStack stack(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    /* Melt-slot stall diagnostics (#377) */

    /** Lava's own working heat, and a recipe that needs more of it than lava has. */
    private static final int LAVA = 1300;
    private static final int AMBIENT = MeltingRecipe.AMBIENT_TEMPERATURE;

    @Test
    void aStalledSlotIsExplainedAsNoSpace() {
        assertEquals("progress.no_space", SmelteryScreen.stallReason(true, LAVA, 100));
    }

    @Test
    void aStalledSlotIsStillExplainedAsNoSpaceWhenTheFuelAlsoRanOut() {
        // Deliberately not upstream's branch order (it tests no_fuel before its overheat state): the
        // drawn bar is already the stalled variant here, and a bar saying "full" over a tooltip
        // saying "no fuel" is worse than either message alone. The melt is done; the tank is the
        // thing to fix.
        assertEquals("progress.no_space", SmelteryScreen.stallReason(true, 0, 100));
    }

    @Test
    void aSmelteryWithNothingBurnableInReachIsExplainedAsNoFuel() {
        assertEquals("progress.no_fuel", SmelteryScreen.stallReason(false, 0, 100));
    }

    @Test
    void aRecipeHotterThanTheFuelIsExplainedAsNoHeat() {
        // The GameTest fixture's 1400-degree recipe that "lava's own 1300 degrees can never reach".
        assertEquals("progress.no_heat", SmelteryScreen.stallReason(false, LAVA, 1400 - AMBIENT));
    }

    @Test
    void aSlotTheSmelteryIsExactlyHotEnoughForHasNothingToExplain() {
        // The boundary SmelteryControllerBlockEntity#meltTick uses: it skips a slot when the
        // smeltery's heat is strictly less than the recipe's, so equal heat melts and says nothing.
        assertEquals(null, SmelteryScreen.stallReason(false, LAVA, LAVA - AMBIENT));
        assertEquals("progress.no_heat", SmelteryScreen.stallReason(false, LAVA, LAVA - AMBIENT + 1));
    }

    /* Heat-bar hit box (#377) */

    @Test
    void theHeatBarHitBoxIsTheDrawnBarAndNotTheWholeCell() {
        assertEquals(0, SmelteryScreen.heatBarAt(0, 1, 1), "the bar's own top-left pixel");
        assertEquals(0, SmelteryScreen.heatBarAt(0, 3, 16), "the bar's own bottom-right pixel");
        assertEquals(-1, SmelteryScreen.heatBarAt(0, 0, 0), "the 1px bevel above and left of the bar");
        assertEquals(-1, SmelteryScreen.heatBarAt(0, 4, 1), "the slot beside the bar is not the bar");
        assertEquals(-1, SmelteryScreen.heatBarAt(0, 1, 17), "the cell's last row is below the 16px bar");
        assertEquals(-1, SmelteryScreen.heatBarAt(0, -1, 1), "left of the grid entirely");
        assertEquals(-1, SmelteryScreen.heatBarAt(0, 1, -1), "above the grid entirely");
    }

    @Test
    void everyCellsBarMapsBackToTheSlotDrawnInIt() {
        for (int row = 0; row < SmelteryMenu.MELT_VISIBLE_ROWS; row++) {
            for (int col = 0; col < SmelteryMenu.MELT_COLUMNS; col++) {
                int x = col * SmelteryMenu.CELL_WIDTH + 1;
                int y = row * SmelteryMenu.SLOT_SIZE + 1;
                assertEquals(row * SmelteryMenu.MELT_COLUMNS + col, SmelteryScreen.heatBarAt(0, x, y),
                        "bar at row " + row + ", column " + col);
            }
        }
        assertEquals(-1, SmelteryScreen.heatBarAt(0, SmelteryMenu.MELT_COLUMNS * SmelteryMenu.CELL_WIDTH + 1, 1),
                "a fourth column is off the grid");
        assertEquals(-1, SmelteryScreen.heatBarAt(0, 1, SmelteryMenu.MELT_VISIBLE_ROWS * SmelteryMenu.SLOT_SIZE + 1),
                "a fourth row is off the grid");
    }

    @Test
    void scrollingShiftsWhichSlotABarBelongsTo() {
        // The window shows rows scrollRow..scrollRow+2, so the top-left bar is the first slot of
        // whichever row is scrolled to -- the same offset renderHeatBars indexes with.
        assertEquals(SmelteryMenu.MELT_COLUMNS, SmelteryScreen.heatBarAt(1, 1, 1));
        assertEquals(4 * SmelteryMenu.MELT_COLUMNS + 2,
                SmelteryScreen.heatBarAt(2, 2 * SmelteryMenu.CELL_WIDTH + 1, 2 * SmelteryMenu.SLOT_SIZE + 1));
    }
}
