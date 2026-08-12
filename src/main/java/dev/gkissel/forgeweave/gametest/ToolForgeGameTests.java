package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;

/**
 * docs/SCOPE.md M3 issue #152's verification: the Tool Forge is a Tool Station superset with two
 * differences, and both are tested here from the menu a player would actually be looking at.
 *
 * <ul>
 *   <li><b>The large-tool gate.</b> No real large tool exists until issues #157-#161 land, so the
 *       GameTest-only datapack ({@code src/gametest/resources}) puts the hatchet in
 *       {@code #forgeweave:large_tools} and these tests build a hatchet: it comes out of the Tool
 *       Forge and is refused, with a reason, at the Tool Station. The shipped tag is empty, which is
 *       exactly why this fixture exists -- the gate has to be proven before there is anything to gate.
 *   <li><b>The 5% repair discount</b> (a Forgeweave deviation, maintainer decision 2026-08-12).
 * </ul>
 *
 * <p>Assembly of a <em>small</em> tool at the forge is tested too, because "superset" is the actual
 * requirement and a gate that accidentally inverted would still pass the two tests above.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ToolForgeGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    @GameTest(template = "empty")
    public static void largeToolAssemblesAtTheForge(GameTestHelper helper) {
        ItemStack output = loadHatchetParts(helper, ForgeweaveBlocks.TOOL_FORGE.get()).getSlot(
                ToolStationMenu.OUTPUT_SLOT).getItem();

        helper.assertTrue(output.is(ForgeweaveItems.TOOL_HATCHET.get()),
                "the Tool Forge must assemble a large tool, got " + output);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void largeToolRejectedAtTheStationWithAReason(GameTestHelper helper) {
        ToolStationMenu menu = loadHatchetParts(helper, ForgeweaveBlocks.TOOL_STATION.get());

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "the Tool Station must not assemble a large tool, got "
                        + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem());

        // A silent empty output slot is indistinguishable from "you loaded the wrong parts"; the
        // issue's requirement is a *visible* reason, which is the string the screen puts in its info
        // panel. Asserted on the key rather than the English text so a wording change isn't a failure.
        Component rejection = menu.rejection();
        helper.assertTrue(rejection != null, "the Tool Station must say why it refused a large tool");
        helper.assertTrue(rejection.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                        && t.getKey().equals("gui.forgeweave.tool_station.needs_forge"),
                "expected the needs_forge message, got " + rejection);
        helper.succeed();
    }

    /** The classification itself, independent of either block: the hatchet is large, the pickaxe isn't. */
    @GameTest(template = "empty")
    public static void onlyTaggedToolsAreLarge(GameTestHelper helper) {
        helper.assertTrue(
                ToolAssemblyRecipes.isLargeToolHead(new ItemStack(ForgeweaveItems.PART_AXE_HEAD.get())),
                "the GameTest datapack tags the hatchet as a large tool");
        helper.assertFalse(
                ToolAssemblyRecipes.isLargeToolHead(new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get())),
                "an untagged tool must not be gated");
        helper.succeed();
    }

    /** Superset: everything the Tool Station assembles, the Tool Forge assembles too. */
    @GameTest(template = "empty")
    public static void forgeAlsoAssemblesSmallTools(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(POS, ForgeweaveBlocks.TOOL_FORGE.get());
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(POS);
        blockEntity.container().setItem(0, ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "stone"));
        blockEntity.container().setItem(1, ToolAssembly.part(ForgeweaveItems.PART_TOOL_BINDING.get(), "wood"));
        blockEntity.container().setItem(2, ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "wood"));

        ToolStationMenu menu = ToolAssembly.menu(helper, player, POS, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().is(ForgeweaveItems.TOOL_PICKAXE.get()),
                "the Tool Forge must still build the tools a Tool Station builds");
        helper.succeed();
    }

    /**
     * The repair discount, as arithmetic. A stone-headed tool's repair item is worth its head
     * material's head durability -- 120 -- at the Tool Station; at the Tool Forge the same repair
     * costs 5% less material, i.e. one item goes {@code ceil(120 / 0.95) = 127} as far.
     */
    @GameTest(template = "empty")
    public static void forgeRepairItemGoesFivePercentFurther(GameTestHelper helper) {
        int station = ToolAssemblyRecipes.repairIncrement(120, 128, 0, false);
        int forge = ToolAssemblyRecipes.repairIncrement(120, 128, 0, true);

        helper.assertTrue(station == 120, "expected the Tool Station's unchanged 120, got " + station);
        helper.assertTrue(forge == 127, "expected ceil(120 / 0.95) = 127 at the Tool Forge, got " + forge);
        helper.succeed();
    }

    /**
     * The same discount end to end: the Broken pickaxe {@code ToolStationGameTests} repairs to 1
     * damage with one cobblestone at a Tool Station comes back undamaged from a Tool Forge.
     *
     * <p>Both numbers are exact, not "less than": 127 damage, minus 120 plus stone's {@code cheap}
     * 5% ({@code 120 * 5 / 100 = 6}) = 1 at the station; minus 127 plus {@code 127 * 5 / 100 = 6} =
     * 0 (clamped) at the forge. Same tool, same single cobblestone, so the only variable is the block.
     */
    @GameTest(template = "empty")
    public static void forgeRepairsFurtherForTheSameMaterial(GameTestHelper helper) {
        helper.assertTrue(repairDamageAfterOneCobblestone(helper, ForgeweaveBlocks.TOOL_STATION.get()) == 1,
                "the Tool Station's repair must be unchanged by #152");
        helper.assertTrue(repairDamageAfterOneCobblestone(helper, ForgeweaveBlocks.TOOL_FORGE.get()) == 0,
                "the Tool Forge's 5% discount must show as a larger repair for the same one item");
        helper.succeed();
    }

    /** Breaks a stone/wood pickaxe outright, then repairs it with one cobblestone at {@code station}. */
    private static int repairDamageAfterOneCobblestone(GameTestHelper helper, Block station) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // Assembled at a Tool Station regardless of where it is repaired -- a pickaxe is not large,
        // so both blocks build the identical stack and the repair is the only thing under test.
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, POS, "stone", "wood", "wood");
        pickaxe.hurtAndBreak(1_000, helper.getLevel(), player, brokenItem -> {});
        helper.assertTrue(ToolItem.isBroken(pickaxe), "the test needs a Broken tool to repair");

        helper.setBlock(POS, station);
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(POS);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.COBBLESTONE, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, POS, blockEntity);
        menu.broadcastChanges();
        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(repaired.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected the repaired pickaxe, got " + repaired);
        return repaired.getDamageValue();
    }

    /**
     * #206 regression: the Tool Forge's own crafting recipe accepts a cobalt storage block as the
     * "M" ingredient -- the new {@code c:storage_blocks/cobalt} tag member -- exactly as it already
     * does for iron/gold/copper, proving the tag additions actually reach the recipe rather than just
     * existing on paper.
     */
    @GameTest(template = "empty")
    public static void forgeCraftsFromACobaltStorageBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack brick = new ItemStack(ForgeweaveBlocks.SEARED_BRICKS.get());
        ItemStack cobalt = new ItemStack(ForgeweaveBlocks.COBALT_BLOCK.get());
        ItemStack station = new ItemStack(ForgeweaveItems.TOOL_STATION.get());
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                brick, brick, brick,
                cobalt, station, cobalt,
                cobalt, ItemStack.EMPTY, cobalt));

        ItemStack crafted = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveItems.TOOL_FORGE.get()),
                "expected the Tool Forge crafted from a cobalt block, got " + crafted);
        helper.succeed();
    }

    /** Loads a full set of hatchet parts into a freshly placed {@code station} and returns its menu. */
    private static ToolStationMenu loadHatchetParts(GameTestHelper helper, Block station) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(POS, station);
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(POS);
        blockEntity.container().setItem(0, ToolAssembly.part(ForgeweaveItems.PART_AXE_HEAD.get(), "stone"));
        blockEntity.container().setItem(1, ToolAssembly.part(ForgeweaveItems.PART_TOOL_BINDING.get(), "wood"));
        blockEntity.container().setItem(2, ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "wood"));

        ToolStationMenu menu = ToolAssembly.menu(helper, player, POS, blockEntity);
        menu.broadcastChanges();
        return menu;
    }
}
