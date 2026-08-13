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
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;

/**
 * docs/SCOPE.md M3.2 issue #235's verification: the four by-name modern-branch materials
 * (amethyst bronze, nahuatl, chorus, ancient -- the maintainer-authorized 1.20-clone additions).
 * The trait <i>behaviors</i> each have their own tests in the #228/#229/#230 batches; what is
 * under test here is the material JSON that selects them ({@link VanillaMaterialGameTests}'s
 * pattern), plus the two mechanics this batch adds: the copper+amethyst alloy and nahuatl's
 * composite casting -- a <em>wood</em> part as the cast at the casting table, molten obsidian
 * poured over it, the same part in nahuatl out (maintainer decision on the issue, upstream 1.20's
 * {@code composite/nahuatl.json} shape on the existing casting flags).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ModernMaterialGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    /** The 1.20 clone's ratio verbatim: 90 mB copper + 100 mB amethyst make 90 mB amethyst bronze. */
    @GameTest(template = "smeltery")
    public static void copperAndAmethystAlloyIntoAmethystBronze(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        core.tank().fill(new FluidStack(ForgeweaveFluids.COPPER.still().get(), 90), IFluidHandler.FluidAction.EXECUTE);
        core.tank().fill(new FluidStack(ForgeweaveFluids.AMETHYST.still().get(), 100), IFluidHandler.FluidAction.EXECUTE);

        helper.assertValueEqual(core.tank().fluids().size(), 1, "distinct fluids left in the tank");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.AMETHYST_BRONZE.still().get(),
                "expected amethyst bronze, the tank holds " + core.tank().getFluid().getFluid());
        helper.assertValueEqual(core.tank().getFluidAmount(), 90, "amethyst bronze in the tank");
        helper.succeed();
    }

    /**
     * Nahuatl composite casting end to end: a wood tool handle sits in the casting table as the
     * cast, 250 mB of molten obsidian (the 1.20 clone's per-cost amount, tool handle cost 1) pours
     * over it, and the finished nahuatl tool handle lands in the output with the wood part consumed
     * ({@code consumes_cast}).
     *
     * <p>Budget (#249): 250 mB pours in two faucet transactions at 6 mB/tick = ~42 ticks, plus
     * obsidian's 24 + (1000-300)*250/1600 = 133 cooling ticks -- a ~175-tick floor, so 600 keeps
     * the &gt;= 2x slack rule.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void pouringObsidianOverAWoodPartCastsItInNahuatl(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.OBSIDIAN.still().get());
        insert(helper, table, ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "wood"));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.PART_TOOL_HANDLE.get()),
                    "expected a finished tool handle in the output slot, found " + table.output());
            helper.assertTrue(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "nahuatl")
                            .equals(table.output().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the part to come out in nahuatl");
            helper.assertTrue(table.input().isEmpty(), "the wood part is the cast and consumes_cast clears it");
        });
    }

    /**
     * The material gate: the composite's cast ingredient is a {@code neoforge:components}
     * ingredient requiring the part's {@code forgeweave:material} to be wood, so an iron tool
     * handle matches no recipe at all -- the table accepts no fluid and nothing converts.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void obsidianRefusesANonWoodPart(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.OBSIDIAN.still().get());
        insert(helper, table, ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "iron"));
        faucet(helper).activate();

        helper.runAfterDelay(80, () -> {
            helper.assertTrue(table.input().is(ForgeweaveItems.PART_TOOL_HANDLE.get())
                            && ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "iron")
                                    .equals(table.input().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "the iron part must sit untouched in the input slot, found " + table.input());
            helper.assertTrue(table.output().isEmpty(), "and nothing may land in the output slot");
            helper.assertTrue(table.tank().isEmpty(), "and the table must accept no obsidian for it");
            helper.succeed();
        });
    }

    /**
     * The four materials' trait wiring, read off the same synced registry the Tool Station
     * resolves from ({@link VanillaMaterialGameTests}'s pattern). All four are general-scope
     * (the 1.20 clone's {@code default} trait lists; amethyst bronze's melee/harvest scope maps
     * to general because Forgeweave parts are all melee/harvest parts). Ancient's clone-side
     * {@code worldbound} companion is dropped -- Forgeweave has no such trait and the issue
     * scopes ancient to vintage alone.
     */
    @GameTest(template = "empty")
    public static void modernMaterialsExposeTheirTraitWiring(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        Map<String, String> general = Map.of(
                "amethyst_bronze", "crumbling",
                "nahuatl", "lacerating",
                "chorus", "enderference",
                "ancient", "vintage");

        general.forEach((name, trait) -> {
            Material material = materials.get(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name));
            helper.assertTrue(material != null, name + " should be in the synced material registry");
            List<ResourceLocation> expected = List.of(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, trait));
            for (PartItem.Kind kind : PartItem.Kind.values()) {
                helper.assertTrue(expected.equals(material.traits().forPart(kind)),
                        name + " through a " + kind + " part should grant " + expected
                                + ", got " + material.traits().forPart(kind));
            }
        });
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** The 1x1x2 minimum smeltery of {@link SmelteryGameTests} with a Standard Core, formed and empty. */
    private static SmelteryControllerBlockEntity smeltery(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    /** A tank of {@code fluid}, a faucet on its east side pointing back at it, and a casting table below ({@code CastingGameTests#rig}). */
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

    /** Puts {@code stack} in the casting table the way a player does -- through the real right-click path. */
    private static void insert(GameTestHelper helper, CastingBlockEntity casting, ItemStack stack) {
        ItemStack expected = stack.copy();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        casting.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(ItemStack.isSameItemSameComponents(casting.input(), expected),
                "expected the right-click to put " + expected + " in, found " + casting.input());
    }
}
