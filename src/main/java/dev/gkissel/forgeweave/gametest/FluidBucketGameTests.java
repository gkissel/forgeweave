package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

/**
 * Issue #286's regression: every molten fluid registers a bucket, matching upstream 1.12 where
 * every smeltery fluid is bucketable ({@code TinkerFluids#registerItems} and
 * {@code MaterialIntegration#preInit} both call {@code FluidRegistry.addBucketForFluid}).
 *
 * <p>Both of these fail on the pre-#286 code: {@link ForgeweaveFluids} registered no bucket item at
 * all, so {@code Fluid#getBucket} answered {@code Items.AIR} and
 * {@code LiquidBlock#pickupBlock}'s {@code new ItemStack(fluid.getBucket())} came back empty --
 * a molten source block simply could not be picked up.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class FluidBucketGameTests {

    /**
     * The round trip on one representative fluid: fill an empty spot with a molten iron source,
     * pick it up into a bucket, then empty that bucket back and get the source block again. Runs
     * the two real halves of the wiring -- {@code LiquidBlock#pickupBlock} reads
     * {@code Fluid#getBucket} (the {@code BaseFlowingFluid.Properties#bucket} half) and
     * {@code BucketItem#emptyContents} places {@code BucketItem#content} (the {@code new
     * BucketItem(still, ...)} half), so a one-way-only wiring fails here.
     */
    @GameTest(template = "empty")
    public static void moltenIronBucketFillsAndPlacesBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(pos);
        LiquidBlock liquid = ForgeweaveFluids.IRON.block().get();

        helper.setBlock(pos, liquid);
        BlockState source = level.getBlockState(absolute);

        ItemStack filled = liquid.pickupBlock(null, level, absolute, source);
        helper.assertTrue(!filled.isEmpty(),
                "expected picking up a molten iron source to hand back a bucket, got an empty stack "
                        + "(the fluid has no bucket wired to Fluid#getBucket)");
        helper.assertTrue(filled.is(ForgeweaveFluids.IRON.bucket().get()),
                "expected a molten iron bucket, got " + BuiltInRegistries.ITEM.getKey(filled.getItem()));
        helper.assertBlockNotPresent(liquid, pos);

        boolean placed = ((BucketItem) filled.getItem()).emptyContents(null, level, absolute, null);
        helper.assertTrue(placed, "expected emptying the molten iron bucket to place the fluid back");
        helper.assertTrue(level.getFluidState(absolute).getType() == ForgeweaveFluids.IRON.still().get(),
                "expected molten iron back in the world, got " + level.getFluidState(absolute).getType());
        helper.assertTrue(level.getFluidState(absolute).isSource(),
                "expected the placed molten iron to be a source block, not a flowing one");

        helper.succeed();
    }

    /**
     * Coverage half: every registered molten fluid -- not just the representative one above -- has a
     * non-empty bucket wired both ways, so the next fluid added cannot ship bucketless the way all
     * twenty did before #286.
     */
    @GameTest(template = "empty")
    public static void everyMoltenFluidHasABucket(GameTestHelper helper) {
        List<String> broken = ForgeweaveFluids.all().stream()
                // Empty stack == the fluid answers Items.AIR for its bucket, which is exactly what a
                // failed pickup hands the player. The other two clauses pin the wiring in both
                // directions: fluid -> bucket (pickup) and bucket -> fluid (placing it back).
                .filter(fluid -> new ItemStack(fluid.still().get().getBucket()).isEmpty()
                        || fluid.still().get().getBucket() != fluid.bucket().get()
                        || fluid.bucket().get().content != fluid.still().get())
                .map(ForgeweaveFluids.MoltenMetal::name)
                .toList();

        helper.assertTrue(broken.isEmpty(), "molten fluids without a bucket wired both ways: " + broken);
        helper.succeed();
    }
}
