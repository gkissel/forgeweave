package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.capabilities.Capabilities;
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
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;

/**
 * docs/SCOPE.md M3.2 issue #231's verification: the seven vanilla-sourced materials ship with the
 * clone's trait wiring (the trait <i>behaviors</i> each have their own test in the #228/#229/#230
 * batches; what is under test here is the material JSON that selects them), and obsidian's melt/cast
 * path -- upstream's water + lava alloy, table part casting, and basin block casting. The two
 * assembly-path behaviors the acceptance list calls out by name -- writable's +2 slots and squeaky's
 * silk-touch/zero-damage on tools built from the shipped paper/sponge JSON -- live in
 * {@link MiningTraitGameTests}, which issue #231 switched from GameTest-only stand-in materials to
 * the shipped ones.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class VanillaMaterialGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    /**
     * The per-part trait lists of all seven #231 materials, read off the same synced registry the
     * Tool Station resolves from ({@code TraitGameTests#shippedMaterialsExposeTheirTraitListsThroughEveryPart}'s
     * pattern). Head scopes replace the general list on head parts (upstream's {@code addTrait(x,
     * HEAD)} semantics); upstream's redundant re-registrations (prismarine's second {@code
     * aquadynamic}, netherrack's second {@code hellish}) are collapsed to the general registration
     * per the docs/SCOPE.md trait table.
     */
    @GameTest(template = "empty")
    public static void vanillaMaterialsExposeTheirCloneTraitWiring(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        Map<String, List<String>> general = Map.of(
                "cactus", List.of("spiky"),
                "obsidian", List.of("duritos"),
                "prismarine", List.of("aquadynamic"),
                "endstone", List.of("enderference"),
                "paper", List.of("writable"),
                "sponge", List.of("squeaky"),
                "netherrack", List.of("hellish"));
        Map<String, List<String>> head = Map.of(
                "cactus", List.of("prickly"),
                "obsidian", List.of("duritos"),
                "prismarine", List.of("jagged"),
                "endstone", List.of("alien"),
                "paper", List.of("writable2"),
                "sponge", List.of("squeaky"),
                "netherrack", List.of("aridiculous"));

        general.forEach((name, generalTraits) -> {
            Material material = materials.get(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name));
            helper.assertTrue(material != null, name + " should be in the synced material registry");
            for (PartItem.Kind kind : PartItem.Kind.values()) {
                List<ResourceLocation> expected = (kind == PartItem.Kind.HEAD ? head.get(name) : generalTraits)
                        .stream().map(VanillaMaterialGameTests::traitId).toList();
                helper.assertTrue(expected.equals(material.traits().forPart(kind)),
                        name + " through a " + kind + " part should grant " + expected + ", got "
                                + material.traits().forPart(kind));
            }
        });
        helper.succeed();
    }

    /**
     * Upstream's {@code obsidianAlloy} ({@code TinkerSmeltery#registerAlloys}: "125 + 125 = 36"): a
     * bucket of water and a bucket of lava make two ingots of obsidian, poured here at the minimal
     * ratio the recipe is written in.
     */
    @GameTest(template = "smeltery")
    public static void waterAndLavaAlloyIntoObsidianAtUpstreamsRatio(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());

        core.tank().fill(new FluidStack(Fluids.WATER, 125), IFluidHandler.FluidAction.EXECUTE);
        core.tank().fill(new FluidStack(Fluids.LAVA, 125), IFluidHandler.FluidAction.EXECUTE);

        helper.assertValueEqual(core.tank().fluids().size(), 1, "distinct fluids left in the tank");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.OBSIDIAN.still().get(),
                "expected molten obsidian, the tank holds " + core.tank().getFluid().getFluid());
        helper.assertValueEqual(core.tank().getFluidAmount(), 36, "obsidian from a 125:125 water/lava pour");
        helper.succeed();
    }

    /**
     * Obsidian is castable upstream ({@code TinkerMaterials}: {@code obsidian.setCastable(true)},
     * {@code registerToolpartMeltingCasting}): a part cast plus 288 mB of molten obsidian is an
     * obsidian part, and the cast survives -- the same table flow every metal already has.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void obsidianCastsAPickaxeHeadAtTheTable(GameTestHelper helper) {
        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(ForgeweaveFluids.OBSIDIAN.still().get(), SearedTankBlockEntity.CAPACITY),
                        IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.WEST));
        CastingBlockEntity table = helper.getBlockEntity(CASTING);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get()));
        table.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(table.input().is(ForgeweaveItems.CAST_PICKAXE_HEAD.get()),
                "expected the right-click to put the cast in");
        helper.<FaucetBlockEntity>getBlockEntity(FAUCET).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                    "expected an obsidian pickaxe head, found " + table.output());
            helper.assertTrue(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "obsidian")
                            .equals(table.output().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the part to carry the obsidian material");
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_PICKAXE_HEAD.get()),
                    "expected the cast to survive its own casting cycle");
        });
    }

    /**
     * Upstream's basin casting ({@code TinkerSmeltery}: {@code registerBasinCasting(obsidian,
     * VALUE_Ore())} -- "obsidian casting gives you 2 ingot value per obsidian"): 288 mB back into a
     * vanilla obsidian block, and the recipe-sized tank accepts not a millibucket more.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void theBasinCastsAnObsidianBlock(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_BASIN.get());
        CastingBlockEntity basin = helper.getBlockEntity(CASTING);

        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(CASTING), Direction.UP);
        helper.assertTrue(handler != null, "expected the basin to expose a fluid handler");
        int filled = handler.fill(new FluidStack(ForgeweaveFluids.OBSIDIAN.still().get(), 4000),
                IFluidHandler.FluidAction.EXECUTE);
        helper.assertValueEqual(filled, 288, "an obsidian block's worth of fluid, and no more");

        helper.succeedWhen(() -> helper.assertTrue(basin.output().is(Items.OBSIDIAN),
                "expected an obsidian block, found " + basin.output()));
    }

    private static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }
}
