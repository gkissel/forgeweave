package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.gkissel.forgeweave.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import dev.gkissel.forgeweave.gametest.GameTestHolder;
import dev.gkissel.forgeweave.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.StencilTableBlockEntity;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.StencilTableMenu;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * The guide book's worked examples, followed step by step through the real recipes and stations
 * (issue #1105's tutorial chapters). Each test walks one page the way a player reading it would,
 * so a recipe or station change that breaks the page's instructions fails here.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class TutorialGameTests {

    private static final Identifier WOOD = Identifier.fromNamespaceAndPath(Forgeweave.MODID, "wood");

    /**
     * Playtest checklist 1.0.0, line 96, "Your First Pickaxe" ({@code book.forgeweave.intro.first_pickaxe}):
     * every step of the page through the shipped recipes and the real Stencil Table, Part Builder
     * and Tool Station menus.
     *
     * <p>Step one turns one log into four planks, two planks into four sticks, and two planks with
     * two sticks into four blank patterns. Step two stamps three of them. Step three carves the
     * three parts out of a second log: the head takes the whole log and pays back 4 wood shards,
     * and those shards pay for the handle and the binding, so the parts come to exactly one log
     * with nothing left over. That is the page's "it takes two logs". Step four assembles the
     * pickaxe.
     *
     * <p>The page quotes the finished pickaxe: 75 durability, 2.0 mining speed and 2.0 attack. The
     * durability is {@code (35 + 15) * 1.0 + 25 = 75}: the wood head's 35, plus the extra durability
     * the binding adds and the handle's own. {@code BookTutorialPagesTest} checks the page's text
     * against the same numbers; this test checks that the real stations build that pickaxe.
     */
    @GameTest(template = "empty")
    public static void yourFirstPickaxeWorksAsWritten(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // Step one: one log, then planks, sticks and blank patterns.
        ItemStack planks = craft(helper, 1, 1, List.of(new ItemStack(Items.OAK_LOG)));
        assertStack(helper, planks, Items.OAK_PLANKS, 4, "one log");
        ItemStack plank = new ItemStack(Items.OAK_PLANKS);
        ItemStack sticks = craft(helper, 1, 2, List.of(plank, plank));
        assertStack(helper, sticks, Items.STICK, 4, "two planks");
        ItemStack stick = new ItemStack(Items.STICK);
        ItemStack blanks = craft(helper, 2, 2, List.of(plank, stick, stick, plank));
        assertStack(helper, blanks, ForgeweaveItems.PATTERN_BLANK.get(), 4, "two planks and two sticks");

        // Step two: stamp a pickaxe head, a tool handle and a tool binding.
        StencilTableMenu stencil = openStencilTable(helper, new BlockPos(1, 1, 1), player);
        stencil.getSlot(StencilTableMenu.INPUT_SLOT).set(blanks);
        ItemStack headPattern = stamp(helper, stencil, player, ForgeweaveItems.PATTERN_PICKAXE_HEAD);
        ItemStack handlePattern = stamp(helper, stencil, player, ForgeweaveItems.PATTERN_TOOL_HANDLE);
        ItemStack bindingPattern = stamp(helper, stencil, player, ForgeweaveItems.PATTERN_TOOL_BINDING);
        helper.assertValueEqual(stencil.getSlot(StencilTableMenu.INPUT_SLOT).getItem().getCount(), 1,
                "blank patterns left after stamping three of four");

        // Step three: carve the three parts out of one log.
        PartBuilderMenu builder = openPartBuilder(helper, new BlockPos(3, 1, 1), player);
        builder.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(new ItemStack(Items.OAK_LOG));
        ItemStack head = carve(helper, builder, player, headPattern);
        ItemStack change = builder.getSlot(PartBuilderMenu.CHANGE_SLOT).getItem().copy();
        helper.assertTrue(change.is(ForgeweaveItems.SHARD.get()) && change.getCount() == 4,
                "expected the head to pay back 4 wood shards from the log, got " + change);
        builder.getSlot(PartBuilderMenu.CHANGE_SLOT).set(ItemStack.EMPTY);
        builder.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(change);
        ItemStack handle = carve(helper, builder, player, handlePattern);
        ItemStack binding = carve(helper, builder, player, bindingPattern);
        helper.assertTrue(builder.getSlot(PartBuilderMenu.MATERIAL_SLOT).getItem().isEmpty()
                        && builder.getSlot(PartBuilderMenu.CHANGE_SLOT).getItem().isEmpty(),
                "expected the three parts to use the one log exactly, with nothing left over");

        // Step four: the pickaxe at the Tool Station.
        BlockPos stationPos = new BlockPos(5, 1, 1);
        helper.setBlock(stationPos, ForgeweaveBlocks.TOOL_STATION.get());
        ToolStationBlockEntity station = helper.getBlockEntity(stationPos, ToolStationBlockEntity.class);
        station.container().setItem(ToolStationMenu.HEAD_SLOT, head);
        station.container().setItem(ToolStationMenu.BINDING_SLOT, binding);
        station.container().setItem(ToolStationMenu.HANDLE_SLOT, handle);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, stationPos, station);
        menu.broadcastChanges();
        ItemStack pickaxe = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertTrue(pickaxe.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected a pickaxe, got " + pickaxe);
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, pickaxe);

        ToolStats.Stats stats = pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null, "the assembled pickaxe carries no stats");
        helper.assertValueEqual(stats.miningSpeed(), 2.0F, "the pickaxe's mining speed");
        helper.assertValueEqual(stats.attackDamage(), 2.0F, "the pickaxe's attack");
        helper.assertValueEqual(pickaxe.getMaxDamage(), 75, "the pickaxe's durability, (35 + 15) * 1.0 + 25");
        helper.succeed();
    }

    /**
     * Playtest checklist 1.0.0, line 96, "Grout and Seared Bricks" ({@code book.forgeweave.smeltery.grout}):
     * the smallest smeltery the page counts bricks for really forms. That is one seared block under
     * a 1x1 interior and two courses of four wall positions with no corners, where the core, a tank
     * and a drain take three of the eight, leaving 6 plain seared brick blocks. The brick sum
     * itself, {@code 6 * 4 + 8 + 8 + 6 = 46}, is checked against the recipes in {@code
     * BookTutorialArithmeticTest}.
     */
    @GameTest(template = "smeltery")
    public static void theSmallestSmelteryTheGroutPageCountsForms(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        helper.setBlock(new BlockPos(2, 2, 1), ForgeweaveBlocks.SEARED_DRAIN.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        int plainBlocks = 0;
        for (int x = 0; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 0; z <= 2; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(ForgeweaveBlocks.SEARED_BRICKS.get())) {
                        plainBlocks++;
                    }
                }
            }
        }
        helper.assertValueEqual(plainBlocks, 6, "plain seared brick blocks in the smallest smeltery");
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos, SmelteryControllerBlockEntity.class);
        helper.assertTrue(core.isFormed(), "expected the book's smallest smeltery to form: " + core.lastResult().getString());
        helper.succeed();
    }

    private static ItemStack craft(GameTestHelper helper, int width, int height, List<ItemStack> items) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(width, height, items);
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input))
                .orElse(ItemStack.EMPTY);
    }

    private static void assertStack(GameTestHelper helper, ItemStack stack, Item item, int count, String from) {
        helper.assertTrue(stack.is(item) && stack.getCount() == count,
                "expected " + from + " to craft " + count + " " + item + ", got " + stack);
    }

    private static StencilTableMenu openStencilTable(GameTestHelper helper, BlockPos pos, Player player) {
        helper.setBlock(pos, ForgeweaveBlocks.STENCIL_TABLE.get());
        StencilTableBlockEntity blockEntity = helper.getBlockEntity(pos, StencilTableBlockEntity.class);
        return new StencilTableMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());
    }

    private static ItemStack stamp(GameTestHelper helper, StencilTableMenu menu, Player player,
            Supplier<? extends Item> pattern) {
        menu.clickMenuButton(player, StencilTableMenu.PATTERNS.indexOf(pattern));
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(StencilTableMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertTrue(output.is(pattern.get()), "expected the Stencil Table to stamp " + pattern.get() + ", got " + output);
        menu.getSlot(StencilTableMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }

    private static PartBuilderMenu openPartBuilder(GameTestHelper helper, BlockPos pos, Player player) {
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos, PartBuilderBlockEntity.class);
        return new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());
    }

    private static ItemStack carve(GameTestHelper helper, PartBuilderMenu menu, Player player, ItemStack pattern) {
        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(pattern.copy());
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertTrue(!output.isEmpty() && WOOD.equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the Part Builder to carve a wood part from " + pattern + ", got " + output);
        menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }

    private TutorialGameTests() {}
}
