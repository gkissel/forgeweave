package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;

/**
 * Issue #677 (M4-2): the five armor parts at the two stations that make them. The Part Builder is
 * the cast bootstrap (docs/SCOPE.md D12): a non-{@code cast_only} plating material (obsidian)
 * stamps a plating there, gold poured over it moulds the plating cast, and only then does a
 * {@code cast_only} metal (iron) get its plating -- through the cast, never at the Part Builder.
 * Same rig as {@link ShardCastGameTests}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ArmorPartGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private static ItemStack craft(GameTestHelper helper, Player player, Item pattern, ItemStack material) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos);
        PartBuilderMenu menu = new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());
        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(pattern));
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(material);
        menu.broadcastChanges();
        return menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
    }

    /** Obsidian is a plating material and not {@code cast_only}: six blocks pay the chestplate's 6 ingots. */
    @GameTest(template = "empty")
    public static void partBuilderStampsObsidianChestplatePlating(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack output = craft(helper, player, ForgeweaveItems.PATTERN_PLATING_CHESTPLATE.get(),
                new ItemStack(Items.OBSIDIAN, 6));

        helper.assertTrue(output.is(ForgeweaveItems.PART_PLATING_CHESTPLATE.get()),
                "expected an obsidian chestplate plating, got " + output);
        helper.assertTrue(materialId("obsidian").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected forgeweave:obsidian, got " + output.get(ForgeweaveDataComponents.MATERIAL.get()));
        helper.succeed();
    }

    /** Iron is {@code cast_only}: its plating comes from the smeltery, never the Part Builder (D12). */
    @GameTest(template = "empty")
    public static void partBuilderRefusesIronPlating(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(
                craft(helper, player, ForgeweaveItems.PATTERN_PLATING_HELMET.get(), new ItemStack(Items.IRON_INGOT, 3)).isEmpty(),
                "iron is cast_only, so the part builder must stamp nothing");
        helper.assertTrue(
                craft(helper, player, ForgeweaveItems.PATTERN_PLATING_HELMET.get(), new ItemStack(Items.OAK_PLANKS, 3)).isEmpty(),
                "wood has no plating stats, so the part builder must stamp nothing");
        helper.succeed();
    }

    /** Vine carries the maille marker (D11) and is not {@code cast_only}: two vines pay the maille's 2 ingots. */
    @GameTest(template = "empty")
    public static void partBuilderStampsVineMaille(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack output = craft(helper, player, ForgeweaveItems.PATTERN_MAILLE.get(), new ItemStack(Items.VINE, 2));

        helper.assertTrue(output.is(ForgeweaveItems.PART_MAILLE.get()), "expected a vine maille, got " + output);
        helper.assertTrue(materialId("vine").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected forgeweave:vine, got " + output.get(ForgeweaveDataComponents.MATERIAL.get()));
        helper.assertTrue(
                craft(helper, player, ForgeweaveItems.PATTERN_MAILLE.get(), new ItemStack(Items.OAK_PLANKS, 2)).isEmpty(),
                "wood has no maille marker, so the part builder must stamp nothing");
        helper.succeed();
    }

    /** The bootstrap: gold over an obsidian plating moulds the reusable plating cast (no crafting-table recipe, D12). */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void pouringGoldOverObsidianPlatingMouldsThePlatingCast(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.GOLD.still().get());
        insert(helper, table, ToolAssembly.part(ForgeweaveItems.PART_PLATING_BOOTS.get(), "obsidian"));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_PLATING_BOOTS.get()),
                    "expected the boots plating cast in the input slot, found " + table.input());
            helper.assertTrue(table.output().isEmpty(), "the plating is consumed, so nothing lands in the output slot");
        });
    }

    /**
     * Molten iron through the boots plating cast: 288 mB at {@value FaucetBlockEntity#LIQUID_TRANSFER}
     * mB/tick is 48 ticks plus iron's 24 + (769-300)*288/1600 = 108 cooling ticks, a floor of 156.
     */
    @GameTest(template = "empty", timeoutTicks = 156 + CastingGameTests.STALL_ALLOWANCE_TICKS)
    public static void ironBootsPlatingCastsFromTheGoldCast(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_PLATING_BOOTS.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.PART_PLATING_BOOTS.get()),
                    "expected an iron boots plating, found " + table.output());
            helper.assertTrue(materialId("iron").equals(table.output().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the plating to carry the iron material");
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_PLATING_BOOTS.get()),
                    "expected the gold cast to survive");
        });
    }

    /** Every one of the five parts has its gold-cast mould, a metal pour through it, and the clay pair. */
    @GameTest(template = "empty")
    public static void everyArmorPartHasItsCastingEntries(GameTestHelper helper) {
        var recipes = helper.getLevel().registryAccess().registryOrThrow(CastingRecipe.REGISTRY);
        record Row(Item part, Item cast, String clayCast) {}
        for (Row row : new Row[] {
                new Row(ForgeweaveItems.PART_PLATING_HELMET.get(), ForgeweaveItems.CAST_PLATING_HELMET.get(), "cast_plating_helmet"),
                new Row(ForgeweaveItems.PART_PLATING_CHESTPLATE.get(), ForgeweaveItems.CAST_PLATING_CHESTPLATE.get(), "cast_plating_chestplate"),
                new Row(ForgeweaveItems.PART_PLATING_LEGGINGS.get(), ForgeweaveItems.CAST_PLATING_LEGGINGS.get(), "cast_plating_leggings"),
                new Row(ForgeweaveItems.PART_PLATING_BOOTS.get(), ForgeweaveItems.CAST_PLATING_BOOTS.get(), "cast_plating_boots"),
                new Row(ForgeweaveItems.PART_MAILLE.get(), ForgeweaveItems.CAST_MAILLE.get(), "cast_maille")}) {
            helper.assertTrue(recipes.stream().anyMatch(recipe -> recipe.result().is(row.cast())),
                    "no casting recipe moulds " + row.cast());
            helper.assertTrue(recipes.stream().anyMatch(recipe -> recipe.result().is(row.part())
                            && recipe.cast().isPresent() && recipe.cast().get().test(new ItemStack(row.cast()))),
                    "no casting recipe pours a metal through " + row.cast());
            helper.assertTrue(recipes.stream().anyMatch(recipe -> recipe.result().is(row.part())
                            && recipe.cast().isPresent()
                            && recipe.cast().get().test(new ItemStack(ForgeweaveItems.CLAY_CASTS.get(row.clayCast()).get()))),
                    "no casting recipe pours a metal through " + row.clayCast());
        }
        helper.succeed();
    }

    private static CastingBlockEntity rig(GameTestHelper helper, Fluid fluid) {
        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(fluid, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState().setValue(FaucetBlock.FACING, Direction.WEST));
        return helper.getBlockEntity(CASTING);
    }

    private static FaucetBlockEntity faucet(GameTestHelper helper) {
        return helper.getBlockEntity(FAUCET);
    }

    private static void insert(GameTestHelper helper, CastingBlockEntity casting, ItemStack stack) {
        Item expected = stack.getItem();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        casting.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(casting.input().is(expected), "expected the right-click to put the " + expected + " in");
    }
}
