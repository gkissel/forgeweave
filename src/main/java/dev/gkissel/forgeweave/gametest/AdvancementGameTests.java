package dev.gkissel.forgeweave.gametest;

import java.util.Collections;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.ponder.ForgeweavePonderHint;

/**
 * Issue #110's GameTest coverage: the advancement grants that have a real hook to exercise
 * headlessly (structure forming, a melted item, a modifier application), and the Ponder chat hint's
 * one-time-per-player behavior. {@code first_alloy} is covered in {@link SmelteryAlloyGameTests}
 * instead, next to the alloying pass that fires it (#98).
 *
 * <p>Issue #166's M3-17 tail (forge -> large tool -> emboss -> combat modifier) adds a structural
 * check that all four exist and parent correctly ({@link
 * #theM317ChainAdvancementsExistAndParentCorrectly}) plus real-hook coverage for the three that have
 * one -- "forge" has none, the same "own the item" idiom the root's own {@code smeltery_root} step
 * already uses with no GameTest of its own.
 *
 * <p>A real {@link ServerPlayer} (not the plain mock {@code Player} most other GameTests use) is
 * required throughout: {@code PlayerAdvancements} only tracks progress for a player registered with
 * the server, which {@link GameTestHelper#makeMockServerPlayerInLevel()} provides.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class AdvancementGameTests {

    @GameTest(template = "smeltery")
    public static void formingTheStructureGrantsBuildSmelteryAdvancement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        helper.useBlock(SmelteryGameTests.CORE_POS, player);

        helper.assertTrue(isGranted(helper, player, "smeltery/build_smeltery"),
                "expected forming the smeltery through a real player interaction to grant the advancement");
        helper.succeed();
    }

    /** {@link SmelteryControllerBlockEntity#insertForMelting(ItemStack, ServerPlayer)}'s own chosen "first melt" moment: a player-attributed insert into a formed, hot smeltery. */
    @GameTest(template = "smeltery")
    public static void insertingAMeltableItemIntoAHotSmelteryGrantsFirstMeltAdvancement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());

        ItemStack remaining = core.insertForMelting(new ItemStack(Items.IRON_ORE), player);

        helper.assertTrue(remaining.isEmpty(), "expected the iron ore to go into the hot smeltery");
        helper.assertTrue(isGranted(helper, player, "smeltery/first_melt"),
                "expected a player-attributed insert into a hot smeltery to grant the advancement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void applyingAModifierGrantsFirstModifierAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(!output.isEmpty(), "expected one redstone to apply haste before checking the advancement");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);

        helper.assertTrue(isGranted(helper, player, "smeltery/first_modifier"),
                "expected taking a modifier application's output to grant the advancement");
        helper.succeed();
    }

    /** #166: the four M3-17 steps exist and chain onto {@code first_modifier} in the documented order. */
    @GameTest(template = "empty")
    public static void theM317ChainAdvancementsExistAndParentCorrectly(GameTestHelper helper) {
        assertParent(helper, "smeltery/forge", "smeltery/first_modifier");
        assertParent(helper, "smeltery/large_tool", "smeltery/forge");
        assertParent(helper, "smeltery/emboss", "smeltery/large_tool");
        assertParent(helper, "smeltery/combat_modifier", "smeltery/emboss");
        helper.succeed();
    }

    /** #166's "large tool": {@link ToolAssembly#assembleAtForge} already calls {@code onTake} for us. */
    @GameTest(template = "empty")
    public static void assemblingALargeToolAtTheForgeGrantsLargeToolAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ToolAssemblyRecipes.Entry hammer = ToolAssembly.entryFor(ForgeweaveItems.TOOL_HAMMER.get());

        ItemStack assembled = ToolAssembly.assembleAtForge(helper, player, pos, hammer,
                Collections.nCopies(hammer.slotCount(), "stone"));

        helper.assertTrue(assembled.is(ForgeweaveItems.TOOL_HAMMER.get()),
                "expected the Tool Forge to build the hammer under test, got " + assembled);
        helper.assertTrue(isGranted(helper, player, "smeltery/large_tool"),
                "expected assembling a large tool at the Tool Forge to grant the advancement");
        helper.succeed();
    }

    /** #166's "emboss", same reagent set {@link EmbossingGameTests} exercises (iron head, slime/magma/gold). */
    @GameTest(template = "empty")
    public static void embossingAToolGrantsFirstEmbossmentAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, pickaxe);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT,
                ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "iron"));
        blockEntity.container().setItem(ToolStationMenu.HANDLE_SLOT, new ItemStack(Items.SLIME_BLOCK));
        blockEntity.container().setItem(ToolStationMenu.EXTRA_SLOT_1, new ItemStack(Items.MAGMA_BLOCK));
        blockEntity.container().setItem(ToolStationMenu.EXTRA_SLOT_2, new ItemStack(Items.GOLD_BLOCK));

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertTrue(!output.isEmpty(),
                "expected the station to produce an embossed tool before checking the advancement");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);

        helper.assertTrue(isGranted(helper, player, "smeltery/emboss"),
                "expected taking an embossed tool's output to grant the advancement");
        helper.succeed();
    }

    /** #166's "combat modifier": knockback (a piston), one of the eight {@code isCombatModifier} names. */
    @GameTest(template = "empty")
    public static void applyingAKnockbackModifierGrantsCombatModifierAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.PISTON, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(!output.isEmpty(), "expected one piston to apply knockback before checking the advancement");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);

        helper.assertTrue(isGranted(helper, player, "smeltery/first_modifier"),
                "expected taking a combat modifier's output to also grant first_modifier");
        helper.assertTrue(isGranted(helper, player, "smeltery/combat_modifier"),
                "expected taking a combat modifier application's output to grant the advancement");
        helper.succeed();
    }

    /** Server-side half of the one-time hint: the persisted flag ({@code ForgeweavePonderHint}) is what makes "once" durable. */
    @GameTest(template = "smeltery")
    public static void ponderHintIsShownOnlyOnceForANewPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        helper.assertFalse(hintShown(player), "expected a fresh player to not have seen the hint yet");

        helper.useBlock(SmelteryGameTests.CORE_POS, player);
        helper.assertTrue(hintShown(player), "expected the first controller interaction to record the hint as shown");

        // A second interaction (or a direct call, same as a second controller click) must stay a no-op.
        ForgeweavePonderHint.maybeShow(player);
        helper.assertTrue(hintShown(player), "expected a second call to remain a no-op rather than un-set the flag");
        helper.succeed();
    }

    private static boolean isGranted(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Asserts {@code path} exists and its declared parent is exactly {@code expectedParentPath}. */
    private static void assertParent(GameTestHelper helper, String path, String expectedParentPath) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path));
        helper.assertTrue(holder != null, "expected advancement " + path + " to exist");
        ResourceLocation expectedParent = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, expectedParentPath);
        helper.assertTrue(holder.value().parent().map(expectedParent::equals).orElse(false),
                "expected " + path + "'s parent to be " + expectedParentPath + ", got " + holder.value().parent());
    }

    private static boolean hintShown(ServerPlayer player) {
        return player.getPersistentData().getCompound("PlayerPersisted").getBoolean("forgeweave_ponder_hint_shown");
    }

    private AdvancementGameTests() {}
}
