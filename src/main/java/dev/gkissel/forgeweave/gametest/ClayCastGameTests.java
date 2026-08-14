package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #292 (docs/SCOPE.md M3.4-12): upstream 1.12's single-use clay casts, the mid-game bridge to
 * the gold ones. {@code TinkerSmeltery#registerToolpartMeltingCasting} registers two recipes per
 * part behind {@code Config.claycasts} -- one moulding a clay cast out of
 * {@code clayCreationFluids} ({@code Material.VALUE_Ingot * 2} of molten clay, consuming the part
 * and leaving the cast in the input slot) and one casting through it with {@code consumesCast} true,
 * where the gold cast's own recipe passes false.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ClayCastGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    /**
     * Cast creation: molten clay over a crafted part moulds that part's clay cast, eating the part.
     *
     * <p>Budget: 288 mB at {@value FaucetBlockEntity#LIQUID_TRANSFER} mB/tick is 48 ticks of pouring
     * plus {@code CastingRecipe#cooldownTicks}' 24 + (700-300)*288/1600 = 96 cooling ticks, a floor
     * of 144; the rest is {@link CastingGameTests#STALL_ALLOWANCE_TICKS} (#269).
     */
    @GameTest(template = "empty", timeoutTicks = 144 + CastingGameTests.STALL_ALLOWANCE_TICKS)
    public static void pouringMoltenClayOverAPartMouldsItsClayCast(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.MOLTEN_CLAY.still().get());
        insert(helper, table, part());
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.input().is(clayCast()),
                    "expected the finished clay cast in the input slot, found " + table.input());
            helper.assertTrue(table.output().isEmpty(), "the part is consumed, so nothing lands in the output slot");
            helper.assertTrue(table.tank().isEmpty(), "and the pour is spent");
        });
    }

    /**
     * The single-use half: the clay cast shapes exactly one part and is gone with it, where the gold
     * cast survives its own casting cycle ({@link M3CastingGameTests}).
     *
     * <p>Budget: 48 pouring ticks plus 24 + (769-300)*288/1600 = 108 cooling ticks for molten iron.
     */
    @GameTest(template = "empty", timeoutTicks = 156 + CastingGameTests.STALL_ALLOWANCE_TICKS)
    public static void castingThroughAClayCastConsumesIt(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(clayCast()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.PART_AXE_HEAD.get()),
                    "expected an iron axe head, found " + table.output());
            helper.assertTrue(table.input().isEmpty(),
                    "a clay cast is single use, so nothing survives in the input slot: " + table.input());
        });
    }

    /**
     * Upstream skips registering both clay recipes while {@code Config.claycasts} is off. They are
     * datapack entries here, so {@link CastingRecipe#matches} filters them at lookup instead -- and
     * only them: the gold cast keeps working either way.
     *
     * <p>Synchronous on purpose: this mutates a global config value, and GameTests in one batch tick
     * concurrently, so the set/assert/restore has to complete inside a single test method.
     */
    @GameTest(template = "empty")
    public static void clayCastRecipesResolveOnlyWhileTheOptionIsOn(GameTestHelper helper) {
        Registry<CastingRecipe> recipes = helper.getLevel().registryAccess().registryOrThrow(CastingRecipe.REGISTRY);
        Fluid clay = ForgeweaveFluids.MOLTEN_CLAY.still().get();
        Fluid iron = ForgeweaveFluids.IRON.still().get();
        ItemStack part = part();
        ItemStack cast = new ItemStack(clayCast());
        ItemStack gold = new ItemStack(ForgeweaveItems.CAST_AXE_HEAD.get());

        helper.assertTrue(find(recipes, part, clay) != null, "molten clay over a part moulds its clay cast");
        helper.assertTrue(find(recipes, cast, iron) != null, "and metal poured through that cast shapes a part");

        ForgeweaveConfig.ENABLE_CLAY_CASTS.set(false);
        try {
            helper.assertTrue(find(recipes, part, clay) == null,
                    "no clay cast can be moulded while enableClayCasts is off");
            helper.assertTrue(find(recipes, cast, iron) == null,
                    "and no clay cast can be cast through while enableClayCasts is off");
            helper.assertTrue(find(recipes, gold, iron) != null, "the gold cast is unaffected by the option");
        } finally {
            ForgeweaveConfig.ENABLE_CLAY_CASTS.set(true);
        }
        helper.succeed();
    }

    private static CastingRecipe find(Registry<CastingRecipe> recipes, ItemStack held, Fluid fluid) {
        return CastingRecipe.find(recipes, CastingRecipe.Station.TABLE, held, fluid);
    }

    /**
     * Issue #387: the sharpening kit's clay cast, same creation/single-use coverage as the axe
     * head's above -- #292 registered one clay cast per gold cast, but #372's sharpening kit (and
     * its gold cast, {@code cast_sharpening_kit}) landed after #292 had already merged, so
     * {@code clay_cast_sharpening_kit} was never added to {@link ForgeweaveItems#CLAY_CASTS}.
     */
    @GameTest(template = "empty", timeoutTicks = 144 + CastingGameTests.STALL_ALLOWANCE_TICKS)
    public static void pouringMoltenClayOverASharpeningKitMouldsItsClayCast(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.MOLTEN_CLAY.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.PART_SHARPENING_KIT.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.input().is(sharpeningKitClayCast()),
                    "expected the finished sharpening kit clay cast in the input slot, found " + table.input());
            helper.assertTrue(table.output().isEmpty(), "the part is consumed, so nothing lands in the output slot");
            helper.assertTrue(table.tank().isEmpty(), "and the pour is spent");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 156 + CastingGameTests.STALL_ALLOWANCE_TICKS)
    public static void castingThroughASharpeningKitClayCastConsumesIt(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(sharpeningKitClayCast()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.PART_SHARPENING_KIT.get()),
                    "expected an iron sharpening kit, found " + table.output());
            helper.assertTrue(table.input().isEmpty(),
                    "a clay cast is single use, so nothing survives in the input slot: " + table.input());
        });
    }

    private static Item sharpeningKitClayCast() {
        return ForgeweaveItems.CLAY_CASTS.get("cast_sharpening_kit").get();
    }

    /** The clay cast this file works through: the axe head's, the same part {@link M3CastingGameTests} casts. */
    private static Item clayCast() {
        return ForgeweaveItems.CLAY_CASTS.get("cast_axe_head").get();
    }

    /** The part a cast is moulded around; its material never matters, the recipe matches the item. */
    private static ItemStack part() {
        return new ItemStack(ForgeweaveItems.PART_AXE_HEAD.get());
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

    /** Puts {@code stack} in the casting table the way a player does -- through the real right-click path. */
    private static void insert(GameTestHelper helper, CastingBlockEntity casting, ItemStack stack) {
        Item expected = stack.getItem();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        casting.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(casting.input().is(expected), "expected the right-click to put the " + expected + " in");
    }
}
