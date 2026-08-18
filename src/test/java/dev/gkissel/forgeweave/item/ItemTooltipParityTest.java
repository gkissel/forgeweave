package dev.gkissel.forgeweave.item;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredItem;

import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

/**
 * The 1.12 tooltip-parity batch that lives outside {@code ToolTooltip}/{@link PartItem} (issue
 * #379): a pattern quoting what its part costs, a broken seared tank still naming what was inside,
 * and the guide book's flavour line. Each pins the piece of arithmetic or plumbing that can silently
 * drift -- notably the shard-unit-to-ingot conversion, which is the only reason the pattern's number
 * differs from the Part Builder panel's.
 */
class ItemTooltipParityTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<Component> hover(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);
        return tooltip;
    }

    private static ItemStack stackOf(DeferredItem<? extends Item> item) {
        return new ItemStack(item.get());
    }

    private static Component costLine(String ingots) {
        return Component.translatable("tooltip.forgeweave.pattern_cost", ingots);
    }

    /**
     * Upstream prints {@code getCost() / VALUE_Ingot}; Forgeweave's costs are in the same unit (T58,
     * issue #489), so a head part's 288 reads as 2 ingots and a large head's 1152 as 8 -- the same numbers
     * upstream's own {@code TinkerTools#registerToolParts} calls produce.
     */
    @Test
    void patternsQuoteTheirPartsCostInIngots() {
        assertEquals(144, PartBuilderRecipes.INGOT_VALUE, "one ingot is upstream's VALUE_Ingot (T58, issue #489)");

        assertEquals(List.of(costLine("2")), hover(stackOf(ForgeweaveItems.PATTERN_PICKAXE_HEAD)));
        assertEquals(List.of(costLine("1")), hover(stackOf(ForgeweaveItems.PATTERN_TOOL_HANDLE)));
        assertEquals(List.of(costLine("3")), hover(stackOf(ForgeweaveItems.PATTERN_TOUGH_TOOL_ROD)));
        assertEquals(List.of(costLine("8")), hover(stackOf(ForgeweaveItems.PATTERN_HAMMER_HEAD)));
    }

    /** The blank pattern stamps no part, so there is no cost to quote and no line to show. */
    @Test
    void theBlankPatternQuotesNoCost() {
        assertTrue(hover(stackOf(ForgeweaveItems.PATTERN_BLANK)).isEmpty());
    }

    /**
     * Upstream's {@code ItemTank} reads the tank NBT its drop carried; Forgeweave reads the
     * {@code FLUID_CONTENT} component the tank loot table copies off the block entity.
     */
    @Test
    void aTankItemNamesTheFluidAndAmountItWasBrokenWith() {
        ItemStack stack = stackOf(ForgeweaveItems.SEARED_TANK);
        stack.set(ForgeweaveDataComponents.FLUID_CONTENT.get(),
                SimpleFluidContent.copyOf(new FluidStack(Fluids.WATER, 1500)));

        assertEquals(List.of(
                new FluidStack(Fluids.WATER, 1500).getHoverName().copy().withStyle(ChatFormatting.GRAY),
                Component.translatable("tooltip.forgeweave.tank.amount", 1500).withStyle(ChatFormatting.GRAY)),
                hover(stack));
    }

    @Test
    void anEmptyTankItemShowsNoFluidLines() {
        assertTrue(hover(stackOf(ForgeweaveItems.SEARED_TANK)).isEmpty(),
                "a tank broken empty carries no fluid component to describe");
    }

    @Test
    void theGuideBookCarriesItsGreyFlavourLine() {
        assertEquals(List.of(Component.translatable("tooltip.forgeweave.guide_book").withStyle(ChatFormatting.GRAY)),
                hover(stackOf(ForgeweaveItems.GUIDE_BOOK)));
    }
}
