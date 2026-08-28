package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredItem;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.SlimeColour;
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

    // ---------------------------------------------------------------- issue #783

    /** A one-line component, styled like every other flavour tooltip in this file. */
    private static Component reagentLine(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }

    /** Moss is not itself a modifier reagent -- it reuses #752's bookshelf-conversion line. */
    @Test
    void mossPointsToItsBookshelfConversion() {
        assertEquals(List.of(reagentLine("tooltip.forgeweave.mending_moss.source")),
                hover(stackOf(ForgeweaveItems.MOSS)));
    }

    /**
     * Every modifier reagent registered through {@code DescribedItem} shows its own modifier's
     * name and description (so the two can't drift apart -- ForgeweaveItems#modifierReagentTooltip
     * reads the same key family {@link dev.gkissel.forgeweave.data.ForgeweaveLanguageProvider} and
     * {@code ModifierLangCoverageTest} already guard) plus the shared "where" line.
     */
    @Test
    void everyModifierReagentNamesItsModifierAndWhereItsApplied() {
        record Reagent(DeferredItem<? extends Item> item, String modifierId) {}
        List<Reagent> reagents = List.of(
                new Reagent(ForgeweaveItems.MENDING_MOSS, "mending_moss"),
                new Reagent(ForgeweaveItems.REINFORCED_PLATE, "reinforced"),
                new Reagent(ForgeweaveItems.SILKY_JEWEL, "silky"),
                new Reagent(ForgeweaveItems.EXTRA_MODIFIER, "extra_slot"),
                new Reagent(ForgeweaveItems.NECROTIC_BONE, "necrotic"),
                new Reagent(ForgeweaveItems.EXPANDER_W, "harvest_width"),
                new Reagent(ForgeweaveItems.EXPANDER_H, "harvest_height"));

        for (Reagent reagent : reagents) {
            assertEquals(List.of(
                    reagentLine("modifier.forgeweave." + reagent.modifierId() + ".name"),
                    reagentLine("modifier.forgeweave." + reagent.modifierId() + ".description"),
                    reagentLine("tooltip.forgeweave.reagent.tool_station")),
                    hover(stackOf(reagent.item())), reagent.modifierId() + "'s reagent item");
        }
    }

    /** Silky Cloth is Silky Jewel's crafting precursor, not a modifier reagent of its own. */
    @Test
    void silkyClothNamesWhatItsGroundInto() {
        assertEquals(List.of(reagentLine("tooltip.forgeweave.silky_cloth")),
                hover(stackOf(ForgeweaveItems.SILKY_CLOTH)));
    }

    /** Issue #727's Part Builder material for nahuatl, cast rather than mined. */
    @Test
    void nahuatlBoardNamesItsSourceAndUse() {
        assertEquals(List.of(reagentLine("tooltip.forgeweave.nahuatl_board")),
                hover(stackOf(ForgeweaveItems.NAHUATL_BOARD)));
    }

    /**
     * Every reusable gold cast and its single-use clay counterpart (issue #292): walks
     * {@link ForgeweaveItems#CLAY_CASTS}' key set rather than re-listing all 37 names a second time,
     * so a cast added to that map is covered here for free.
     */
    @Test
    void everyGoldAndClayCastCarriesItsSharedTooltipLine() {
        assertFalse(ForgeweaveItems.CLAY_CASTS.isEmpty());
        for (String castName : ForgeweaveItems.CLAY_CASTS.keySet()) {
            Item goldCast = BuiltInRegistries.ITEM
                    .get(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, castName));
            assertEquals(List.of(reagentLine("tooltip.forgeweave.cast")),
                    hover(new ItemStack(goldCast)), castName + " (gold cast)");
            assertEquals(List.of(reagentLine("tooltip.forgeweave.clay_cast")),
                    hover(stackOf(ForgeweaveItems.CLAY_CASTS.get(castName))), "clay_" + castName);
        }
    }

    /**
     * Upstream Mantle's {@code ItemEdible#addInformation} always lists the potion effects a food
     * carries; Forgeweave's slime balls and drops carried none until issue #783's audit ({@link
     * SlimeFoodItem}). Blue is pinned exactly (order matters, upstream lists them in add order); the
     * rest of the ten are swept for "at least one line" so a colour added without effects still fails.
     */
    @Test
    void slimeBallBlueListsBothItsPotionEffectsInOrder() {
        assertEquals(List.of(MobEffects.MOVEMENT_SLOWDOWN.value().getDisplayName(),
                MobEffects.JUMP.value().getDisplayName()),
                hover(stackOf(ForgeweaveItems.slimeBallItem(SlimeColour.BLUE))));
    }

    @Test
    void everySlimeBallAndDropListsAtLeastOnePotionEffect() {
        for (ForgeweaveItems.SlimeBall ball : ForgeweaveItems.slimeBalls()) {
            assertFalse(hover(stackOf(ball.item())).isEmpty(), ball.colour() + " slime ball lists no effects");
        }
        for (ForgeweaveItems.SlimeDrop drop : ForgeweaveItems.slimeDrops()) {
            assertFalse(hover(stackOf(drop.item())).isEmpty(), drop.colour() + " slime drop lists no effects");
        }
    }
}
