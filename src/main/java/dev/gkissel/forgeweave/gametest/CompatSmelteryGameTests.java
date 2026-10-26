package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

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
import dev.gkissel.forgeweave.item.ForgeweaveCreativeTab;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialForms;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;
import dev.gkissel.forgeweave.recipe.EntityMeltingRecipe;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Issue #873 (M6 epic #824's JC3 reversal): every compat metal now gets a molten fluid + bucket, and
 * melting/casting recipes carrying the same {@code neoforge:conditions} as its material. Two paths:
 *
 * <ul>
 *   <li>The <b>negative</b> path is proven directly against a real, shipped compat metal (bronze):
 *       neither {@code mekanism} nor {@code immersiveengineering} is a build/test dependency (see
 *       {@code SteelAndTagGatedGameTests}'s own javadoc), so bronze's molten fluid still registers
 *       (the NeoForge platform constraint every fluid in {@link ForgeweaveFluids} lives under) but its
 *       bucket is unlisted and its melting/casting recipes never load.
 *   <li>The <b>positive</b> round trip cannot use a real compat metal for the same reason
 *       {@code ConditionalMaterialGameTests} cannot -- a GameTest server can fake a {@code c:} tag but
 *       not a modid or another mod's item id (docs/research/m6-material-expansion-references.md
 *       &sect;1.4). It reuses that class's gametest-fixture-provider approach (issue #826/#853): a
 *       modid-blind {@code item_exists minecraft:diamond} condition, this time on a melting/casting
 *       recipe pair (see {@code src/gametest/resources/README.md}) riding one of this issue's own real
 *       fluids, {@code forgeweave:molten_bronze} -- proof the mechanism itself (a compat fluid's
 *       recipes gate open and round-trip an item) works, independent of any specific provider mod.
 * </ul>
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CompatSmelteryGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    /** Checklist line 9's materials, each gated on an item from a mod this server does not load. */
    private static final List<String> UNSUPPLIED_MATERIALS = List.of(
            "zinc", "brass", "andesite_alloy", "rose_quartz",
            "infused_alloy", "reinforced_alloy", "atomic_alloy",
            "hellforged");

    /**
     * The negative path: bronze's molten fluid exists (unconditional Java registration) but is
     * unobtainable and unlisted, and neither of its real melting/casting recipes registered. The
     * bucket half reads the Smeltery tab, the one tab that lists buckets. It used to read the General
     * tab, which never lists one, so that half could not fail.
     */
    @GameTest(template = "empty")
    public static void absentProviderHidesTheCompatBucketAndItsRecipes(GameTestHelper helper) {
        Fluid bronze = ForgeweaveFluids.BRONZE.still().get();
        helper.assertTrue(bronze != null, "expected forgeweave:molten_bronze to be registered unconditionally");

        boolean bronzeBucketShown = smelteryTabItems(helper).stream()
                .anyMatch(stack -> stack.is(ForgeweaveFluids.BRONZE.bucket().get()));
        helper.assertTrue(!bronzeBucketShown,
                "expected the bronze bucket to be hidden from the creative tab without mekanism/immersiveengineering");

        Registry<MeltingRecipe> melting = helper.getLevel().registryAccess().registryOrThrow(MeltingRecipe.REGISTRY);
        ResourceKey<MeltingRecipe> meltingKey = ResourceKey.create(MeltingRecipe.REGISTRY, id("bronze_ingot"));
        helper.assertTrue(melting.get(meltingKey) == null,
                "expected bronze_ingot's melting recipe to be absent (mekanism:ingot_bronze condition unmet)");

        Registry<CastingRecipe> casting = helper.getLevel().registryAccess().registryOrThrow(CastingRecipe.REGISTRY);
        ResourceKey<CastingRecipe> castingKey = ResourceKey.create(CastingRecipe.REGISTRY, id("ingot_bronze"));
        helper.assertTrue(casting.get(castingKey) == null,
                "expected ingot_bronze's casting recipe to be absent (mekanism:ingot_bronze condition unmet)");

        helper.succeed();
    }

    /**
     * Playtest checklist 1.0.0, line 9: with none of their mods present, the Create four (zinc,
     * brass, andesite alloy, rose quartz), Mekanism's three alloys (infused, reinforced, atomic) and
     * Neo Vitae's hellforged are not in the material registry, and no melting, casting or alloy row
     * of theirs loaded. A row counts as theirs when its id names the material, or when it pours or
     * makes the material's own molten fluid. Each material JSON is gated on an item from its
     * provider, for example {@code neoforge:item_exists mekanism:alloy_infused}.
     */
    @GameTest(template = "empty")
    public static void unsuppliedCompatMaterialsAndTheirSmelteryRowsDoNotLoad(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Registry<Material> materials = registries.registryOrThrow(Material.REGISTRY);
        for (String material : UNSUPPLIED_MATERIALS) {
            helper.assertTrue(materials.get(id(material)) == null,
                    "expected the " + material + " material to be absent without its mod, found it registered");

            ForgeweaveFluids.MoltenMetal molten = ForgeweaveFluids.compatMetalFluid(material);
            Fluid fluid = molten == null ? null : molten.still().get();
            List<String> loaded = new ArrayList<>();
            for (var entry : registries.registryOrThrow(MeltingRecipe.REGISTRY).entrySet()) {
                if (namesMaterial(entry.getKey().location(), material) || entry.getValue().fluid() == fluid) {
                    loaded.add("melting " + entry.getKey().location());
                }
            }
            for (var entry : registries.registryOrThrow(CastingRecipe.REGISTRY).entrySet()) {
                if (namesMaterial(entry.getKey().location(), material)
                        || (fluid != null && entry.getValue().fluid().orElse(null) == fluid)) {
                    loaded.add("casting " + entry.getKey().location());
                }
            }
            for (var entry : registries.registryOrThrow(AlloyRecipe.REGISTRY).entrySet()) {
                if (namesMaterial(entry.getKey().location(), material)
                        || (fluid != null && entry.getValue().result().getFluid() == fluid)) {
                    loaded.add("alloy " + entry.getKey().location());
                }
            }
            helper.assertTrue(loaded.isEmpty(), "expected no smeltery rows for " + material + ", found " + loaded);
        }
        helper.succeed();
    }

    /**
     * Playtest checklist 1.0.0, line 10: the only buckets the Smeltery tab shows without their mod
     * are the three known ones, molten ferricore, blazegold and eclipsealloy (#1059, #1062). The
     * sweep reads the real tab contents and keeps every bucket whose fluid nothing loaded can make:
     * no melting row (main fluid or byproduct), no alloy row and no entity melting row outputs it,
     * and it is not made some other way (blood, the default for any entity, and seared stone, which
     * stone tool parts melt into, both come from code, and blue slime pools in slime island lakes).
     * Any other bucket in that set shows without its owning mod, which is a defect.
     */
    @GameTest(template = "empty")
    public static void onlyTheThreeKnownBucketsShowWithoutAProvider(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Set<Fluid> producible = new HashSet<>();
        producible.add(ForgeweaveFluids.BLOOD.still().get());
        producible.add(ForgeweaveFluids.SEARED_STONE.still().get());
        // No recipe makes blue slime: it pools in the blue slime island lakes (SlimeIslandShape),
        // so a player buckets it from the world.
        producible.add(ForgeweaveFluids.BLUE_SLIME.still().get());
        for (MeltingRecipe recipe : registries.registryOrThrow(MeltingRecipe.REGISTRY)) {
            producible.add(recipe.fluid());
            recipe.byproduct().ifPresent(byproduct -> producible.add(byproduct.fluid()));
        }
        for (AlloyRecipe recipe : registries.registryOrThrow(AlloyRecipe.REGISTRY)) {
            producible.add(recipe.result().getFluid());
        }
        for (EntityMeltingRecipe recipe : registries.registryOrThrow(EntityMeltingRecipe.REGISTRY)) {
            producible.add(recipe.fluid());
        }

        Set<Item> providerless = new HashSet<>();
        for (ItemStack stack : smelteryTabItems(helper)) {
            if (stack.getItem() instanceof BucketItem bucket && !producible.contains(bucket.content)) {
                providerless.add(bucket);
            }
        }

        Set<Item> expected = Set.of(ForgeweaveFluids.FERRICORE.bucket().get(), ForgeweaveFluids.BLAZEGOLD.bucket().get(),
                ForgeweaveFluids.ECLIPSEALLOY.bucket().get());
        helper.assertTrue(providerless.equals(expected), "expected exactly the three known provider-less buckets "
                + names(expected) + ", found " + names(providerless));
        helper.succeed();
    }

    /**
     * Playtest checklist 1.0.0, line 11: without Mekanism, atomic matter alloy stays in the material
     * registry, but its ingot, nugget, block, every material form and its bucket are hidden from the
     * creative tabs. Nothing can make its ingot either: the Mekanism nucleosynthesizer row, the
     * ingot, nugget and block casting rows and the ingot's melting row all stayed unloaded.
     */
    @GameTest(template = "empty")
    public static void atomicMatterAlloyStaysRegisteredButItsItemFormsAreHidden(GameTestHelper helper) {
        String material = "atomic_matter_alloy";
        RegistryAccess registries = helper.getLevel().registryAccess();
        helper.assertTrue(registries.registryOrThrow(Material.REGISTRY).get(id(material)) != null,
                "expected atomic_matter_alloy to stay in the material registry without Mekanism");

        List<Item> forms = new ArrayList<>(List.of(ForgeweaveItems.trackBAlloyIngot(material).get(),
                ForgeweaveItems.trackBAlloyNugget(material).get(), ForgeweaveItems.trackBAlloyBlockItem(material).get(),
                ForgeweaveFluids.trackBAlloyFluid(material).bucket().get()));
        MaterialForms.FormedMaterial formed = MaterialForms.ALL.stream()
                .filter(entry -> entry.id().equals(material)).findFirst()
                .orElseThrow(() -> new AssertionError("expected atomic_matter_alloy to have material forms"));
        formed.forms().forEach(form -> forms.add(ForgeweaveItems.materialForm(material, form).get()));

        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.VANILLA_SET, true, registries);
        List<ItemStack> shown = new ArrayList<>(smelteryTabItems(helper));
        // The 3-argument overload, as ConditionalMaterialGameTests uses: the 2-argument one reads a
        // client config a GameTest server never loads.
        ForgeweaveCreativeTab.addGeneralItems(parameters, (stack, visibility) -> shown.add(stack), true);
        List<Item> leaked = forms.stream().filter(item -> shown.stream().anyMatch(stack -> stack.is(item))).toList();
        helper.assertTrue(leaked.isEmpty(), "expected every atomic_matter_alloy item form to be hidden, found " + leaked);

        helper.assertTrue(helper.getLevel().getRecipeManager()
                        .byKey(id("compat/mekanism/atomic_matter_alloy_ingot")).isEmpty(),
                "expected the nucleosynthesizer row to be absent without Mekanism");
        Registry<CastingRecipe> casting = registries.registryOrThrow(CastingRecipe.REGISTRY);
        for (String row : List.of("ingot_", "nugget_", "block_")) {
            helper.assertTrue(casting.get(id(row + material)) == null,
                    "expected the " + row + material + " casting row to be absent without Mekanism");
        }
        helper.assertTrue(registries.registryOrThrow(MeltingRecipe.REGISTRY).get(id(material + "_ingot")) == null,
                "expected the atomic_matter_alloy_ingot melting row to be absent without Mekanism");
        helper.succeed();
    }

    /** The Forgeweave Smeltery creative tab's real contents, built the way the client builds them. */
    private static Collection<ItemStack> smelteryTabItems(GameTestHelper helper) {
        CreativeModeTab tab = ForgeweaveCreativeTab.SMELTERY.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.VANILLA_SET, true, helper.getLevel().registryAccess()));
        return tab.getDisplayItems();
    }

    /** Whether {@code id}'s path holds {@code material} as a whole run of underscore-separated words. */
    private static boolean namesMaterial(ResourceLocation id, String material) {
        return ("_" + id.getPath().replace('/', '_') + "_").contains("_" + material + "_");
    }

    private static List<String> names(Set<Item> items) {
        return items.stream().map(item -> BuiltInRegistries.ITEM.getKey(item).toString()).sorted().toList();
    }

    /**
     * The positive melting half, via the gametest fixture provider (src/gametest/resources/README.md):
     * the tag-planted stand-in ingot (minecraft:nether_brick under c:ingots/bronze) melts, live in a
     * lava-fuelled smeltery, into 144 mB of molten_bronze -- proof {@code
     * gametest_compat_smeltery_present.json}'s condition gated open and {@code MeltingRecipe#find}
     * actually resolves it, not just that the JSON parses.
     */
    @GameTest(template = "smeltery", timeoutTicks = 3200)
    public static void fixtureRecipeMeltsTheStandInIngot(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SearedTankBlockEntity wallTank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        wallTank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());

        helper.assertTrue(core.insertForMelting(new ItemStack(Items.NETHER_BRICK)).isEmpty(),
                "expected the stand-in ingot to go into the smeltery");

        Fluid bronze = ForgeweaveFluids.BRONZE.still().get();
        helper.succeedWhen(() -> {
            helper.assertTrue(core.tank().getFluid().getFluid() == bronze,
                    "expected molten_bronze, tank holds " + core.tank().getFluid().getFluid());
            helper.assertValueEqual(core.tank().getFluidAmount(), 144, "molten bronze in the tank");
        });
    }

    /**
     * The positive casting half: molten_bronze (poured directly, same isolation
     * {@code TrackBAlloyGameTests}' own casting-only tests use so this does not re-prove melting)
     * casts back out as a gold ingot at a casting table -- proof {@code
     * gametest_compat_smeltery_present.json}'s casting row gated open too.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void fixtureRecipeCastsBackToAGoldIngot(GameTestHelper helper) {
        Fluid bronze = ForgeweaveFluids.BRONZE.still().get();

        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(bronze, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.WEST));
        CastingBlockEntity table = helper.getBlockEntity(CASTING);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        table.interact(player, InteractionHand.MAIN_HAND);

        helper.<FaucetBlockEntity>getBlockEntity(FAUCET).activate();

        helper.succeedWhen(() -> helper.assertTrue(table.output().is(Items.GOLD_INGOT),
                "expected the fixture casting recipe to produce a gold ingot, found " + table.output()));
    }

    /**
     * Issue #954: a diamond-tier Track A metal's melting temperature (1400, the table's
     * {@code minecraft:incorrect_for_diamond_tool} bucket) sits strictly between lava (1300) and
     * blazing blood (1500) -- {@code gametest_track_a_diamond_tier.json} rides the same modid-blind
     * {@code item_exists minecraft:diamond} condition {@code gametest_compat_smeltery_present.json}
     * uses above, so this exercises a real <em>conditioned</em> recipe rather than one of {@code
     * SmelteryFuelGameTests}'s unconditional ladder fixtures. Both halves run on one structure so the
     * negative half can never pass vacuously.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void diamondTierTrackAMetalFailsUnderLavaMeltsUnderBlazingBlood(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        Fluid platinum = ForgeweaveFluids.PLATINUM.still().get();
        assertCannotMeltFixture(helper, Fluids.LAVA, Items.PRISMARINE_SHARD, "lava (1300)");
        assertMeltsFixtureTo(helper, ForgeweaveFluids.BLAZING_BLOOD.still().get(), 1500, Items.PRISMARINE_SHARD,
                platinum, "blazing blood");
        helper.succeed();
    }

    /**
     * Issue #954: awakened draconium's melting temperature (1800, the draconic override above the
     * general netherite-tier rule) sits strictly between molten magma (1700) and brimspar (1900) --
     * same conditioned-fixture shape as the diamond tier test above.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void awakenedDraconiumFailsUnderMagmaMeltsUnderBrimspar(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        Fluid draconiumAwakened = ForgeweaveFluids.DRACONIUM_AWAKENED.still().get();
        assertCannotMeltFixture(helper, ForgeweaveFluids.MOLTEN_MAGMA.still().get(), Items.SHULKER_SHELL, "molten magma (1700)");
        assertMeltsFixtureTo(helper, ForgeweaveFluids.BRIMSPAR.still().get(), 1900, Items.SHULKER_SHELL,
                draconiumAwakened, "brimspar");
        helper.succeed();
    }

    /**
     * The colder half of a #954 boundary check: the core is torn down and re-placed on the caller's
     * existing walls with only {@code colder} in the wall tank (a block entity carries its
     * in-progress burn across a wall-tank swap, the same reason {@code SmelteryFuelGameTests}'s own
     * {@code refuelledSmeltery} tears down between runs), and makes no melt progress at all on
     * {@code fixture}.
     */
    private static void assertCannotMeltFixture(GameTestHelper helper, Fluid colder, Item fixture, String fuelName) {
        SmelteryControllerBlockEntity core = refuelledSmeltery(helper, colder);
        helper.assertTrue(core.insertForMelting(new ItemStack(fixture)).isEmpty(),
                "expected the fixture item to go into the smeltery");
        helper.assertTrue(!core.meltTick(), fuelName + " must not heat this Track A fixture recipe");
        helper.assertTrue(core.meltProgress(0) == 0f, "expected no melt progress at all under " + fuelName);
    }

    /**
     * The hotter half: the core is re-placed with {@code hotter} alone in the wall tank and melts
     * {@code fixture} through to {@code expectedFluid} at the fixture recipe's 144 mB.
     */
    private static void assertMeltsFixtureTo(GameTestHelper helper, Fluid hotter, int expectedTemperature,
            Item fixture, Fluid expectedFluid, String fuelName) {
        SmelteryControllerBlockEntity core = refuelledSmeltery(helper, hotter);
        helper.assertValueEqual(core.currentTemperature(), expectedTemperature,
                "smeltery temperature with " + fuelName + " in the wall tank");
        helper.assertTrue(core.insertForMelting(new ItemStack(fixture)).isEmpty(),
                "expected the fixture item to go into the smeltery");

        while (core.meltProgress(0) < 1.0f) {
            helper.assertTrue(core.meltTick(), "expected " + fuelName + " to keep heating the fixture recipe");
        }
        core.meltTick(); // finish-only tick.

        helper.assertValueEqual(core.tank().getFluidAmount(), 144, "molten output from the fixture recipe");
        helper.assertTrue(core.tank().getFluid().getFluid() == expectedFluid,
                "expected " + expectedFluid + ", the rung below can never reach this recipe");
    }

    /**
     * Re-places the core on the caller's walls with {@code fuel} alone in the wall tank. Mirrors
     * {@code SmelteryFuelGameTests}'s own private helper of the same shape.
     */
    private static SmelteryControllerBlockEntity refuelledSmeltery(GameTestHelper helper, Fluid fuel) {
        helper.setBlock(SmelteryGameTests.CORE_POS, Blocks.AIR);

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
        tank.tank().fill(new FluidStack(fuel, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private CompatSmelteryGameTests() {}
}
