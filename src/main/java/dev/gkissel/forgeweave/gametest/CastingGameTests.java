package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
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

/**
 * Covers docs/SCOPE.md M2 issue #100's verification on a headless dedicated server: molten gold
 * poured over a crafted part makes that part's cast, the cast survives casting a metal part, the
 * basin casts a block, and the faucet moves upstream's exact amounts.
 *
 * <p>Every test builds the same real rig -- a seared tank, a faucet on its side, a casting block
 * underneath -- and starts the pour by activating the faucet, so what is exercised is the whole
 * chain (faucet transaction, casting fluid handler, recipe lookup, scheduled cooling tick) rather
 * than any one piece in isolation. The one exception is the basin, which is filled through its own
 * capability so the test does not also spend 216 ticks watching nine faucet transactions.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CastingGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);
    /** #207: a hopper directly under the casting block, and a redstone block on top of the faucet. */
    private static final BlockPos HOPPER = new BlockPos(2, 1, 1);
    private static final BlockPos POWER = new BlockPos(2, 4, 1);

    // #183: the rig a player actually builds -- a formed smeltery with a drain in its wall, a faucet
    // on the outside of that drain, and a casting block under the faucet. Positions are relative to
    // SmelteryGameTests' 1x1x2 minimum structure (core (0,2,1), wall tank (1,2,0)).
    private static final BlockPos DRAIN = new BlockPos(1, 2, 2);
    private static final BlockPos DRAIN_FAUCET = new BlockPos(1, 2, 3);
    private static final BlockPos DRAIN_CASTING = new BlockPos(1, 1, 3);
    /** #207: the redstone block that powers that faucet, clear of the smeltery's own walls. */
    private static final BlockPos DRAIN_POWER = new BlockPos(1, 3, 3);

    /**
     * How far past its measured tick floor a casting test's {@code timeoutTicks} is set (#269).
     *
     * <p>A GameTest's deadline is <em>game time</em>: {@code GameTestInfo#tickInternal} computes
     * {@code tickCount = level.getGameTime() - startTick}, so the budget burns down on every server
     * tick. A pour, though, runs on vanilla <em>scheduled block ticks</em>, and {@code LevelTicks}
     * only delivers those for chunks passing {@code ServerLevel::isPositionTickingWithEntitiesLoaded}
     * -- which is {@code areEntitiesLoaded(chunk) && chunkSource.isPositionTicking(chunk)}. That is
     * strictly stronger than the {@code isPositionEntityTicking} a {@code GameTestInfo} latches
     * {@code chunksLoaded} on, and the gap between the two is real: {@code
     * PersistentEntitySectionManager#updateChunkStatus} publishes {@code chunkVisibility}
     * synchronously (so the test clock may start) but only then queues {@code requestChunkLoad},
     * whose entity-section read completes asynchronously and is promoted to {@code LOADED} on some
     * <em>later</em> {@code processPendingLoads}. While that window is open the level keeps ticking
     * -- the deadline keeps running -- and not one scheduled tick is delivered to the test's chunk.
     * The pour is not lost ({@code LevelTicks#sortContainersToTick} holds the container rather than
     * dropping it) but it resumes late against a clock that never paused, which is exactly #269's
     * shape: the assertion's last observation was an empty output slot.
     *
     * <p>So the budget past a test's floor is a <em>wall-clock stall allowance</em>, not tick
     * jitter, and it does not scale with how long the pour is. A {@code GameTestServer} free-runs
     * ({@code waitUntilNextTick} is overridden to {@code runAllTasks}); measured over this suite it
     * turns 7180 game ticks in 6.2 s, about 1160 ticks per wall-clock second. The old 1600-tick
     * budgets left the two cobalt tests -- floors measured at 765 and 683 ticks -- 835 and 917
     * ticks of slack, i.e. 0.72 s and 0.79 s. Half a second of GC, of background-executor queueing
     * behind the worldgen every batch kicks off, or of ticket churn from the mock players each test
     * logs in at world spawn is enough to spend all of it. 3500 ticks is roughly three seconds of
     * that allowance instead, and costs nothing on a passing run -- both tests still succeed on
     * their floor tick.
     */
    static final int STALL_ALLOWANCE_TICKS = 3500;

    /** {@code shovel_head_iron.json}: two ingots, the amount every part cast in the pack asks for. */
    private static final int PART_CAST_AMOUNT = 288;
    /** Enough of it to be unambiguously mid-pour, and the figure the screenshot harness captures. */
    private static final int PART_POUR = 204;

    /**
     * #183 regression: right-click the faucet hanging off a smeltery drain and the molten metal has
     * to reach the basin underneath. Every other casting test feeds the faucet from a seared tank,
     * so nothing covered the drain -- the only way fluid leaves a smeltery, and so the only way a
     * player ever fills a basin.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void aFaucetOnASmelteryDrainPoursIntoTheBasin(GameTestHelper helper) {
        CastingBlockEntity basin = drainRig(helper, ForgeweaveBlocks.CASTING_BASIN.get(), ForgeweaveFluids.GOLD.still().get());

        helper.useBlock(DRAIN_FAUCET, helper.makeMockPlayer(GameType.SURVIVAL));

        helper.succeedWhen(() -> helper.assertTrue(!basin.tank().isEmpty(),
                "expected molten gold to have reached the basin"));
    }

    /**
     * #206 regression: the basin refused every metal but iron/copper/gold/netherite for lack of a
     * storage block to cast -- proven end to end through the same real rig #183 proved for gold: a
     * formed smeltery, a drain in its wall, a faucet on the drain, right-clicked exactly as a player
     * would (not fed through the fluid-handler capability directly, unlike {@link
     * #theBasinCastsAMetalBlock}).
     *
     * <p>Budget: a cobalt block is 1296 mB = nine 144-mB faucet transactions at 6 mB/tick = 216
     * ticks of pouring, then {@code CastingRecipe#cooldownTicks}' 24 + (950-300)*1296/1600 = 550
     * ticks of cooling -- and the block does land on tick 765, measured. Everything past that floor
     * is {@link #STALL_ALLOWANCE_TICKS}: #269 showed that "the floor doubled" (the old 800 and then
     * 1600) is the wrong rule, because what has to be covered is a wall-clock stall that does not
     * scale with the length of the pour.
     */
    @GameTest(template = "smeltery", timeoutTicks = 765 + STALL_ALLOWANCE_TICKS)
    public static void theBasinCastsACobaltBlockViaTheDrainFaucet(GameTestHelper helper) {
        CastingBlockEntity basin = drainRig(helper, ForgeweaveBlocks.CASTING_BASIN.get(), ForgeweaveFluids.COBALT.still().get());

        helper.useBlock(DRAIN_FAUCET, helper.makeMockPlayer(GameType.SURVIVAL));

        helper.succeedWhen(() -> helper.assertTrue(basin.output().is(ForgeweaveItems.COBALT_BLOCK.get()),
                "expected a cobalt block, found " + basin.output()));
    }

    /**
     * #183, the order a player actually builds in: the smeltery is put up and formed first, and only
     * then is a wall block swapped for a drain. The drain is not next to the core, so nothing tells
     * the core to look at its walls again.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void aDrainAddedToAFormedSmelteryStillPours(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the smeltery to form first: " + core.lastResult().getString());
        core.tank().fill(new FluidStack(ForgeweaveFluids.GOLD.still().get(), 4000), IFluidHandler.FluidAction.EXECUTE);

        // Only now the drain, the faucet and the basin -- the smeltery is already built and idle.
        helper.setBlock(DRAIN, ForgeweaveBlocks.SEARED_DRAIN.get());
        helper.setBlock(DRAIN_CASTING, ForgeweaveBlocks.CASTING_BASIN.get());
        helper.setBlock(DRAIN_FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.NORTH));
        CastingBlockEntity basin = helper.getBlockEntity(DRAIN_CASTING);

        // A few ticks between building and right-clicking, as a player takes.
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.useBlock(DRAIN_FAUCET, helper.makeMockPlayer(GameType.SURVIVAL)))
                .thenWaitUntil(() -> helper.assertTrue(!basin.tank().isEmpty(),
                        "expected molten gold to have reached the basin"))
                .thenSucceed();
    }

    /** A smeltery full of {@code fluid}, a drain in its wall, a faucet on the drain, {@code casting} below it. */
    private static CastingBlockEntity drainRig(GameTestHelper helper, net.minecraft.world.level.block.Block casting, Fluid fluid) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        helper.setBlock(DRAIN, ForgeweaveBlocks.SEARED_DRAIN.get());
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the drain smeltery to form: " + core.lastResult().getString());
        core.tank().fill(new FluidStack(fluid, 4000), IFluidHandler.FluidAction.EXECUTE);

        helper.setBlock(DRAIN_CASTING, casting);
        helper.setBlock(DRAIN_FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(DRAIN_CASTING);
    }

    /**
     * SCOPE.md M2's acceptance step 2: "pour molten gold over a crafted part to create a reusable
     * gold cast". Upstream consumes the part and leaves the fresh cast in the input slot.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void pouringGoldOverAPartCreatesItsCast(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.GOLD.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_PICKAXE_HEAD.get()),
                    "expected the finished cast in the input slot, found " + table.input());
            helper.assertTrue(table.output().isEmpty(), "the part is consumed, so nothing lands in the output slot");
            helper.assertTrue(table.tank().isEmpty(), "and the pour is spent");
        });
    }

    /**
     * The other half of "casts are gold-only and reusable (pure parity)": casting a metal part
     * leaves the cast exactly where it was, ready for the next pour.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aCastSurvivesCastingAndProducesTheMetalPart(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                    "expected an iron pickaxe head, found " + table.output());
            helper.assertTrue(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "iron")
                            .equals(table.output().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the part to carry the iron material");
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_PICKAXE_HEAD.get()),
                    "expected the cast to survive its own casting cycle");
        });
    }

    /**
     * Issue #474 (parity audit T43), upstream's {@code BucketCastingRecipe}: an empty vanilla bucket
     * sitting in the table, poured full of a smeltery fluid, comes back a filled bucket of it -- the
     * container-filling path the audit found missing. Budget: 1000 mB at {@link
     * FaucetBlockEntity#LIQUID_TRANSFER} 6 mB/tick is 167 ticks of pouring, plus upstream's flat
     * 5-tick bucket cool ({@code bucket_molten_iron.json}'s {@code time}), so 172 past which is
     * {@link #STALL_ALLOWANCE_TICKS}.
     */
    @GameTest(template = "empty", timeoutTicks = 172 + STALL_ALLOWANCE_TICKS)
    public static void pouringMoltenIronOverAnEmptyBucketFillsIt(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(Items.BUCKET));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveFluids.IRON.bucket().get()),
                    "expected a molten iron bucket, found " + table.output());
            helper.assertTrue(table.input().isEmpty(), "expected the empty bucket to be consumed");
            helper.assertTrue(table.tank().isEmpty(), "and the pour to be spent");
        });
    }

    /**
     * Basin casting: no cast at all, one block's worth of fluid, one metal block out. Filled through
     * the capability rather than the faucet -- see the class javadoc.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void theBasinCastsAMetalBlock(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_BASIN.get());
        CastingBlockEntity basin = helper.getBlockEntity(CASTING);

        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(CASTING), Direction.UP);
        helper.assertTrue(handler != null, "expected the basin to expose a fluid handler");
        int filled = handler.fill(new FluidStack(ForgeweaveFluids.GOLD.still().get(), 4000),
                IFluidHandler.FluidAction.EXECUTE);
        helper.assertValueEqual(filled, 1296, "a gold block's worth of fluid, and no more");

        helper.succeedWhen(() -> helper.assertTrue(basin.output().is(Items.GOLD_BLOCK),
                "expected a gold block, found " + basin.output()));
    }

    /**
     * T41 (#472): upstream {@code TinkerSmeltery} (line 479) casts an emerald block from an empty
     * basin at {@code Material.VALUE_Gem * 9} = 5994 mB of molten emerald -- the block half of the
     * ore/gem/block melting row that was until now fluid-and-gem-cast only. Same rig as {@link
     * #theBasinCastsAMetalBlock}: filled directly through the capability with more than the recipe
     * needs, to prove the fill itself caps at the recipe amount rather than only the eventual output.
     */
    @GameTest(template = "empty", timeoutTicks = 2900)
    public static void theBasinCastsAnEmeraldBlock(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_BASIN.get());
        CastingBlockEntity basin = helper.getBlockEntity(CASTING);

        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(CASTING), Direction.UP);
        helper.assertTrue(handler != null, "expected the basin to expose a fluid handler");
        int filled = handler.fill(new FluidStack(ForgeweaveFluids.EMERALD.still().get(), 8000),
                IFluidHandler.FluidAction.EXECUTE);
        helper.assertValueEqual(filled, 5994, "an emerald block's worth of fluid, and no more");

        helper.succeedWhen(() -> helper.assertTrue(basin.output().is(Items.EMERALD_BLOCK),
                "expected an emerald block, found " + basin.output()));
    }

    /**
     * #183 regression: the basin fed the way a player feeds it -- a faucet above it, one
     * {@value FaucetBlockEntity#TRANSACTION_AMOUNT} mB transaction at a time -- rather than through
     * one capability call that hands it the whole recipe amount at once.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void theBasinCastsABlockFromAPouredFaucet(GameTestHelper helper) {
        CastingBlockEntity basin = rig(helper, ForgeweaveBlocks.CASTING_BASIN.get(), ForgeweaveFluids.GOLD.still().get());
        faucet(helper).activate();

        helper.succeedWhen(() -> helper.assertTrue(basin.output().is(Items.GOLD_BLOCK),
                "expected a gold block, found " + basin.output()));
    }

    /**
     * #183 regression: a basin that was poured into, then reloaded from disk, keeps taking fluid and
     * still finishes. A block entity's {@code loadAdditional} runs before it has a level, so nothing
     * resolved from a recipe can be restored there.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void aPartlyPouredBasinStillFinishesAfterAReload(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_BASIN.get());
        fill(helper, new FluidStack(ForgeweaveFluids.GOLD.still().get(), FaucetBlockEntity.TRANSACTION_AMOUNT));

        helper.startSequence()
                .thenExecute(() -> reload(helper, CASTING))
                .thenIdle(2)
                .thenExecute(() -> helper.assertValueEqual(
                        fill(helper, new FluidStack(ForgeweaveFluids.GOLD.still().get(), 4000)),
                        1296 - FaucetBlockEntity.TRANSACTION_AMOUNT, "the rest of the gold block still fits"))
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.<CastingBlockEntity>getBlockEntity(CASTING).output().is(Items.GOLD_BLOCK),
                        "expected a gold block after the reload"))
                .thenSucceed();
    }

    /**
     * #186 regression: the other half of the same defect -- a table left holding a partial pour by a
     * source that ran dry must not be sealed shut by a reload, with its cast trapped inside.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void aPartlyPouredTableGivesItsCastBackAfterAReload(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        insert(helper, helper.getBlockEntity(CASTING), new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        fill(helper, new FluidStack(ForgeweaveFluids.IRON.still().get(), 72));

        helper.startSequence()
                .thenExecute(() -> reload(helper, CASTING))
                .thenIdle(2)
                .thenExecute(() -> helper.assertValueEqual(
                        fill(helper, new FluidStack(ForgeweaveFluids.IRON.still().get(), 4000)), 72,
                        "the rest of the ingot still fits"))
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.<CastingBlockEntity>getBlockEntity(CASTING).output().is(Items.IRON_INGOT),
                        "expected an iron ingot after the reload"))
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    helper.useBlock(CASTING, player);
                    helper.useBlock(CASTING, player);
                    helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_INGOT)),
                            "expected the ingot back");
                    helper.assertTrue(player.getInventory().contains(new ItemStack(ForgeweaveItems.CAST_INGOT.get())),
                            "expected the cast back");
                })
                .thenSucceed();
    }

    /**
     * #355 regression: a faucet saved and reloaded in the one-tick window between two transactions
     * has to come back still pouring. The step that moves the last {@value
     * FaucetBlockEntity#LIQUID_TRANSFER} mB of a transaction empties the buffer a tick before the
     * next transaction fills it again, and a faucet whose pour state was re-derived from that buffer
     * read the gap as "not pouring": clients stopped drawing the stream, {@code FaucetBlock#tick}
     * handed the pending tick to {@code activate} rather than {@code pourStep}, and a stop request
     * riding along with it was dropped.
     *
     * <p>A basin is the target because it takes nine transactions to fill, so the window is reached
     * eight times before the pour ends and the reload lands squarely mid-pour rather than on the
     * final boundary. Budget: 1296 mB at {@value FaucetBlockEntity#LIQUID_TRANSFER} mB/tick is 216
     * ticks of pouring plus {@code CastingRecipe#cooldownTicks}' 24 + (532-300)*1296/1600 = 211 of
     * cooling, and everything past that floor is {@link #STALL_ALLOWANCE_TICKS}.
     */
    @GameTest(template = "empty", timeoutTicks = 430 + STALL_ALLOWANCE_TICKS)
    public static void aFaucetReloadedBetweenTransactionsFinishesItsPour(GameTestHelper helper) {
        CastingBlockEntity basin = rig(helper, ForgeweaveBlocks.CASTING_BASIN.get(), ForgeweaveFluids.GOLD.still().get());
        faucet(helper).activate();

        helper.startSequence()
                // The window: the transaction in flight has reached the basin in full, and the one
                // after it has not started yet.
                .thenWaitUntil(() -> {
                    FaucetBlockEntity faucet = faucet(helper);
                    helper.assertTrue(faucet.isPouring(), "expected the faucet to be pouring");
                    helper.assertTrue(faucet.buffered().isEmpty(),
                            "expected to catch the faucet between two transactions, found "
                                    + faucet.buffered().getAmount() + " mB buffered");
                })
                .thenExecute(() -> {
                    reload(helper, FAUCET);
                    helper.assertTrue(faucet(helper).isPouring(),
                            "expected a faucet reloaded between transactions to still be pouring");
                })
                .thenWaitUntil(() -> helper.assertTrue(basin.output().is(Items.GOLD_BLOCK),
                        "expected the reloaded faucet to have finished its pour, found " + basin.output()))
                .thenSucceed();
    }

    // ------------------------------------------------------------------ #502 (T71 parity audit)

    /**
     * Upstream {@code TinkerRegistry.registerTableCasting(TinkerCommons.mudBrick, castIngot,
     * TinkerFluids.dirt, Material.VALUE_Ingot)}: molten dirt through an ingot cast at the table makes
     * a mud brick, the first table-casting GameTest against a non-metal fluid.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void mudBrickCastsFromMoltenDirtAtTheTable(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.MOLTEN_DIRT.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> helper.assertTrue(table.output().is(ForgeweaveItems.MUD_BRICK.get()),
                "expected a mud brick, found " + table.output()));
    }

    /**
     * Upstream {@code TinkerRegistry.registerBasinCasting(new ItemStack(Blocks.HARDENED_CLAY),
     * ItemStack.EMPTY, TinkerFluids.clay, Material.VALUE_BrickBlock)}: an empty basin under molten
     * clay makes plain terracotta, same "empty basin" shape as {@link #theBasinCastsABlockFromAPouredFaucet}.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void terracottaCastsInAnEmptyBasinFromMoltenClay(GameTestHelper helper) {
        CastingBlockEntity basin = rig(helper, ForgeweaveBlocks.CASTING_BASIN.get(), ForgeweaveFluids.MOLTEN_CLAY.still().get());
        faucet(helper).activate();

        helper.succeedWhen(() -> helper.assertTrue(basin.output().is(Items.TERRACOTTA),
                "expected terracotta, found " + basin.output()));
    }

    /**
     * Upstream {@code CastingRecipe(new ItemStack(Blocks.SAND, 1, 1), RecipeMatch.of(sand),
     * FluidStack(blood, 10), true, false)}: sand sitting in the basin under blood becomes red sand --
     * the basin's "cast is a held item, not empty" shape, exercised against blood rather than a metal.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void redSandCastsFromSandAndBloodInTheBasin(GameTestHelper helper) {
        CastingBlockEntity basin = rig(helper, ForgeweaveBlocks.CASTING_BASIN.get(), ForgeweaveFluids.BLOOD.still().get());
        insert(helper, basin, new ItemStack(Items.SAND));
        faucet(helper).activate();

        helper.succeedWhen(() -> helper.assertTrue(basin.output().is(Items.RED_SAND),
                "expected red sand, found " + basin.output()));
    }

    /**
     * Issue #594: the seared glass cast is the full {@code c:glass_blocks} tag, not just its
     * {@code colorless} child -- old stained glass sitting in the basin under molten seared stone
     * gets recycled into seared glass exactly like plain glass does.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void searedGlassCastsFromStainedGlassInTheBasin(GameTestHelper helper) {
        CastingBlockEntity basin = rig(helper, ForgeweaveBlocks.CASTING_BASIN.get(), ForgeweaveFluids.SEARED_STONE.still().get());
        insert(helper, basin, new ItemStack(Items.WHITE_STAINED_GLASS));
        faucet(helper).activate();

        helper.succeedWhen(() -> helper.assertTrue(basin.output().is(ForgeweaveItems.SEARED_GLASS.get()),
                "expected seared glass, found " + basin.output()));
    }

    /** Pours into the casting block through its own capability, the way a faucet does. */
    private static int fill(GameTestHelper helper, FluidStack fluid) {
        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(CASTING), Direction.UP);
        helper.assertTrue(handler != null, "expected the casting block to expose a fluid handler");
        return handler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
    }

    /**
     * Puts the block entity through the exact round trip a chunk load does: saved to NBT, rebuilt by
     * {@link BlockEntity#loadStatic} with no level, and only then handed one.
     */
    private static void reload(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pos);
        CompoundTag saved = level.getBlockEntity(absolute).saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(absolute);
        BlockEntity reloaded = BlockEntity.loadStatic(absolute, level.getBlockState(absolute), saved,
                level.registryAccess());
        helper.assertTrue(reloaded != null, "expected the block entity to load back");
        level.setBlockEntity(reloaded);
    }

    /**
     * #186 regression: the whole right-click path through the block, not {@link
     * CastingBlockEntity#interact} directly -- a cast goes in, comes back out, and the finished
     * result comes out after it.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void rightClickingTheTablePutsItemsInAndTakesThemBackOut(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        CastingBlockEntity table = helper.getBlockEntity(CASTING);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));

        helper.useBlock(CASTING, player);
        helper.assertTrue(table.input().is(ForgeweaveItems.CAST_INGOT.get()),
                "expected the right-click to put the cast in, found " + table.input());

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(CASTING, player);
        helper.assertTrue(table.input().isEmpty(), "expected the cast to come back out, found " + table.input());
        helper.assertTrue(player.getInventory().contains(new ItemStack(ForgeweaveItems.CAST_INGOT.get())),
                "expected the cast in the player's inventory");
        helper.succeed();
    }

    /** #186 regression, second half: a finished result is retrievable by right-clicking too. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void rightClickingTheTableTakesOutAFinishedResult(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        faucet(helper).activate();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(table.output().is(Items.IRON_INGOT),
                        "expected an iron ingot, found " + table.output()))
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    helper.useBlock(CASTING, player);
                    helper.assertTrue(table.output().isEmpty(),
                            "expected the ingot to come back out, found " + table.output());
                    helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_INGOT)),
                            "expected the ingot in the player's inventory");
                })
                .thenSucceed();
    }

    /**
     * Upstream's two faucet constants: a transaction leaves the source in one go
     * ({@code TRANSACTION_AMOUNT}, one ingot) and reaches the target {@code LIQUID_TRANSFER} mB at a
     * time. Both are checked on the very tick the pour starts, before anything else can move.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void theFaucetMovesUpstreamsExactAmounts(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        SearedTankBlockEntity tank = helper.getBlockEntity(TANK);
        int before = tank.tank().getFluidAmount();

        FaucetBlockEntity faucet = faucet(helper);
        faucet.activate();

        helper.assertValueEqual(before - tank.tank().getFluidAmount(), FaucetBlockEntity.TRANSACTION_AMOUNT,
                "one ingot leaves the tank the moment the pour starts");
        helper.assertValueEqual(table.tank().getFluidAmount(), FaucetBlockEntity.LIQUID_TRANSFER,
                "and only one step of it has reached the table");
        helper.assertValueEqual(faucet.buffered().getAmount(),
                FaucetBlockEntity.TRANSACTION_AMOUNT - FaucetBlockEntity.LIQUID_TRANSFER, "the rest is in the faucet");

        // An ingot cast wants exactly one ingot, so the faucet stops itself after this transaction
        // rather than draining the tank dry.
        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(Items.IRON_INGOT), "expected an iron ingot, found " + table.output());
            helper.assertValueEqual(tank.tank().getFluidAmount(), before - FaucetBlockEntity.TRANSACTION_AMOUNT,
                    "exactly one ingot left the tank in total");
            helper.assertFalse(faucet.isPouring(), "and the faucet stopped once the table stopped accepting");
        });
    }

    /**
     * #200 regression: the packet that ends a pour has to actually say so. A faucet's whole state is
     * its buffered fluid, so once that emptied it saved nothing, {@link
     * net.minecraft.world.level.block.entity.BlockEntity#getUpdateTag} returned an empty tag, and
     * NeoForge's {@code IBlockEntityExtension#onDataPacket} throws empty tags away -- leaving every
     * client stuck on the last mid-pour state it heard about, drawing the stream forever.
     *
     * <p>Checked at the level it broke: the tag really leaving the block entity, and what happens
     * when a faucet that is already mid-pour is handed it -- which is exactly a client's situation.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aFinishedPourTellsClientsToStopDrawingTheStream(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        FaucetBlockEntity faucet = faucet(helper);
        faucet.activate();

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BlockPos absolute = helper.absolutePos(FAUCET);
        CompoundTag midPour = faucet.getUpdateTag(registries);
        helper.assertFalse(midPour.isEmpty(), "a pouring faucet has something to tell clients");

        helper.succeedWhen(() -> {
            helper.assertFalse(faucet.isPouring(), "the faucet stops once the table stops accepting");
            CompoundTag idle = faucet.getUpdateTag(registries);
            helper.assertFalse(idle.isEmpty(),
                    "an idle faucet's update tag must not be empty -- an empty one is dropped unread");

            // A client already holds the mid-pour state when the idle packet lands on top of it.
            FaucetBlockEntity clientSide = new FaucetBlockEntity(absolute, helper.getLevel().getBlockState(absolute));
            clientSide.loadWithComponents(midPour, registries);
            helper.assertTrue(clientSide.isPouring(), "expected the client to have been drawing a stream");
            clientSide.loadWithComponents(idle, registries);
            helper.assertFalse(clientSide.isPouring(), "and to stop once the pour-ended update arrives");
            helper.assertTrue(clientSide.buffered().isEmpty(), "with nothing left for the renderer to draw");
        });
    }

    /**
     * #204 regression: a client has to keep the capacity it draws its fill fraction against across
     * the whole pour, not just the first drop of it. The capacity is not in {@code FluidTank}'s NBT
     * -- only the recipe knows it -- and it used to be re-derived in {@code onLoad} alone, which
     * NeoForge runs once, at the end of the tick the block entity appeared in. Every update packet
     * after that left the capacity equal to the amount poured so far, so a mid-pour casting block
     * rendered as 100% full ({@code 204/204} where the server held {@code 204/288}).
     *
     * <p>Checked as the client experiences it: a block entity that has a level, has had its one
     * {@code onLoad}, and is then handed the same update tag the server sends on every drop.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aMidPourUpdateKeepsTheCapacityClientsDrawAgainst(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        insert(helper, helper.getBlockEntity(CASTING), new ItemStack(ForgeweaveItems.CAST_SHOVEL_HEAD.get()));
        helper.assertValueEqual(fill(helper, new FluidStack(ForgeweaveFluids.IRON.still().get(), PART_POUR)),
                PART_POUR, "the partial pour the table takes");

        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();
        BlockPos absolute = helper.absolutePos(CASTING);
        CompoundTag midPour = helper.<CastingBlockEntity>getBlockEntity(CASTING).getUpdateTag(registries);

        CastingBlockEntity clientSide = new CastingBlockEntity(absolute, level.getBlockState(absolute),
                CastingRecipe.Station.TABLE);
        clientSide.setLevel(level);
        clientSide.loadWithComponents(midPour, registries);
        clientSide.onLoad();
        clientSide.loadWithComponents(midPour, registries); // the next drop's packet

        helper.assertValueEqual(clientSide.tank().getFluidAmount(), PART_POUR, "the fluid a client sees");
        helper.assertValueEqual(clientSide.tank().getCapacity(), PART_CAST_AMOUNT,
                "the capacity a client draws its fill fraction against");
        helper.succeed();
    }

    /**
     * #207: a powered faucet pours by itself. Nobody right-clicks anything here -- a redstone block
     * goes up next to the faucet hanging off a full smeltery drain, and the basin below has to fill
     * and finish its gold block on its own, over the nine transactions a block costs.
     *
     * <p>A redstone block rather than a lever: same signal, no attachment face to keep valid, and
     * taking it away is the falling edge the next test needs.
     */
    @GameTest(template = "smeltery", timeoutTicks = 600)
    public static void aPoweredFaucetPoursWithoutAnyInteraction(GameTestHelper helper) {
        CastingBlockEntity basin = drainRig(helper, ForgeweaveBlocks.CASTING_BASIN.get(), ForgeweaveFluids.GOLD.still().get());

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.setBlock(DRAIN_POWER, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(basin.output().is(Items.GOLD_BLOCK),
                        "expected a powered faucet to have filled the basin unaided, found " + basin.output()))
                .thenSucceed();
    }

    /**
     * #207, the half a single rising edge cannot cover: the source is empty when the signal arrives.
     * A faucet that only reacts to edges gives up there and never notices the metal that shows up
     * twenty ticks later -- which is every real smeltery, still melting when the lever went up. A
     * powered one keeps looking, so the basin fills with no second edge and no click.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void aPoweredFaucetWaitsForASourceThatIsStillEmpty(GameTestHelper helper) {
        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_BASIN.get());
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.WEST));
        CastingBlockEntity basin = helper.getBlockEntity(CASTING);
        SearedTankBlockEntity tank = helper.getBlockEntity(TANK);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(POWER, Blocks.REDSTONE_BLOCK))
                // Long past the two-tick delay the rising edge booked, and nothing to pour yet.
                .thenExecuteAfter(20, () -> {
                    helper.assertFalse(faucet(helper).isPouring(), "there is nothing to pour yet");
                    tank.tank().fill(new FluidStack(ForgeweaveFluids.GOLD.still().get(), SearedTankBlockEntity.CAPACITY),
                            IFluidHandler.FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> helper.assertTrue(basin.output().is(Items.GOLD_BLOCK),
                        "expected the still-powered faucet to have picked the gold up by itself, found " + basin.output()))
                .thenSucceed();
    }

    /**
     * #207, the other edge: cutting the signal lets the transaction in flight land and then stops
     * the faucet, rather than draining the source dry. Checked against the tank, which is the only
     * thing that can tell a stopped faucet from a resting one.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void unpoweringAFaucetStopsItAfterTheTransactionInFlight(GameTestHelper helper) {
        rig(helper, ForgeweaveBlocks.CASTING_BASIN.get(), ForgeweaveFluids.GOLD.still().get());
        SearedTankBlockEntity tank = helper.getBlockEntity(TANK);
        FaucetBlockEntity faucet = faucet(helper);
        int[] atCut = new int[1];

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(POWER, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(faucet.isPouring(), "expected the signal to start a pour"))
                .thenExecute(() -> {
                    helper.setBlock(POWER, Blocks.AIR);
                    atCut[0] = tank.tank().getFluidAmount();
                })
                .thenWaitUntil(() -> helper.assertFalse(faucet.isPouring(),
                        "expected the faucet to stop once its transaction landed"))
                .thenExecute(() -> {
                    helper.assertTrue(faucet.buffered().isEmpty(), "expected nothing left buffered in a stopped faucet");
                    // A basin swallows nine transactions before it is full, so a faucet that carried
                    // on regardless of the signal would be caught here: only the transaction that was
                    // already in flight -- at most one, and it had already left the tank -- may show.
                    helper.assertTrue(atCut[0] - tank.tank().getFluidAmount() <= FaucetBlockEntity.TRANSACTION_AMOUNT,
                            "expected at most the in-flight transaction to leave the tank after the signal dropped, "
                                    + "found " + (atCut[0] - tank.tank().getFluidAmount()) + " mB");
                })
                .thenExecuteAfter(40, () -> helper.assertFalse(faucet.isPouring(), "and to stay stopped while unpowered"))
                .thenSucceed();
    }

    /**
     * #207: a hopper under a finished casting table takes the result and nothing else. The cast is
     * the reusable half of the pair (docs/SCOPE.md M2: "casts are gold-only and reusable"), so
     * automating a table must not eat it -- upstream's {@code canExtractItem} answers for the output
     * slot only.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aHopperUnderATableTakesTheResultAndLeavesTheCast(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        helper.setBlock(HOPPER, Blocks.HOPPER);
        faucet(helper).activate();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.<HopperBlockEntity>getBlockEntity(HOPPER).getItem(0).is(Items.IRON_INGOT),
                        "expected the hopper to have pulled the finished ingot out of the table"))
                .thenExecute(() -> {
                    helper.assertTrue(table.output().isEmpty(),
                            "expected the output slot emptied, found " + table.output());
                    helper.assertTrue(table.input().is(ForgeweaveItems.CAST_INGOT.get()),
                            "expected the cast to still be in the table, found " + table.input());
                })
                // And it stays that way: the cast is not the hopper's next course.
                .thenExecuteAfter(40, () -> helper.assertTrue(table.input().is(ForgeweaveItems.CAST_INGOT.get()),
                        "expected the cast to survive the hopper, found " + table.input()))
                .thenSucceed();
    }

    /**
     * #207: nothing comes out of a table that is still being poured into -- not the result (there
     * isn't one) and not the cast sitting under the fluid. Upstream guards both slots on {@code
     * tank.isEmpty()}; without it a hopper would pull the cast out from under a live pour.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aMidPourTableGivesAHopperNothing(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        CastingBlockEntity table = helper.getBlockEntity(CASTING);
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        fill(helper, new FluidStack(ForgeweaveFluids.IRON.still().get(), 72));
        helper.setBlock(HOPPER, Blocks.HOPPER);

        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(CASTING), Direction.DOWN);
        helper.assertTrue(handler != null, "expected the casting table to expose an item handler");
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            helper.assertTrue(handler.extractItem(slot, 64, true).isEmpty(),
                    "expected slot " + slot + " of a mid-pour table to be unextractable");
        }
        helper.assertFalse(handler.isItemValid(0, new ItemStack(ForgeweaveItems.CAST_INGOT.get())),
                "and nothing to be insertable into it either");

        // The real hopper agrees, given more than the 8 ticks it waits between transfers.
        helper.startSequence()
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(helper.<HopperBlockEntity>getBlockEntity(HOPPER).isEmpty(),
                            "expected the hopper under a mid-pour table to have taken nothing");
                    helper.assertTrue(table.input().is(ForgeweaveItems.CAST_INGOT.get()),
                            "expected the cast to still be there, found " + table.input());
                    helper.assertFalse(table.tank().isEmpty(), "and the pour to be untouched");
                })
                .thenSucceed();
    }

    /** A tank of {@code fluid}, a faucet on its east side pointing back at it, and a casting block below. */
    private static CastingBlockEntity rig(GameTestHelper helper, net.minecraft.world.level.block.Block casting, Fluid fluid) {
        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(fluid, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(CASTING, casting);
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.WEST));
        return helper.getBlockEntity(CASTING);
    }

    private static FaucetBlockEntity faucet(GameTestHelper helper) {
        return helper.getBlockEntity(FAUCET);
    }

    /** Puts {@code stack} in the casting block the way a player does -- through the real right-click path. */
    private static void insert(GameTestHelper helper, CastingBlockEntity casting, ItemStack stack) {
        Item expected = stack.getItem();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        casting.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(casting.input().is(expected), "expected the right-click to put the " + expected + " in");
    }
}
