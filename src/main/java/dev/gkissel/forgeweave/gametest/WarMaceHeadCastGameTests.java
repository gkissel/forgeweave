package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

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
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.menu.StencilTableMenu;

/**
 * Covers issue #1044's verification: the war mace head is cast only. It replaces #989's
 * {@code WarMaceHeadPatternGameTests}, whose {@code blankAloneDoesNotCraftTheWarMaceHeadPattern},
 * {@code blankPlusHeavyCoreCraftsTheWarMaceHeadPattern} and
 * {@code partBuilderMakesAWarMaceHeadFromThatPattern} no longer hold: the blank-pattern-plus-Heavy-
 * Core crafting-table recipe they exercised is gone ({@link
 * dev.gkissel.forgeweave.data.ForgeweaveRecipeProvider}), and the Part Builder no longer turns the
 * pattern into a part at all ({@link #partBuilderRefusesTheWarMaceHeadPatternFromAnyMaterial}, the
 * mirror image of what {@code partBuilderMakesAWarMaceHeadFromThatPattern} used to prove).
 *
 * <p>Not re-tested here: that a war mace head part still assembles into a war mace. That was never
 * pattern-route-specific -- {@link WarmaceGameTests} already builds its tools straight from {@link
 * ForgeweaveItems#PART_WAR_MACE_HEAD}, a path this issue leaves untouched.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class WarMaceHeadCastGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    @GameTest(template = "empty")
    public static void warMaceHeadPatternIsNotAStencilTableOption(GameTestHelper helper) {
        helper.assertFalse(StencilTableMenu.PATTERNS.contains(ForgeweaveItems.PATTERN_WAR_MACE_HEAD),
                "expected the war mace head pattern to be absent from the Stencil Table's selection list");

        helper.succeed();
    }

    /** The Part Builder refuses the pattern structurally (no {@code Entry} for it at all), not per material. */
    @GameTest(template = "empty")
    public static void partBuilderRefusesTheWarMaceHeadPatternFromAnyMaterial(GameTestHelper helper) {
        helper.assertFalse(PartBuilderRecipes.isPattern(new ItemStack(ForgeweaveItems.PATTERN_WAR_MACE_HEAD.get())),
                "expected the war mace head pattern to no longer be recognized as a part pattern at all");

        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos);
        PartBuilderMenu menu = new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());

        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_WAR_MACE_HEAD.get()));
        ResourceLocation wood = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood");
        int shardCount = PartBuilderRecipes.LARGE_HEAD_COST / PartBuilderRecipes.SHARD_VALUE;
        ItemStack shards = new ItemStack(ForgeweaveItems.SHARD.get(), shardCount);
        shards.set(ForgeweaveDataComponents.MATERIAL.get(), wood);
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(shards);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "expected no output: the war mace head pattern builds nothing at the Part Builder, in any material");

        helper.succeed();
    }

    /** The one route left: gold over a Heavy Core makes the cast, and the core is the price of it. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void pouringGoldOverAHeavyCoreCreatesTheCastAndConsumesTheCore(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.GOLD.still().get());
        insert(helper, table, new ItemStack(Items.HEAVY_CORE));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_WAR_MACE_HEAD.get()),
                    "expected the finished war mace head cast in the input slot, found " + table.input());
            helper.assertTrue(table.output().isEmpty(), "the Heavy Core is consumed, so nothing lands in the output slot");
            helper.assertTrue(table.tank().isEmpty(), "and the pour is spent");
        });
    }

    /** The old route is gone: a war mace head part sitting in the table matches no casting recipe any more. */
    @GameTest(template = "empty")
    public static void pouringGoldOverAWarMaceHeadPartMakesNoCast(GameTestHelper helper) {
        CastingBlockEntity table = rigWithoutPour(helper);
        insert(helper, table, new ItemStack(ForgeweaveItems.PART_WAR_MACE_HEAD.get()));

        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(CASTING), Direction.UP);
        helper.assertTrue(handler != null, "expected the casting table to expose a fluid handler");
        int filled = handler.fill(new FluidStack(ForgeweaveFluids.GOLD.still().get(), 288), IFluidHandler.FluidAction.EXECUTE);
        helper.assertValueEqual(filled, 0,
                "expected no casting recipe to match a war mace head part sitting in the table");

        helper.succeed();
    }

    /** The cast itself is unaffected: gold-cast metal still pours into a war mace head part through it. */
    @GameTest(template = "empty", timeoutTicks = 683 + CastingGameTests.STALL_ALLOWANCE_TICKS)
    public static void theCastStillCastsAWarMaceHeadPart(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.COBALT.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_WAR_MACE_HEAD.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.PART_WAR_MACE_HEAD.get()),
                    "expected a cobalt war mace head, found " + table.output());
            helper.assertTrue(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "cobalt")
                            .equals(table.output().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the part to carry the cobalt material");
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_WAR_MACE_HEAD.get()),
                    "expected the cast to survive its own casting cycle");
        });
    }

    /**
     * Save compat (issue #1044): the pattern item stays registered, just unobtainable, rather than
     * being removed or aliased away -- there is nothing sensible to alias it to (unlike #1020's
     * Armor Station, which pointed its alias at a block that still exists). An item stack an old
     * world holds has to keep resolving to itself instead of reading back as air.
     */
    @GameTest(template = "empty")
    public static void theOldPatternItemStillResolvesForSaveCompat(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ForgeweaveItems.PATTERN_WAR_MACE_HEAD.get());
        CompoundTag saved = (CompoundTag) stack.save(helper.getLevel().registryAccess());
        ItemStack loaded = ItemStack.parseOptional(helper.getLevel().registryAccess(), saved);

        helper.assertTrue(loaded.is(ForgeweaveItems.PATTERN_WAR_MACE_HEAD.get()),
                "expected forgeweave:pattern_war_mace_head to still resolve for a world holding one");

        helper.succeed();
    }

    /** A tank of {@code fluid}, a faucet on its east side pointing back at it, and a casting table below. */
    private static CastingBlockEntity rig(GameTestHelper helper, Fluid fluid) {
        CastingBlockEntity table = rigWithoutPour(helper);
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(fluid, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        return table;
    }

    /** The same rig, without filling the tank yet -- for the negative test, which never activates the faucet. */
    private static CastingBlockEntity rigWithoutPour(GameTestHelper helper) {
        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        Block casting = ForgeweaveBlocks.CASTING_TABLE.get();
        helper.setBlock(CASTING, casting);
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
