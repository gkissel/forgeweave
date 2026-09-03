package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.CastingBlockEntity;
import dev.gkissel.forgeweave.block.FaucetBlock;
import dev.gkissel.forgeweave.block.FaucetBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.CompatMaterialAvailability;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;
import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * Issue #840 (epic #824's Track B): the molten fluids, melting/casting rows and alloy table for the
 * 12 ore-sourced metals ({@link TrackBOre}), the 18 alloy tool materials ({@link TrackBAlloy}) and the
 * 6 smeltery-only catalysts this class's {@link #CATALYST_SOURCE_ITEMS} mirrors from {@code
 * scripts/generate_track_b_recipes.py}. Follows two shapes already established in this package:
 * {@code SmelteryAlloyGameTests}' direct tank-fill for exercising the alloy table live, and {@code
 * TrackBOreGameTests}' "walk the roster against the registry" shape for the melting/casting rows --
 * re-proving the generic core-tier-multiplier and casting-table mechanics per new material would just
 * re-test {@code SmelteryMeltingGameTests}/{@code CastingGameTests} rather than this issue's own risk
 * surface (did every one of the ~2350 generated JSON rows come out right).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class TrackBAlloyGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    /**
     * Mirrors scripts/generate_track_b_recipes.py's CATALYSTS table (id -&gt; source item). Six rows
     * since #910 retired twinalloy: brimspar took over its alloy-input role, and brimspar melts from
     * its own ore's crystals rather than a vanilla item, so its melting row is
     * {@code SmelteryFuelGameTests}' to cover, not this table's.
     */
    private static final Map<String, String> CATALYST_SOURCE_ITEMS = Map.of(
            "flarealloy", "minecraft:blaze_powder",
            "deepalloy", "minecraft:echo_shard",
            "sparkalloy", "minecraft:glowstone_dust",
            "redcinder", "minecraft:redstone",
            "pearlcinder", "minecraft:ender_pearl",
            "ambercinder", "minecraft:honeycomb");

    private record Input(String id, int amount) {}

    /**
     * Mirrors scripts/generate_track_b_recipes.py's ALLOY_RECIPES table: output id, the recipe file's
     * suffix ({@code ""} for the primary recipe, {@code "_altN"} for an alternative), its inputs and
     * the output amount. 21 rows for the 18 outputs (3 with an alternate recipe).
     */
    private record AlloyCase(String outputId, String suffix, List<Input> inputs, int outputAmount) {}

    private static Input in(String id, int amount) {
        return new Input(id, amount);
    }

    private static final List<AlloyCase> ALLOY_CASES = List.of(
            new AlloyCase("ironbrand", "", List.of(in("redcinder", 32), in("pearlcinder", 32), in("ambercinder", 32)), 72),
            // Issue #884 (1): quakestone's cinderstone input was replaced with basalt (same amount).
            new AlloyCase("quakestone", "", List.of(in("fulmenite", 144), in("basalt", 144)), 144),
            // Issue #910: twinalloy merged into brimspar (#903's mined fuel), same 32 mB amounts here
            // and in both glowveil rows below.
            new AlloyCase("quakestone", "_alt1", List.of(in("fulmenite", 144), in("brimspar", 32)), 144),
            new AlloyCase("embercast", "", List.of(in("duskspar", 144), in("ardite", 144)), 144),
            new AlloyCase("riftalloy", "", List.of(in("murkiron", 144), in("nightshale", 144), in("voltcinder", 144)), 216),
            new AlloyCase("dreadalloy", "", List.of(in("hardcinder", 144), in("murkiron", 144), in("deepalloy", 32)), 144),
            new AlloyCase("mendalloy", "", List.of(in("nightshale", 144), in("hardcinder", 144), in("flarealloy", 32)), 144),
            new AlloyCase("mendstone", "", List.of(in("hollowstone", 144), in("warspar", 144), in("flarealloy", 32)), 144),
            new AlloyCase("mendstone", "_alt1", List.of(in("hollowstone", 144), in("warspar", 144), in("deepalloy", 32)), 144),
            new AlloyCase("tideiron", "", List.of(in("cobalt", 144), in("ironbrand", 72)), 144),
            new AlloyCase("cinderforge", "", List.of(in("ardite", 144), in("ironbrand", 72), in("flarealloy", 32)), 144),
            new AlloyCase("skipalloy", "", List.of(in("ironbrand", 72), in("duskspar", 144)), 144),
            new AlloyCase("daybrass", "", List.of(in("nightshale", 144), in("ironbrand", 72)), 144),
            new AlloyCase("faultsteel", "", List.of(in("obsidian", 144), in("quakestone", 144), in("voltcinder", 144)), 216),
            new AlloyCase("shardline", "", List.of(in("quakestone", 144), in("obsidian", 144), in("deepalloy", 32)), 144),
            new AlloyCase("glowveil", "", List.of(in("riftalloy", 216), in("sparkalloy", 32), in("brimspar", 32)), 216),
            new AlloyCase("glowveil", "_alt1", List.of(in("dreadalloy", 144), in("sparkalloy", 32), in("brimspar", 32)), 144),
            new AlloyCase("sunsteel", "", List.of(in("warspar", 144), in("hollowstone", 144), in("glowveil", 144)), 216),
            new AlloyCase("hollowsteel", "", List.of(in("resonite", 144), in("sunsteel", 216)), 216),
            // truesteel's inputs are a superset of hollowsteel's (both start from resonite + sunsteel):
            // pouring sparkalloy last would let resonite + sunsteel alone match hollowsteel's shorter
            // recipe the moment the second of the two lands, before sparkalloy ever arrives -- the
            // smeltery alloys on every fill, not once per batch. Pouring the catalyst first sidesteps
            // that; truesteel's priority -1 (alloy_recipe/truesteel.json) is what then lets it win over
            // hollowsteel once all three are actually in the tank together.
            new AlloyCase("truesteel", "", List.of(in("sparkalloy", 32), in("resonite", 144), in("sunsteel", 216)), 216),
            new AlloyCase("stormalloy", "", List.of(in("quakestone", 144), in("shardline", 144), in("faultsteel", 216)), 288));

    /** Every {@code alloy_recipe/*.json} this issue ships alloys live in a smeltery tank exactly as generated. */
    @GameTest(template = "smeltery")
    public static void everyGeneratedAlloyRecipeProducesItsStatedRatio(GameTestHelper helper) {
        for (AlloyCase alloyCase : ALLOY_CASES) {
            SmelteryControllerBlockEntity core = smeltery(helper);
            for (Input input : alloyCase.inputs()) {
                pour(core, fluidByMaterialId(input.id()), input.amount());
            }
            Fluid expected = ForgeweaveFluids.trackBAlloyFluid(alloyCase.outputId()).still().get();
            helper.assertTrue(core.tank().getFluid().getFluid() == expected,
                    alloyCase.outputId() + alloyCase.suffix() + ": expected " + expected
                            + " but the tank holds " + core.tank().getFluid().getFluid());
            helper.assertValueEqual(core.tank().getFluidAmount(), alloyCase.outputAmount(),
                    alloyCase.outputId() + alloyCase.suffix() + " in the tank");
        }
        helper.succeed();
    }

    /** Ore-metal, existing-base-metal or catalyst id -&gt; its still molten fluid. */
    private static Fluid fluidByMaterialId(String id) {
        var ore = ForgeweaveFluids.trackBOreFluid(id);
        if (ore != null) {
            return ore.still().get();
        }
        var alloy = ForgeweaveFluids.trackBAlloyFluid(id);
        if (alloy != null) {
            return alloy.still().get();
        }
        return switch (id) {
            case "cobalt" -> ForgeweaveFluids.COBALT.still().get();
            case "ardite" -> ForgeweaveFluids.ARDITE.still().get();
            case "obsidian" -> ForgeweaveFluids.OBSIDIAN.still().get();
            // Issue #884 (1): basalt replaces cinderstone as a standalone (non-TrackBOre) fluid.
            case "basalt" -> ForgeweaveFluids.BASALT.still().get();
            case "flarealloy" -> ForgeweaveFluids.FLAREALLOY.still().get();
            case "deepalloy" -> ForgeweaveFluids.DEEPALLOY.still().get();
            case "sparkalloy" -> ForgeweaveFluids.SPARKALLOY.still().get();
            case "redcinder" -> ForgeweaveFluids.REDCINDER.still().get();
            case "pearlcinder" -> ForgeweaveFluids.PEARLCINDER.still().get();
            case "ambercinder" -> ForgeweaveFluids.AMBERCINDER.still().get();
            // #910: twinalloy merged into brimspar, which is both a fuel and (from that issue on) an
            // alloy input -- the only fluid in this switch that is fed by an ore rather than an item.
            case "brimspar" -> ForgeweaveFluids.BRIMSPAR.still().get();
            default -> throw new IllegalArgumentException("unknown alloy input id " + id);
        };
    }

    /**
     * Every ore metal's ore/raw melting rows: ore-class ({@code ore: true}), base amount 144 mB --
     * except fulmenite ({@link TrackBOre#dropsCrystal}), whose ore block has no melting row of its own
     * (mirroring brimspar, #903): only its crystal melts, not ore-class, at the same base amount
     * (#929, {@code melting_recipe/fulmenite_crystal.json}).
     */
    @GameTest(template = "empty")
    public static void everyTrackBOreOreAndRawMeltAsOreClassAtBaseAmount(GameTestHelper helper) {
        for (TrackBOre ore : TrackBOre.ALL) {
            Fluid fluid = ForgeweaveFluids.trackBOreFluid(ore.id()).still().get();
            if (ore.dropsCrystal()) {
                assertMelting(helper, ore.crystalItemId(), fluid, MeltingRecipe.VALUE_INGOT, false);
            } else {
                assertMelting(helper, ore.oreBlockId(), fluid, MeltingRecipe.VALUE_INGOT, true);
                assertMelting(helper, "raw_" + ore.id(), fluid, MeltingRecipe.VALUE_INGOT, true);
            }
        }
        helper.succeed();
    }

    /** Every ore metal's ingot/nugget/block melting rows: not ore-class, fixed amounts. */
    @GameTest(template = "empty")
    public static void everyTrackBOreIngotNuggetBlockMeltAtFixedAmounts(GameTestHelper helper) {
        for (TrackBOre ore : TrackBOre.ALL) {
            Fluid fluid = ForgeweaveFluids.trackBOreFluid(ore.id()).still().get();
            assertMelting(helper, ore.ingotId(), fluid, MeltingRecipe.VALUE_INGOT, false);
            assertMelting(helper, ore.nuggetId(), fluid, MeltingRecipe.VALUE_NUGGET, false);
            assertMelting(helper, ore.storageBlockId(), fluid, MeltingRecipe.VALUE_BLOCK, false);
        }
        helper.succeed();
    }

    /** Every alloy's ingot/nugget/block melting rows: alloy-only, so no ore/raw form to check. */
    @GameTest(template = "empty")
    public static void everyTrackBAlloyIngotNuggetBlockMeltAtFixedAmounts(GameTestHelper helper) {
        for (TrackBAlloy alloy : TrackBAlloy.ALL) {
            // #873 -- alumite/osgloglas/osmiridium (added to TrackBAlloy.ALL) carry the same
            // `neoforge:conditions` gate their alloy_recipe/material do; none of their compat inputs'
            // provider mods is a build/test dependency, so their melting recipes correctly do not
            // register in this GameTest server. CompatCastOnlyGameTests covers their gated shape
            // directly (positive path with the gametest fixture provider, negative path here).
            if (!CompatMaterialAvailability.isAvailable(alloy.id())) {
                continue;
            }
            Fluid fluid = ForgeweaveFluids.trackBAlloyFluid(alloy.id()).still().get();
            assertMelting(helper, alloy.ingotId(), fluid, MeltingRecipe.VALUE_INGOT, false);
            assertMelting(helper, alloy.nuggetId(), fluid, MeltingRecipe.VALUE_NUGGET, false);
            assertMelting(helper, alloy.blockId(), fluid, MeltingRecipe.VALUE_BLOCK, false);
        }
        helper.succeed();
    }

    /** Every catalyst melts from its common vanilla source item -- deliverable 5's "no Material entry" branch. */
    @GameTest(template = "empty")
    public static void everyCatalystMeltsFromItsVanillaSourceItem(GameTestHelper helper) {
        Registry<MeltingRecipe> recipes = helper.getLevel().registryAccess().registryOrThrow(MeltingRecipe.REGISTRY);
        CATALYST_SOURCE_ITEMS.forEach((catalystId, itemId) -> {
            MeltingRecipe recipe = recipes.get(recipeKey(catalystId));
            helper.assertTrue(recipe != null, "expected a melting recipe registered as " + catalystId);
            ItemStack sourceItem = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)));
            helper.assertTrue(recipe.input().test(sourceItem), catalystId + "'s melting recipe must accept " + itemId);
            helper.assertTrue(recipe.fluid() == fluidByMaterialId(catalystId),
                    catalystId + "'s melting recipe must yield molten_" + catalystId);
        });
        helper.succeed();
    }

    /** Every generated {@code casting_recipe} row for one representative ore (fulmenite -- issue
     * #884 (1) retired the previous representative, cinderstone) and one alloy (hollowsteel)
     * resolves against the registry with the right fluid -- proof the 73-row per-material template
     * clone (scripts/generate_track_b_recipes.py) actually landed, without running all 2190 rows
     * live. */
    @GameTest(template = "empty")
    public static void theFullPartRosterIsRegisteredForARepresentativeOreAndAlloy(GameTestHelper helper) {
        assertFullCastingRoster(helper, "fulmenite", ForgeweaveFluids.trackBOreFluid("fulmenite").still().get());
        assertFullCastingRoster(helper, "hollowsteel", ForgeweaveFluids.trackBAlloyFluid("hollowsteel").still().get());
        helper.succeed();
    }

    /** Fulmenite (ore metal) -&gt; its ingot casts from the molten fluid, live. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void fulmeniteCastsIntoAnIngot(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.trackBOreFluid("fulmenite").still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> helper.assertTrue(table.output().is(ForgeweaveItems.trackBIngot("fulmenite").get()),
                "expected a fulmenite ingot in the output slot, found " + table.output()));
    }

    /** Hollowsteel (alloy) -&gt; a pickaxe head casts from the molten fluid, live. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void hollowsteelCastsAPickaxeHead(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.trackBAlloyFluid("hollowsteel").still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                    "expected a finished pickaxe head in the output slot, found " + table.output());
            helper.assertTrue(materialId("hollowsteel").equals(table.output().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the part to come out in hollowsteel, got "
                            + table.output().get(ForgeweaveDataComponents.MATERIAL.get()));
        });
    }

    // ------------------------------------------------------------------ helpers

    private static ResourceKey<MeltingRecipe> recipeKey(String name) {
        return ResourceKey.create(MeltingRecipe.REGISTRY, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name));
    }

    private static void assertMelting(GameTestHelper helper, String recipeName, Fluid fluid, int amount, boolean ore) {
        Registry<MeltingRecipe> recipes = helper.getLevel().registryAccess().registryOrThrow(MeltingRecipe.REGISTRY);
        MeltingRecipe recipe = recipes.get(recipeKey(recipeName));
        helper.assertTrue(recipe != null, "expected a melting recipe registered as " + recipeName);
        helper.assertTrue(recipe.fluid() == fluid, recipeName + ": expected " + fluid + ", got " + recipe.fluid());
        helper.assertValueEqual(recipe.amount(), amount, recipeName + "'s amount");
        helper.assertValueEqual(recipe.ore(), ore, recipeName + "'s ore flag");
    }

    private static void assertFullCastingRoster(GameTestHelper helper, String materialId, Fluid fluid) {
        Registry<CastingRecipe> recipes = helper.getLevel().registryAccess().registryOrThrow(CastingRecipe.REGISTRY);
        List<String> parts = List.of("pickaxe_head", "sword_blade", "axe_head", "shovel_head", "hammer_head",
                "excavator_head", "broad_axe_head", "war_mace_head", "vein_hammer_head", "kama_head",
                "scythe_head", "katana_blade", "knife_blade", "curved_blade", "large_sword_blade",
                "tool_handle", "tough_tool_rod", "tool_binding", "tough_binding", "cross_guard",
                "hand_guard", "wide_guard", "bow_limb", "large_plate", "sign_plate", "pan",
                "sharpening_kit", "shard", "maille", "plating_helmet", "plating_chestplate",
                "plating_leggings", "plating_boots");
        for (String part : parts) {
            assertCasting(helper, recipes, part + "_" + materialId, fluid);
            assertCasting(helper, recipes, "clay_" + part + "_" + materialId, fluid);
        }
        assertCasting(helper, recipes, "ingot_" + materialId, fluid);
        assertCasting(helper, recipes, "clay_ingot_" + materialId, fluid);
        assertCasting(helper, recipes, "nugget_" + materialId, fluid);
        assertCasting(helper, recipes, "clay_nugget_" + materialId, fluid);
        assertCasting(helper, recipes, "block_" + materialId, fluid);
    }

    private static void assertCasting(GameTestHelper helper, Registry<CastingRecipe> recipes, String name, Fluid fluid) {
        ResourceKey<CastingRecipe> key = ResourceKey.create(CastingRecipe.REGISTRY,
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name));
        CastingRecipe recipe = recipes.get(key);
        helper.assertTrue(recipe != null, "expected a casting recipe registered as " + name);
        helper.assertTrue(recipe.fluid().isPresent() && recipe.fluid().get() == fluid,
                name + ": expected fluid " + fluid + ", got " + recipe.fluid());
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    /** The 1x1x2 minimum smeltery of {@code SmelteryGameTests} with a Standard Core, formed and empty. */
    private static SmelteryControllerBlockEntity smeltery(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    private static void pour(SmelteryControllerBlockEntity core, Fluid fluid, int amount) {
        core.tank().fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    /** A tank of {@code fluid}, a faucet on its east side pointing back at it, and a casting table below. */
    private static CastingBlockEntity rig(GameTestHelper helper, Fluid fluid) {
        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(fluid, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.WEST));
        return helper.getBlockEntity(CASTING);
    }

    private static FaucetBlockEntity faucet(GameTestHelper helper) {
        return helper.getBlockEntity(FAUCET);
    }

    private static void insert(GameTestHelper helper, CastingBlockEntity casting, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        casting.interact(player, InteractionHand.MAIN_HAND);
    }
}
