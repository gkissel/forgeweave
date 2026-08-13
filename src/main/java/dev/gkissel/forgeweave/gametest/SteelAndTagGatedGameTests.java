package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
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
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Covers issue #234's verification: steel as an FW-native metal (carbon alloy ratio, casting, the
 * clone's exact material stats) and the four tag-gated compat metals (bronze/lead/silver/electrum)
 * gated both ways -- with a synthetic {@code c:ingots/bronze} entry supplied by the GameTest-only
 * datapack (see src/gametest/resources/README.md) the Part Builder crafts bronze parts, and the
 * three metals whose tags nothing supplies stay unobtainable, which is docs/SCOPE.md M3.2's
 * intended upstream-ore-dict-parity gating.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SteelAndTagGatedGameTests {

    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    /**
     * The maintainer-proposed ratio on the issue #234 PR: 144 mB iron + 72 mB carbon -> 144 mB
     * steel, i.e. one iron ingot plus one coal's worth of carbon becomes one steel ingot --
     * iron-volume-preserving like rose gold's ratio, with the carbon consumed as the half-ingot
     * flux that turns it. Written in {@code alloy_recipe/steel.json} as minimal units (2:1 -> 2)
     * like every shipped alloy, so the remainder rule applies at the smallest step.
     */
    @GameTest(template = "smeltery")
    public static void ironAndCarbonAlloyIntoSteelAtTheProposedRatio(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.IRON.still().get(), MeltingRecipe.VALUE_INGOT);
        pour(core, ForgeweaveFluids.CARBON.still().get(), MeltingRecipe.VALUE_INGOT / 2);

        helper.assertValueEqual(core.tank().fluids().size(), 1, "distinct fluids left in the tank");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.STEEL.still().get(),
                "expected molten steel, the tank holds " + core.tank().getFluid().getFluid());
        helper.assertValueEqual(core.tank().getFluidAmount(), MeltingRecipe.VALUE_INGOT, "steel in the tank");
        helper.succeed();
    }

    /**
     * The whole chain from items, like {@code SmelteryAlloyGameTests}' rose gold end-to-end test:
     * one iron ingot and one coal melt ({@code iron_ingot.json} at 144 mB, {@code coal.json}'s
     * maintainer-picked 72 mB -- half an ingot-unit, exactly one alloy application), and the tank
     * alloys itself into one ingot of steel with nothing left over.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void anIronIngotAndACoalMeltAndAlloyIntoOneSteelIngot(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        helper.assertTrue(core.insertForMelting(new ItemStack(Items.IRON_INGOT)).isEmpty(), "expected the iron ingot to go in");
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.COAL)).isEmpty(), "expected the coal to go in");

        helper.succeedWhen(() -> {
            helper.assertValueEqual(core.tank().fluids().size(), 1, "distinct fluids left in the tank");
            helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.STEEL.still().get(),
                    "expected molten steel, the tank holds " + core.tank().getFluid().getFluid());
            helper.assertValueEqual(core.tank().getFluidAmount(), MeltingRecipe.VALUE_INGOT, "steel from one iron ingot + one coal");
        });
    }

    /** Steel's casting row set works like every other metal's: 144 mB over an ingot cast is a steel ingot, cast kept. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void steelCastsAnIngotAtTheTable(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.STEEL.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        helper.<FaucetBlockEntity>getBlockEntity(FAUCET).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.INGOT_STEEL.get()),
                    "expected a steel ingot in the output slot, found " + table.output());
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_INGOT.get()),
                    "expected the ingot cast to survive the pour");
        });
    }

    /**
     * Steel's clone-exact stat line (TinkerMaterials: 540/7.0/6.0 head, 0.9/+150 handle, 25 extra)
     * through a real Tool Station: durability = round((540 + 25) * 0.9f) + 150 = 658.
     */
    @GameTest(template = "empty")
    public static void steelToolCarriesTheClonesExactStats(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "steel", "steel", "steel");

        ToolStats.Stats stats = pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null, "an assembled tool must carry its computed stats");
        helper.assertTrue(stats.durability() == Math.round((540 + 25) * 0.9F) + 150,
                "expected round((540 + 25) * 0.9) + 150 durability, got " + stats.durability());
        helper.assertTrue(Math.abs(stats.miningSpeed() - 7.0F) < 0.001F,
                "expected steel's 7.0 mining speed, got " + stats.miningSpeed());
        helper.assertTrue(Math.abs(stats.attackDamage() - 6.0F) < 0.001F,
                "expected steel's 6.0 attack damage, got " + stats.attackDamage());
        helper.succeed();
    }

    /**
     * Tag-gating, lit side: the GameTest-only datapack plants {@code minecraft:nether_brick} in
     * {@code c:ingots/bronze}, standing in for a modded bronze ingot. Bronze's {@code
     * crafting_items} keys on that tag at value 2, so two of them cover a pickaxe head's cost of 4
     * and the Part Builder produces a bronze part -- no Forgeweave item, recipe, or code names
     * nether brick anywhere.
     */
    @GameTest(template = "empty")
    public static void aModSuppliedBronzeIngotTagLightsUpThePartBuilder(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openMenu(helper, pos, player);

        menu.getSlot(0).set(new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        menu.getSlot(1).set(new ItemStack(Items.NETHER_BRICK, 2));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(2).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected a pickaxe head part from two tag-supplied bronze ingots, got " + output);
        helper.assertTrue(materialId("bronze").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the part's material to be forgeweave:bronze, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));
        helper.succeed();
    }

    /**
     * Tag-gating, dark side: lead/silver/electrum load as materials (stats, traits, lang -- ready
     * for a supplying mod) but their {@code c:} ingot and storage-block tags are empty in a
     * Forgeweave-only install, so no item in existence pays their part cost and the Part Builder
     * ignores them -- the material is unobtainable by design, and an arbitrary untagged item still
     * crafts nothing.
     */
    @GameTest(template = "empty")
    public static void untaggedCompatMetalsStayUnobtainable(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : new String[] {"lead", "silver", "electrum"}) {
            Material material = materials.get(materialId(name));
            helper.assertTrue(material != null, "expected the " + name + " material to be registered");
            // What "unobtainable" means is that no item in the game passes the ingredient. The one
            // exclusion is vanilla's own dev-runtime artifact: Ingredient.TagValue pads an empty
            // tag with a barrier placeholder under SharedConstants.IS_RUNNING_IN_IDE (it even
            // test()s true against a real barrier there), which a production install never has.
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.BARRIER) {
                    continue;
                }
                for (Material.CraftingItem craftingItem : material.craftingItems()) {
                    helper.assertFalse(craftingItem.ingredient().test(new ItemStack(item)),
                            "expected " + name + "'s crafting tag to match nothing in a Forgeweave-only install, "
                                    + "but it matches " + item);
                }
            }
        }

        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openMenu(helper, pos, player);
        menu.getSlot(0).set(new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        menu.getSlot(1).set(new ItemStack(Items.GOLD_NUGGET, 2));
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(),
                "expected no part from an item no material's crafting_items names");
        helper.succeed();
    }

    /**
     * A tag-gated material is a full material once its parts exist (the gate is acquisition, not
     * behavior): a bronze tool assembled from parts carries the clone's exact stat line
     * (430/6.8/3.5 head, 1.1/+70 handle, 80 extra) -> round((430 + 80) * 1.1f) + 70 = 631.
     */
    @GameTest(template = "empty")
    public static void bronzeToolCarriesTheClonesExactStats(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "bronze", "bronze", "bronze");

        ToolStats.Stats stats = pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null, "an assembled tool must carry its computed stats");
        helper.assertTrue(stats.durability() == Math.round((430 + 80) * 1.1F) + 70,
                "expected round((430 + 80) * 1.1) + 70 durability, got " + stats.durability());
        helper.assertTrue(Math.abs(stats.miningSpeed() - 6.8F) < 0.001F,
                "expected bronze's 6.8 mining speed, got " + stats.miningSpeed());
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    /** The 1x1x2 minimum smeltery of {@link SmelteryGameTests} with a Standard Core, formed and empty. */
    private static SmelteryControllerBlockEntity smeltery(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    /** Puts fluid straight into the smeltery's tank, the way a faucet pouring through a drain would. */
    private static void pour(SmelteryControllerBlockEntity core, Fluid fluid, int amount) {
        core.tank().fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    /** A tank of {@code fluid}, a faucet on its east side, and a casting table below ({@code M3CastingGameTests#rig}). */
    private static CastingBlockEntity rig(GameTestHelper helper, Fluid fluid) {
        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(fluid, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.WEST));
        return helper.getBlockEntity(CASTING);
    }

    /** Puts {@code stack} in the casting table the way a player does -- through the real right-click path. */
    private static void insert(GameTestHelper helper, CastingBlockEntity casting, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        casting.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(casting.input().is(stack.getItem()), "expected the right-click to put the " + stack.getItem() + " in");
    }

    private static PartBuilderMenu openMenu(GameTestHelper helper, BlockPos pos, Player player) {
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos);
        return new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());
    }

    private SteelAndTagGatedGameTests() {}
}
