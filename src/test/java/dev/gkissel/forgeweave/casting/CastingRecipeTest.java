package dev.gkissel.forgeweave.casting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Pins the shipped casting recipes (docs/SCOPE.md M2 issue #100) against upstream 1.12's own
 * constants, and round-trips the codec that reads them.
 */
class CastingRecipeTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    // ------------------------------------------------------------------ the codec

    @Test
    void aRecipeSurvivesAnEncodeDecodeRoundTrip() {
        CastingRecipe original = shipped("pickaxe_head_iron");

        JsonElement encoded = CastingRecipe.CODEC.encodeStart(ops, original).getOrThrow();
        CastingRecipe decoded = CastingRecipe.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(original.station(), decoded.station());
        assertEquals(original.fluid(), decoded.fluid());
        assertEquals(original.amount(), decoded.amount());
        assertEquals(original.consumesCast(), decoded.consumesCast());
        assertEquals(original.resultInInput(), decoded.resultInInput());
        assertEquals(original.time(), decoded.time());
        assertTrue(ItemStack.matches(original.result(), decoded.result()), "result stack, components and all");
        assertTrue(decoded.cast().isPresent());
        assertTrue(decoded.cast().get().test(new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get())));
    }

    @Test
    void theOptionalFieldsDefaultTheWayAPlainCastingRecipeNeeds() {
        CastingRecipe plain = parse("""
                {"station": "table", "cast": {"item": "forgeweave:cast_ingot"},
                 "fluid": "minecraft:lava", "amount": 144, "result": {"id": "minecraft:iron_ingot"}}
                """);

        assertFalse(plain.consumesCast(), "a cast survives its own casting cycle unless a recipe says otherwise");
        assertFalse(plain.resultInInput());
        assertTrue(plain.time().isEmpty(), "with no explicit time the cooldown is derived");
    }

    @Test
    void aRecipeWithoutAnAmountDoesNotParse() {
        DataResult<CastingRecipe> result = CastingRecipe.CODEC.parse(ops, JsonParser.parseString("""
                {"station": "table", "fluid": "minecraft:lava", "result": {"id": "minecraft:iron_ingot"}}
                """));

        assertTrue(result.isError(), "a casting recipe with no fluid amount must not parse");
    }

    // ------------------------------------------------------------------ upstream's constants

    /**
     * Upstream's {@code TinkerSmeltery#castCreationFluids}: {@code Material.VALUE_Ingot * 2} of
     * molten gold, consuming the part it was moulded around and leaving the fresh cast in the input
     * slot ({@code consumesCast}/{@code switchOutputs} both true).
     */
    @Test
    void castCreationCostsTwoIngotsOfMoltenGoldAndEatsThePart() {
        for (String part : List.of("pickaxe_head", "shovel_head", "axe_head", "tool_binding", "tool_handle")) {
            CastingRecipe recipe = shipped("cast_" + part);

            assertEquals(288, recipe.amount(), part + " cast creation costs 288 mB");
            assertEquals(ForgeweaveFluids.GOLD.still().get(), recipe.fluid(), "casts are gold-only (pure parity)");
            assertTrue(recipe.consumesCast(), part + " is consumed by the pour that moulds its cast");
            assertTrue(recipe.resultInInput(), "and the cast lands in the input slot, ready for metal");
            assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "cast_" + part),
                    BuiltInRegistries.ITEM.getKey(recipe.result().getItem()));
        }
    }

    /**
     * Issue #292, upstream's {@code clayCreationFluids}: a clay cast is moulded from
     * {@code Material.VALUE_Ingot * 2} of molten clay instead of gold, and is otherwise the same
     * pour -- the part is eaten and the fresh cast lands in the input slot.
     */
    @Test
    void clayCastCreationCostsTwoIngotsOfMoltenClayAndEatsThePartToo() {
        for (String part : List.of("pickaxe_head", "shovel_head", "axe_head", "tool_binding", "tool_handle")) {
            CastingRecipe recipe = shipped("clay_cast_" + part);

            assertEquals(288, recipe.amount(), part + " clay cast creation costs 288 mB");
            assertEquals(ForgeweaveFluids.MOLTEN_CLAY.still().get(), recipe.fluid(), "moulded from molten clay");
            assertTrue(recipe.consumesCast(), part + " is consumed by the pour that moulds its clay cast");
            assertTrue(recipe.resultInInput(), "and the cast lands in the input slot, ready for metal");
            assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "clay_cast_" + part),
                    BuiltInRegistries.ITEM.getKey(recipe.result().getItem()));
        }
    }

    /**
     * Issue #292's single-use half ({@code TinkerSmeltery#registerToolpartMeltingCasting} passes
     * {@code consumesCast} true for the clay cast and false for the gold one): same fluid, same
     * amount, same part out -- the cast is what differs.
     */
    @Test
    void aClayCastIsConsumedByItsOwnCastingCycleWhereTheGoldCastSurvives() {
        CastingRecipe gold = shipped("axe_head_iron");
        CastingRecipe clay = shipped("clay_axe_head_iron");

        assertFalse(gold.consumesCast(), "the gold cast is reusable");
        assertTrue(clay.consumesCast(), "the clay cast is single use");
        assertEquals(gold.fluid(), clay.fluid());
        assertEquals(gold.amount(), clay.amount());
        assertTrue(ItemStack.matches(gold.result(), clay.result()), "and both shape the same iron axe head");
        assertTrue(clay.cast().orElseThrow()
                .test(new ItemStack(ForgeweaveItems.CLAY_CASTS.get("cast_axe_head").get())));
    }

    /** Upstream {@code TinkerTools}: pick/shovel/axe head cost {@code VALUE_Ingot * 2}, binding and rod {@code VALUE_Ingot}. */
    @Test
    void partCastingUsesUpstreamsPartCostsAndTagsTheResultWithItsMaterial() {
        assertEquals(288, shipped("pickaxe_head_iron").amount());
        assertEquals(288, shipped("shovel_head_iron").amount());
        assertEquals(288, shipped("axe_head_iron").amount());
        assertEquals(144, shipped("tool_binding_iron").amount());
        assertEquals(144, shipped("tool_handle_iron").amount());

        CastingRecipe recipe = shipped("pickaxe_head_manyullyn");
        assertEquals(ForgeweaveItems.PART_PICKAXE_HEAD.get(), recipe.result().getItem());
        assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "manyullyn"),
                recipe.result().get(ForgeweaveDataComponents.MATERIAL.get()));
        assertFalse(recipe.consumesCast(), "casts are reusable (pure parity)");
    }

    @Test
    void ingotNuggetAndBlockCastingUseTheSameScaleAsMelting() {
        assertEquals(144, shipped("ingot_iron").amount(), "Material.VALUE_Ingot");
        assertEquals(16, shipped("nugget_iron").amount(), "Material.VALUE_Nugget");
        assertEquals(1296, shipped("block_iron").amount(), "Material.VALUE_Block");
        assertEquals(CastingRecipe.Station.BASIN, shipped("block_iron").station(), "blocks are cast in the basin");
        assertTrue(shipped("block_iron").cast().isEmpty(), "and the basin has to be empty");
    }

    /** Upstream's {@code calcCooldownTime}: {@code 24 + (temperature - 300) * amount / 1600}. */
    @Test
    void cooldownFollowsUpstreamsFormula() {
        assertEquals(24 + (532 - 300) * 288 / 1600, shipped("cast_pickaxe_head").cooldownTicks(),
                "molten gold at 532 K, 288 mB");
        assertEquals(24 + (769 - 300) * 144 / 1600, shipped("ingot_iron").cooldownTicks(),
                "molten iron at 769 K, 144 mB");
        assertEquals(24, CastingRecipe.cooldownTicks(Fluids.WATER, 1000),
                "a room-temperature fluid cools in the faucet's own pour time");
    }

    /**
     * Issue #474 (parity audit T43), upstream's {@code BucketCastingRecipe}: registered once for
     * {@code Items.BUCKET}, matching whatever fluid is poured over it via the item's fluid
     * capability. Every other casting recipe here is already one datapack row per (station, cast,
     * fluid) rather than upstream's in-code registries, so this ships as one row per fluid this mod
     * already makes bucketable (issue #286) instead of a second, fluid-agnostic Java match path --
     * same player-facing result (any bucketable fluid poured over an empty bucket fills it), recorded
     * as a deviation in the PR body. {@code getTime()} is hardcoded to 5 ticks upstream, not the
     * usual temperature-based cooldown.
     */
    @Test
    void everyBucketableFluidHasATableBucketCastingRecipe() {
        for (ForgeweaveFluids.MoltenMetal metal : ForgeweaveFluids.all()) {
            CastingRecipe recipe = shipped("bucket_" + metal.name());

            assertEquals(CastingRecipe.Station.TABLE, recipe.station(), metal.name());
            assertEquals(metal.still().get(), recipe.fluid(), metal.name());
            assertEquals(1000, recipe.amount(), metal.name() + ": one bucket's worth");
            assertTrue(ItemStack.matches(new ItemStack(metal.bucket().get()), recipe.result()), metal.name());
            assertTrue(recipe.consumesCast(), metal.name() + ": the empty bucket is consumed");
            assertFalse(recipe.resultInInput(), metal.name());
            assertEquals(5, recipe.time().orElseThrow(), metal.name() + ": upstream's flat 5-tick cool");
            assertTrue(recipe.cast().orElseThrow().test(new ItemStack(Items.BUCKET)), metal.name());
            assertTrue(recipe.matches(CastingRecipe.Station.TABLE, new ItemStack(Items.BUCKET), metal.still().get()),
                    metal.name());
        }
    }

    // ------------------------------------------------------------------ matching

    @Test
    void aRecipeMatchesOnlyItsOwnStationCastAndFluid() {
        CastingRecipe ironPickaxeHead = shipped("pickaxe_head_iron");
        ItemStack cast = new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get());
        var moltenIron = ForgeweaveFluids.IRON.still().get();

        assertTrue(ironPickaxeHead.matches(CastingRecipe.Station.TABLE, cast, moltenIron));
        assertFalse(ironPickaxeHead.matches(CastingRecipe.Station.BASIN, cast, moltenIron), "wrong station");
        assertFalse(ironPickaxeHead.matches(CastingRecipe.Station.TABLE, cast, Fluids.LAVA), "wrong fluid");
        assertFalse(ironPickaxeHead.matches(CastingRecipe.Station.TABLE,
                new ItemStack(ForgeweaveItems.CAST_AXE_HEAD.get()), moltenIron), "wrong cast");
        assertFalse(ironPickaxeHead.matches(CastingRecipe.Station.TABLE, ItemStack.EMPTY, moltenIron), "no cast at all");
    }

    @Test
    void aCastlessRecipeRequiresAnEmptyBlock() {
        CastingRecipe ironBlock = shipped("block_iron");
        var moltenIron = ForgeweaveFluids.IRON.still().get();

        assertTrue(ironBlock.matches(CastingRecipe.Station.BASIN, ItemStack.EMPTY, moltenIron));
        assertFalse(ironBlock.matches(CastingRecipe.Station.BASIN, new ItemStack(Items.IRON_INGOT), moltenIron));
    }

    /**
     * Nothing in the shipped set matches the same station, cast and fluid twice -- a duplicate would
     * make which recipe runs depend on registry iteration order.
     */
    @Test
    void castCreationAndPartCastingCannotBothClaimTheSamePour() {
        CastingRecipe creation = shipped("cast_pickaxe_head");
        var moltenGold = ForgeweaveFluids.GOLD.still().get();

        assertTrue(creation.matches(CastingRecipe.Station.TABLE,
                new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get()), moltenGold), "gold over a part makes a cast");
        assertFalse(creation.matches(CastingRecipe.Station.TABLE,
                new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get()), moltenGold), "gold over a cast does not");
    }

    // ------------------------------------------------------------------ #502 (T71 parity audit)

    /**
     * Upstream {@code TinkerRegistry.registerTableCasting(TinkerCommons.mudBrick, castIngot,
     * TinkerFluids.dirt, Material.VALUE_Ingot)}: a mud brick is cast at the table through an ingot
     * cast, same shape and amount as any metal ingot's own {@code ingot_*.json} recipe.
     */
    @Test
    void mudBrickCastsAtTheTableThroughAnIngotCast() {
        CastingRecipe recipe = shipped("mud_brick");

        assertEquals(CastingRecipe.Station.TABLE, recipe.station());
        assertEquals(144, recipe.amount(), "Material.VALUE_Ingot");
        assertEquals(ForgeweaveFluids.MOLTEN_DIRT.still().get(), recipe.fluid());
        assertEquals(ForgeweaveItems.MUD_BRICK.get(), recipe.result().getItem());
        assertTrue(recipe.cast().isPresent());
        assertTrue(recipe.cast().get().test(new ItemStack(ForgeweaveItems.CAST_INGOT.get())));
    }

    /**
     * Upstream {@code TinkerRegistry.registerBasinCasting(new ItemStack(Blocks.HARDENED_CLAY),
     * ItemStack.EMPTY, TinkerFluids.clay, Material.VALUE_BrickBlock)}: an empty basin under molten
     * clay makes plain terracotta.
     */
    @Test
    void terracottaCastsInAnEmptyBasinFromMoltenClay() {
        CastingRecipe recipe = shipped("terracotta");

        assertEquals(CastingRecipe.Station.BASIN, recipe.station());
        assertEquals(576, recipe.amount(), "Material.VALUE_BrickBlock");
        assertEquals(ForgeweaveFluids.MOLTEN_CLAY.still().get(), recipe.fluid());
        assertTrue(recipe.cast().isEmpty(), "the basin has to be empty");
        assertTrue(recipe.matches(CastingRecipe.Station.BASIN, ItemStack.EMPTY, ForgeweaveFluids.MOLTEN_CLAY.still().get()));
    }

    /** Upstream's {@code Config.castableBricks} table recipe: a brick casts through an ingot cast, one ingot's worth of molten clay. */
    @Test
    void brickCastsAtTheTableThroughAnIngotCast() {
        CastingRecipe recipe = shipped("brick");

        assertEquals(CastingRecipe.Station.TABLE, recipe.station());
        assertEquals(144, recipe.amount(), "Material.VALUE_Ingot");
        assertEquals(ForgeweaveFluids.MOLTEN_CLAY.still().get(), recipe.fluid());
        assertEquals(Items.BRICK, recipe.result().getItem());
    }

    /**
     * Upstream: "funny thing about hardened clay. If it's stained and you wash it with water, it
     * turns back into regular hardened clay!" -- {@code FluidStack(WATER, 250)}, 150 explicit cooldown
     * ticks, {@code consumesCast: true} (upstream's fourth constructor arg).
     */
    @Test
    void stainedTerracottaWashesBackToPlainTerracottaWithWater() {
        CastingRecipe recipe = shipped("stained_terracotta_wash");

        assertEquals(CastingRecipe.Station.BASIN, recipe.station());
        assertEquals(Fluids.WATER, recipe.fluid());
        assertEquals(250, recipe.amount());
        assertEquals(150, recipe.cooldownTicks(), "upstream's explicit 150-tick wash time, not the derived formula");
        assertTrue(recipe.consumesCast());
        assertEquals(Items.TERRACOTTA, recipe.result().getItem());
        assertTrue(recipe.cast().get().test(new ItemStack(Items.RED_TERRACOTTA)));
        assertFalse(recipe.cast().get().test(new ItemStack(Items.TERRACOTTA)), "plain terracotta is not stained");
    }

    /** Upstream {@code CastingRecipe(new ItemStack(Blocks.SAND, 1, 1), RecipeMatch.of(sand), FluidStack(blood, 10), true, false)}. */
    @Test
    void redSandCastsFromPlainSandAndBlood() {
        CastingRecipe recipe = shipped("red_sand");

        assertEquals(CastingRecipe.Station.BASIN, recipe.station());
        assertEquals(10, recipe.amount());
        assertEquals(ForgeweaveFluids.BLOOD.still().get(), recipe.fluid());
        assertEquals(Items.RED_SAND, recipe.result().getItem());
        assertTrue(recipe.cast().get().test(new ItemStack(Items.SAND)));
    }

    // ------------------------------------------------------------------ #469 (T38 parity audit)

    /**
     * Upstream {@code TinkerRegistry.registerTableCasting(TinkerCommons.searedBrick, castIngot,
     * TinkerFluids.searedStone, Material.VALUE_SearedMaterial)}: a loose seared brick casts at the
     * table through a reusable ingot cast, same shape as any metal ingot's own recipe.
     */
    @Test
    void searedBrickCastsAtTheTableThroughAnIngotCast() {
        CastingRecipe recipe = shipped("seared_brick");

        assertEquals(CastingRecipe.Station.TABLE, recipe.station());
        assertEquals(72, recipe.amount(), "Material.VALUE_SearedMaterial");
        assertEquals(ForgeweaveFluids.SEARED_STONE.still().get(), recipe.fluid());
        assertEquals(ForgeweaveItems.SEARED_BRICK.get(), recipe.result().getItem());
        assertFalse(recipe.consumesCast(), "the ingot cast is reusable (pure parity)");
        assertTrue(recipe.cast().orElseThrow().test(new ItemStack(ForgeweaveItems.CAST_INGOT.get())));
    }

    /**
     * Upstream {@code TinkerRegistry.registerBasinCasting(blockSeared, ItemStack.EMPTY,
     * TinkerFluids.searedStone, Material.VALUE_SearedBlock)}: an empty basin under molten seared
     * stone makes a plain seared stone block.
     */
    @Test
    void searedStoneCastsInAnEmptyBasin() {
        CastingRecipe recipe = shipped("seared_stone");

        assertEquals(CastingRecipe.Station.BASIN, recipe.station());
        assertEquals(288, recipe.amount(), "Material.VALUE_SearedBlock");
        assertEquals(ForgeweaveFluids.SEARED_STONE.still().get(), recipe.fluid());
        assertEquals(ForgeweaveItems.SEARED_STONE.get(), recipe.result().getItem());
        assertTrue(recipe.cast().isEmpty(), "the basin has to be empty");
    }

    /**
     * Upstream {@code new CastingRecipe(searedCobble, RecipeMatch.of("cobblestone"),
     * TinkerFluids.searedStone, Material.VALUE_SearedBlock - Material.VALUE_SearedMaterial, true,
     * false)}: plain cobblestone in the basin, consumed by the pour, comes out seared.
     */
    @Test
    void searedCobblestoneCastsFromPlainCobblestoneInTheBasin() {
        CastingRecipe recipe = shipped("seared_cobblestone");

        assertEquals(CastingRecipe.Station.BASIN, recipe.station());
        assertEquals(216, recipe.amount(), "VALUE_SearedBlock - VALUE_SearedMaterial");
        assertEquals(ForgeweaveFluids.SEARED_STONE.still().get(), recipe.fluid());
        assertEquals(ForgeweaveItems.SEARED_COBBLESTONE.get(), recipe.result().getItem());
        assertTrue(recipe.consumesCast(), "the cobblestone is consumed by the pour");
        assertFalse(recipe.resultInInput());
        assertTrue(recipe.cast().isPresent(), "cast is the c:cobblestones/normal tag");
    }

    /**
     * Upstream {@code new CastingRecipe(searedGlass, RecipeMatch.of("blockGlass"),
     * TinkerFluids.searedStone, Material.VALUE_SearedMaterial * 4, true, true)}: plain glass in the
     * basin, consumed by the pour, comes out seared.
     */
    @Test
    void searedGlassCastsFromPlainGlassInTheBasin() {
        CastingRecipe recipe = shipped("seared_glass");

        assertEquals(CastingRecipe.Station.BASIN, recipe.station());
        assertEquals(288, recipe.amount(), "Material.VALUE_SearedMaterial * 4");
        assertEquals(ForgeweaveFluids.SEARED_STONE.still().get(), recipe.fluid());
        assertEquals(ForgeweaveItems.SEARED_GLASS.get(), recipe.result().getItem());
        assertTrue(recipe.consumesCast(), "the glass is consumed by the pour");
        assertTrue(recipe.cast().isPresent(), "cast is the c:glass_blocks/colorless tag");
    }

    // ------------------------------------------------------------------ #473 (T42 parity audit)

    /**
     * Upstream {@code TinkerRegistry.registerTableCasting(new CastingRecipe(new
     * ItemStack(Blocks.GLASS_PANE), null, TinkerFluids.glass, Material.VALUE_Glass * 6 / 16, 50))}:
     * an empty casting table under molten glass makes a vanilla glass pane, at an explicit 50-tick
     * cool rather than the derived one.
     */
    @Test
    void glassPaneCastsOnAnEmptyTable() {
        CastingRecipe recipe = shipped("glass_pane");

        assertEquals(CastingRecipe.Station.TABLE, recipe.station());
        assertEquals(375, recipe.amount(), "Material.VALUE_Glass * 6 / 16");
        assertEquals(ForgeweaveFluids.GLASS.still().get(), recipe.fluid());
        assertEquals(Items.GLASS_PANE, recipe.result().getItem());
        assertEquals(50, recipe.time().orElseThrow(), "upstream's explicit 50-tick cool");
        assertTrue(recipe.cast().isEmpty(), "the table has to be empty");
        assertFalse(recipe.consumesCast());
    }

    /**
     * Upstream {@code TinkerRegistry.registerBasinCasting(new CastingRecipe(new
     * ItemStack(TinkerCommons.blockClearGlass), null, TinkerFluids.glass, Material.VALUE_Glass,
     * 120))}: an empty basin under molten glass makes clear glass, at an explicit 120-tick cool.
     */
    @Test
    void clearGlassCastsInAnEmptyBasin() {
        CastingRecipe recipe = shipped("clear_glass");

        assertEquals(CastingRecipe.Station.BASIN, recipe.station());
        assertEquals(1000, recipe.amount(), "Material.VALUE_Glass");
        assertEquals(ForgeweaveFluids.GLASS.still().get(), recipe.fluid());
        assertEquals(ForgeweaveItems.CLEAR_GLASS.get(), recipe.result().getItem());
        assertEquals(120, recipe.time().orElseThrow(), "upstream's explicit 120-tick cool");
        assertTrue(recipe.cast().isEmpty(), "the basin has to be empty");
        assertFalse(recipe.consumesCast());
    }

    // ------------------------------------------------------------------ helpers

    private static CastingRecipe parse(String json) {
        return CastingRecipe.CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow();
    }

    private static CastingRecipe shipped(String name) {
        String path = "/data/forgeweave/forgeweave/casting_recipe/" + name + ".json";
        try (InputStream in = CastingRecipeTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing shipped casting recipe: " + path);
            return CastingRecipe.CODEC
                    .parse(ops, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .getOrThrow();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
