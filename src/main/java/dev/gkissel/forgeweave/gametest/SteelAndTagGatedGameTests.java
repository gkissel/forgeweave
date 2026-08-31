package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
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
 * clone's exact material stats).
 *
 * <p>The four compat metals (bronze/lead/silver/electrum) were originally gated here too, on
 * obtainability alone (docs/SCOPE.md M3.2: registered but unobtainable without a supplying mod's
 * {@code c:} ingot tag). Issue #826 (M6) reverses that: they now carry {@code neoforge:conditions}
 * existence gates keyed on a real provider's item id ({@code mekanism:ingot_bronze},
 * {@code immersiveengineering:ingot_lead}, ...), so without that mod loaded the material is not in
 * the registry at all -- not merely uncraftable. {@link #unsuppliedCompatMetalsDoNotExistAtAll}
 * covers that negative path directly: neither Mekanism nor Immersive Engineering is a build
 * dependency, so all four are absent in this GameTest server exactly as they would be in a
 * Forgeweave-only install. The positive existence path (and the tag-obtainability path layered on
 * top of it) can't be demonstrated with the real four -- a GameTest server can fake a {@code c:} tag
 * but not a modid or another mod's item id (docs/research/m6-material-expansion-references.md
 * &sect;1.4) -- so {@code ConditionalMaterialGameTests} covers it with a gametest-only conditional
 * material instead, reusing this class's synthetic {@code c:ingots/bronze} fixture for the
 * obtainability half.
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
     * Issue #826's negative existence path for the real four: neither {@code mekanism} nor
     * {@code immersiveengineering} is a build dependency (see build.gradle), so every one of
     * bronze/lead/silver/electrum's {@code neoforge:item_exists}/{@code neoforge:or} conditions
     * fails here exactly as it would in a Forgeweave-only install -- the material is absent from the
     * registry entirely, not merely present-and-uncraftable the way docs/SCOPE.md M3.2 originally
     * shipped it. An arbitrary item still crafts nothing at the Part Builder either way.
     */
    @GameTest(template = "empty")
    public static void unsuppliedCompatMetalsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : new String[] {"bronze", "lead", "silver", "electrum"}) {
            helper.assertTrue(materials.get(materialId(name)) == null,
                    "expected the " + name + " material to be absent without its supplying mod, found it registered");
        }

        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openMenu(helper, pos, player);
        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(new ItemStack(Items.GOLD_NUGGET, 2));
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "expected no part from an item no material's crafting_items names");
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
