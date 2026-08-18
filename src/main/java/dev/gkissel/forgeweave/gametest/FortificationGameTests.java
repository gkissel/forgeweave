package dev.gkissel.forgeweave.gametest;


import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.Fortification;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Issue #271's verification: the sharpening kit built at a real Part Builder and fortification driven
 * through the real Tool Station menu against the shipped {@code forgeweave:fortification} recipe JSON.
 *
 * <p>The tier ladder these tests read against is {@code ForgeweaveModifiers#TIER_TAGS}, and since
 * issue #433 its index is upstream's {@code HarvestLevels} number: the shipped materials sit on it
 * at wood 0 ({@code incorrect_for_wooden_tool}), stone 1 ({@code incorrect_for_stone_tool}), iron 2
 * ({@code incorrect_for_iron_tool}), steel 3 ({@code incorrect_for_diamond_tool}), cobalt 4
 * ({@code incorrect_for_netherite_tool}). Every tool built here is a <b>wood/wood/wood</b> pickaxe,
 * the bottom rung, so a raised tier can only have come from the kit.
 *
 * <p>What each test pins to upstream 1.12 ({@code tools/modifiers/ModFortify}, the pinned commit):
 *
 * <ul>
 *   <li>the kit's 2-ingot cost -- {@code SharpeningKit()}'s {@code super(Material.VALUE_Shard * 4)};
 *   <li>the tier being <b>set</b> to the kit material's --
 *       {@code applyEffect}'s {@code tag.setInteger(Tags.HARVESTLEVEL, stats.harvestLevel)};
 *   <li>costing no modifier slot -- the constructor's aspect set, which has no
 *       {@code FreeModifierAspect};
 *   <li>refusing the same material twice -- {@code ModifierAspect.SingleAspect#canApply};
 *   <li>a different material replacing rather than stacking -- {@code applyEffect}'s loop dropping
 *       every other {@code fortify*} entry, including the "really does lower it again" consequence.
 * </ul>
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class FortificationGameTests {

    private static final ResourceLocation COBALT = material("cobalt");
    private static final ResourceLocation IRON = material("iron");

    /**
     * The part half of the issue: the kit builds at the Part Builder for the same 2 ingots every head
     * part costs (upstream {@code Material.VALUE_Shard * 4} = 288 = 2 ingots), and comes out carrying
     * the material it was built from -- which is the whole payload fortification later reads.
     */
    @GameTest(template = "empty")
    public static void sharpeningKitBuildsForTwoIngots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos);
        PartBuilderMenu menu = new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)),
                blockEntity.findSideInventory());

        menu.getSlot(PartBuilderMenu.PATTERN_SLOT)
                .set(new ItemStack(ForgeweaveItems.PATTERN_SHARPENING_KIT.get()));
        // Cobblestone is 2 shard-units each and a head part costs 4 (PartBuilderRecipes), so exactly
        // two cover the kit with no change -- the same arithmetic the pickaxe head's own test uses.
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(new ItemStack(Items.COBBLESTONE, 2));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_SHARPENING_KIT.get()),
                "expected a sharpening kit, got " + output);
        helper.assertTrue(material("stone").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the kit's material to be forgeweave:stone, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));

        menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).getItem().isEmpty(),
                "two cobblestone must be exactly consumed by a 4-unit part");
        helper.assertTrue(menu.getSlot(PartBuilderMenu.CHANGE_SLOT).getItem().isEmpty(),
                "an exact-value craft must leave no shard change");
        helper.succeed();
    }

    /**
     * The mechanic: a cobalt kit plus a flint takes a wood pickaxe from the ladder's bottom rung to
     * its top, the entry serializes as {@code fortification.cobalt}, and taking the output spends one
     * of every loaded slot ({@code RecipeMatch.ItemCombination(1, kit, flint)}).
     */
    @GameTest(template = "empty")
    public static void fortifyingSetsTheTierToTheKitsMaterial(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "wood", "wood", "wood");
        helper.assertTrue(tierIndex(pickaxe) == 0,
                "a wood-headed pickaxe must start on the ladder's bottom rung, got " + tierIndex(pickaxe));

        ToolStationMenu menu = load(helper, player, pos, pickaxe, kit("cobalt"));
        ItemStack fortified = take(helper, player, menu);

        helper.assertTrue(tierIndex(fortified) == 4,
                "a cobalt kit must pin the tool to cobalt's rung (4), got " + tierIndex(fortified));
        ModifierEntry entry = ForgeweaveModifiers.entry(fortified, Fortification.idFor(COBALT));
        helper.assertTrue(entry != null && entry.level() == 5,
                "the fortification must serialize as forgeweave:fortification.cobalt carrying tier "
                        + "index 4 as level 4+1, got " + entry);
        for (int slot = ToolStationMenu.HEAD_SLOT; slot <= ToolStationMenu.HANDLE_SLOT; slot++) {
            helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(),
                    "taking the fortified tool must spend one of every loaded slot; slot " + slot
                            + " still holds " + menu.getSlot(slot).getItem());
        }
        helper.succeed();
    }

    /**
     * Upstream's aspect set is {@code SingleAspect + DataAspect + harvestOnly} -- no
     * {@code FreeModifierAspect}, so a fortification is free. Issue #344's slot accounting has to
     * agree, which it does through {@code Fortification.BEHAVIOR#occupiedSlots} rather than an
     * exception in {@code ForgeweaveModifiers#occupiedSlots}.
     */
    @GameTest(template = "empty")
    public static void fortifyingCostsNoModifierSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "wood", "wood", "wood");
        int before = ForgeweaveModifiers.freeSlots(pickaxe);

        ItemStack fortified = take(helper, player, load(helper, player, pos, pickaxe, kit("cobalt")));

        helper.assertTrue(ForgeweaveModifiers.freeSlots(fortified) == before,
                "a fortification must occupy no modifier slot: " + before + " -> "
                        + ForgeweaveModifiers.freeSlots(fortified));
        helper.assertTrue(before == ForgeweaveModifiers.DEFAULT_SLOTS,
                "the base tool must start at the default slot count, or this test proves nothing");
        helper.succeed();
    }

    /** Upstream {@code SingleAspect#canApply}: the same fortify twice is the error case. */
    @GameTest(template = "empty")
    public static void refortifyingWithTheSameMaterialIsRejected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "wood", "wood", "wood");
        ItemStack fortified = take(helper, player, load(helper, player, pos, pickaxe, kit("cobalt")));

        ToolStationMenu menu = load(helper, player, pos, fortified, kit("cobalt"));

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "re-fortifying with the same material must produce no output");
        helper.assertTrue(menu.rejection() != null,
                "a refused re-fortification must tell the player why");
        helper.succeed();
    }

    /**
     * Upstream {@code applyEffect}'s sweep: a second fortification of a <em>different</em> material
     * replaces the first outright rather than stacking beside it -- and because the tier is set, not
     * raised, going cobalt (4) then iron (2) really does lower the tool back down. That is upstream's
     * own behaviour, and it is the one place fortification differs from diamond/emerald's capped bump.
     */
    @GameTest(template = "empty")
    public static void adifferentMaterialReplacesTheFortificationAndCanLowerTheTier(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "wood", "wood", "wood");
        ItemStack cobalt = take(helper, player, load(helper, player, pos, pickaxe, kit("cobalt")));
        helper.assertTrue(tierIndex(cobalt) == 4, "precondition: cobalt-fortified sits at rung 4");

        ItemStack iron = take(helper, player, load(helper, player, pos, cobalt, kit("iron")));

        helper.assertTrue(tierIndex(iron) == 2,
                "an iron kit must set the tier to iron's rung (2) even coming down from cobalt's, got "
                        + tierIndex(iron));
        helper.assertTrue(ForgeweaveModifiers.entry(iron, Fortification.idFor(COBALT)) == null,
                "the previous fortification must be dropped, not kept beside the new one");
        helper.assertTrue(ForgeweaveModifiers.entry(iron, Fortification.idFor(IRON)) != null,
                "the new fortification must be recorded");
        helper.assertTrue(ForgeweaveModifiers.of(iron).size() == 1,
                "exactly one fortification may sit on a tool, got " + ForgeweaveModifiers.of(iron));
        helper.succeed();
    }

    /**
     * The tier a fortification pins survives a part exchange. Exchange rebuilds the vanilla
     * {@code tool} component from the new material set and then re-runs every modifier's bump
     * ({@code ModifierApplication#rebake}), so a fortified tool whose head is swapped for another
     * <em>low-tier</em> material must still mine at the kit's rung rather than falling back to the
     * new head's -- which is what "mining checks, tooltips and part exchange all agree" means.
     */
    @GameTest(template = "empty")
    public static void fortificationSurvivesAPartExchange(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "wood", "wood", "wood");
        ItemStack fortified = take(helper, player, load(helper, player, pos, pickaxe, kit("cobalt")));

        // Swap the head for stone (rung 1). Without the rebake replaying the fortification, the fresh
        // tool component would come back carrying stone's tag and the cobalt tier would be lost.
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, fortified);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT,
                ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "stone"));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        ItemStack swapped = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(swapped.isEmpty(), "expected the station to produce an exchanged tool");
        helper.assertTrue(tierIndex(swapped) == 4,
                "the fortified tier must survive a head swap to a lower-tier material, got "
                        + tierIndex(swapped));
        helper.succeed();
    }

    /**
     * The flint half of the cost is a real {@link dev.gkissel.forgeweave.modifier.ModifierRecipe}, so
     * that the cost stays datapack-tunable and JEI lists fortification -- but its modifier id is a
     * family marker that must never land on a tool. A flint on its own therefore does nothing at all:
     * {@code ModifierApplication#recipeFor} skips that one recipe.
     */
    @GameTest(template = "empty")
    public static void aFlintWithoutAKitDoesNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "wood", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, pickaxe);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT, new ItemStack(Items.FLINT));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a lone flint must never apply the forgeweave:fortification marker to a tool, got "
                        + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem());
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** The tool's current rung on {@code ForgeweaveModifiers#TIER_TAGS}, read off its deny-drops rule. */
    private static int tierIndex(ItemStack stack) {
        Tool component = stack.get(DataComponents.TOOL);
        if (component == null) {
            return -1;
        }
        return component.rules().stream()
                .filter(rule -> rule.speed().isEmpty())
                .findFirst()
                .map(rule -> ForgeweaveModifiers.tierIndexOf(rule.blocks()))
                .orElse(-1);
    }

    /** The station loaded for a fortification: the tool, the kit, and the flint. */
    private static ToolStationMenu load(GameTestHelper helper, Player player, BlockPos pos, ItemStack tool,
            ItemStack kit) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, tool);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT, kit);
        blockEntity.container().setItem(ToolStationMenu.HANDLE_SLOT, new ItemStack(Items.FLINT));

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        return menu;
    }

    private static ItemStack take(GameTestHelper helper, Player player, ToolStationMenu menu) {
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "expected the station to produce a fortified tool");
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }

    private static ItemStack kit(String materialName) {
        return ToolAssembly.part(ForgeweaveItems.PART_SHARPENING_KIT.get(), materialName);
    }

    private static ResourceLocation material(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }
}
