package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.StationMenu;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.menu.ToolStationTabs;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * Covers docs/SCOPE.md M1 issue #10's verification: stone pickaxe head + wood binding + wood
 * handle parts -> a pickaxe whose stored durability derives from those materials' stats, and whose
 * component lists the three materials used. Plus issue #11's repair half: the same station takes a
 * Broken tool and the head material's repair item back to usable.
 *
 * <p>Expected durability is {@code ToolStats}'s ported 1.12 formula (see its javadoc), computed by
 * hand from the shipped material JSONs: stone's head durability (120) + wood's extra_durability
 * (15, as the binding) = 135; * wood's handle durability_modifier (1.0) = 135; + wood's handle
 * durability (25) = 160; * stone's head-scoped trait {@code forgeweave:cheapskate} (upstream's
 * {@code cheapskate}, issue #493) penalty (160 * 80 / 100) = 128.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ToolStationGameTests {

    @GameTest(template = "empty")
    public static void threePartsAssemblePickaxeWithDerivedStats(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.TOOL_STATION.get());

        ResourceLocation stone = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "stone");
        ResourceLocation wood = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood");

        ItemStack head = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());
        head.set(ForgeweaveDataComponents.MATERIAL.get(), stone);
        ItemStack binding = new ItemStack(ForgeweaveItems.PART_TOOL_BINDING.get());
        binding.set(ForgeweaveDataComponents.MATERIAL.get(), wood);
        ItemStack handle = new ItemStack(ForgeweaveItems.PART_TOOL_HANDLE.get());
        handle.set(ForgeweaveDataComponents.MATERIAL.get(), wood);

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, head);
        blockEntity.container().setItem(1, binding);
        blockEntity.container().setItem(2, handle);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolStationMenu menu = new ToolStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory(), blockEntity.isForge());
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected a pickaxe, got " + output);

        ToolMaterials materials = output.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        helper.assertTrue(ToolMaterials.of(ToolAssembly.pickaxeSlots(), List.of(stone, wood, wood)).equals(materials),
                "expected head=stone binding=wood handle=wood, got " + materials);

        Integer maxDamage = output.get(DataComponents.MAX_DAMAGE);
        helper.assertTrue(maxDamage != null && maxDamage == 128,
                "expected max durability 128 (((120 + 15) * 1.0 + 25) * 80 / 100), got " + maxDamage);

        // Simulates taking the crafted tool: all three parts are consumed (unlike the Part Builder's
        // reusable pattern, there's nothing here that survives the craft).
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "expected the head part to be consumed");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "expected the binding part to be consumed");
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "expected the handle part to be consumed");

        helper.succeed();
    }

    /**
     * docs/SCOPE.md M1 issue #11's repair verification. The pickaxe above (durability pool 128, stone
     * head) is broken outright, then repaired with a single cobblestone -- stone's {@code repair_item}
     * is {@code #minecraft:stone_tool_materials}.
     *
     * <p>One repair item is worth the head material's head durability, so 120 of the 127 damage comes
     * off ({@code ToolRepair}, ported from upstream 1.12's
     * {@code TinkersItem#calculateRepairAmount}/{@code #calculateRepair}) -- and nothing more: the
     * head's only trait is the head-scoped {@code forgeweave:cheapskate} (issue #493), which touches
     * durability, not repair, so the general {@code forgeweave:cheap} repair bonus does not apply to
     * this stone-headed-only tool. That leaves the tool at 7 damage, unbroken and usable again. The
     * trait's own tests are {@link TraitGameTests#cheapskateOnTheHeadAddsNoRepairBonus} and
     * {@link TraitGameTests#cheapOffTheHeadStillAddsARepairBonus}.
     */
    @GameTest(template = "empty")
    public static void repairRestoresDurabilityAndClearsBroken(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.hurtAndBreak(1_000, helper.getLevel(), player, brokenItem -> {});
        helper.assertTrue(ToolItem.isBroken(pickaxe), "the test needs a Broken tool to repair");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.COBBLESTONE, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(repaired.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected the repaired pickaxe, got " + repaired);
        helper.assertFalse(ToolItem.isBroken(repaired), "repair must clear the Broken state");
        helper.assertTrue(repaired.getDamageValue() == 7,
                "expected 127 - 120 (no repair bonus off a stone head alone) = 7 damage left after one "
                        + "cobblestone, got " + repaired.getDamageValue());
        helper.assertTrue(repaired.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()),
                "a repaired tool should harvest again");
        helper.assertTrue(ToolMaterials.of(ToolAssembly.pickaxeSlots(), List.of(
                        ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "stone"),
                        ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood"),
                        ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood")))
                        .equals(repaired.get(ForgeweaveDataComponents.TOOL_MATERIALS.get())),
                "repair must not disturb the tool's materials");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, repaired);
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "expected the repaired tool to leave the input slot");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "expected the one cobblestone to be consumed");

        helper.succeed();
    }

    /**
     * Issue #281's regression: a modifier that grows the durability pool (Diamond, {@code +500}) must
     * make the tool's repair proportionally faster too, not just its max durability bigger --
     * upstream {@code TinkersItem#calculateRepair}'s {@code min(10, actualDurability/baseDurability)}
     * term, which the Tool Station's repair recipe had dropped.
     *
     * <p>The pickaxe's base durability (128, {@code forgeweave:tool_stats}) and Diamond's +500 grow
     * the actual pool to 628, a {@code 628 / 128 = 4.90625} factor; one repair item is worth
     * {@code 120 * 4.90625 = 588.75}, cut by Diamond's own occupied slot ({@code 1} -> {@code 0.95}x)
     * to {@code 559.3125}, rounding up to 560 -- no further bonus, since a stone head alone carries
     * only the head-scoped {@code forgeweave:cheapskate} (issue #493), not the general repair-bonus
     * {@code forgeweave:cheap} -- versus the 120 an unmodified stone pickaxe repairs for
     * ({@link #repairRestoresDurabilityAndClearsBroken}).
     */
    @GameTest(template = "empty")
    public static void diamondModifiedToolRepairsProportionallyFaster(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ResourceLocation diamond = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "diamond");
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(diamond, 1)));
        int grownDurability = ForgeweaveModifiers.effectiveStats(pickaxe).durability();
        helper.assertTrue(grownDurability == 628, "expected 128 + Diamond's 500 = 628, got " + grownDurability);
        pickaxe.set(DataComponents.MAX_DAMAGE, grownDurability);
        pickaxe.setDamageValue(600);

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.COBBLESTONE, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(repaired.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected the repaired pickaxe, got " + repaired);
        helper.assertTrue(repaired.getDamageValue() == 40,
                "expected 600 - 560 = 40 damage left after one cobblestone, got " + repaired.getDamageValue());
        helper.succeed();
    }

    /**
     * Issue #281's other regression: upstream {@code TinkersItem#calculateRepair}'s modifier-count
     * repair penalty (1.00 / 0.95 / 0.90 / 0.85 for 0/1/2/3+ occupied, non-embossment modifier slots)
     * had been dropped entirely. Three modifiers with no durability effect of their own (haste,
     * searing, magnetic pull) isolate the penalty term: {@code ceil(120 * 0.85) = 102} -- no further
     * bonus, since a stone head alone carries only the head-scoped {@code forgeweave:cheapskate}
     * (issue #493), not the general repair-bonus {@code forgeweave:cheap} -- versus the 120 an
     * unmodified stone pickaxe repairs for ({@link #repairRestoresDurabilityAndClearsBroken}).
     */
    @GameTest(template = "empty")
    public static void threeOccupiedModifierSlotsRepairAtEightyFivePercent(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "haste"), 1),
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "searing"), 1),
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "magnetic_pull"), 1)));
        helper.assertTrue(ForgeweaveModifiers.occupiedSlots(pickaxe) == 3,
                "expected all three modifiers to occupy a slot, got " + ForgeweaveModifiers.occupiedSlots(pickaxe));
        pickaxe.setDamageValue(120); // below the 128 max, so the repair below isn't clamped by it

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.COBBLESTONE, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(repaired.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected the repaired pickaxe, got " + repaired);
        helper.assertTrue(repaired.getDamageValue() == 18,
                "expected 120 - 102 = 18 damage left after one cobblestone, got " + repaired.getDamageValue());
        helper.succeed();
    }

    /**
     * Issue #47's tab-based menu: selecting a tool's tab through the vanilla menu-button path moves
     * the three input slots to that tool's layout, narrows what each one accepts to that tool's parts,
     * and still assembles the same tool the pre-tab menu did.
     */
    @GameTest(template = "empty")
    public static void pickaxeTabRepositionsSlotsAndStillAssembles(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.TOOL_STATION.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);

        helper.assertTrue(menu.getSelectedTab() == ToolStationTabs.REPAIR,
                "a freshly opened station starts on the repair tab, as upstream's does");

        int pickaxeTab = ToolStationTabs.TABS.indexOf(ToolStationTabs.TABS.stream()
                .filter(tab -> !tab.isRepair() && tab.tool() == ForgeweaveItems.TOOL_PICKAXE.get())
                .findFirst().orElseThrow());
        helper.assertTrue(menu.clickMenuButton(player, pickaxeTab), "selecting the pickaxe tab must be accepted");
        helper.assertFalse(menu.clickMenuButton(player, ToolStationTabs.TABS.size()),
                "an out-of-range tab index must be rejected server-side");

        ToolStationTabs.Pos headPos = ToolStationTabs.get(pickaxeTab).slots().get(ToolStationMenu.HEAD_SLOT);
        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).x == headPos.x()
                        && menu.getSlot(ToolStationMenu.HEAD_SLOT).y == headPos.y(),
                "the head slot must move to the pickaxe layout's position");

        ItemStack head = ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "stone");
        ItemStack wrongHead = ToolAssembly.part(ForgeweaveItems.PART_SHOVEL_HEAD.get(), "stone");
        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).mayPlace(head),
                "the pickaxe tab's head slot must accept a pickaxe head");
        helper.assertFalse(menu.getSlot(ToolStationMenu.HEAD_SLOT).mayPlace(wrongHead),
                "the pickaxe tab's head slot must reject another tool's head");

        menu.getSlot(ToolStationMenu.HEAD_SLOT).set(head);
        menu.getSlot(ToolStationMenu.BINDING_SLOT).set(ToolAssembly.part(ForgeweaveItems.PART_TOOL_BINDING.get(), "wood"));
        menu.getSlot(ToolStationMenu.HANDLE_SLOT).set(ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "wood"));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.TOOL_PICKAXE.get()),
                "assembly through the pickaxe tab must still produce a pickaxe, got " + output);
        helper.succeed();
    }

    /**
     * Issue #378, upstream {@code GuiToolStation:296-301}: a part of exactly the shape the slot wants
     * whose material this world has no definition for. {@code ToolAssemblyRecipes#assemble} gives up
     * on that silently -- the output slot stays empty while the components panel lists every part as
     * satisfied -- so before #378 the station's only answer was to look broken.
     *
     * <p>Also pins the classification: upstream reaches this one through {@code warning(...)} rather
     * than {@code error(...)}, because nothing was attempted and refused, the loadout was simply never
     * going to build anything.
     */
    @GameTest(template = "empty")
    public static void aPartOfAnUnknownMaterialSaysSoRatherThanRefusingSilently(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.TOOL_STATION.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);

        int pickaxeTab = ToolStationTabs.TABS.indexOf(ToolStationTabs.TABS.stream()
                .filter(tab -> !tab.isRepair() && tab.tool() == ForgeweaveItems.TOOL_PICKAXE.get())
                .findFirst().orElseThrow());
        helper.assertTrue(menu.clickMenuButton(player, pickaxeTab), "selecting the pickaxe tab must be accepted");

        // A real pickaxe head -- the slot's own filter accepts it -- made of a material no datapack
        // defines, which is what a shard or part from a since-removed pack carries.
        ItemStack head = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());
        head.set(ForgeweaveDataComponents.MATERIAL.get(),
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "unobtainium"));
        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).mayPlace(head),
                "the test needs a part the slot itself accepts -- the material is the only thing wrong");
        menu.getSlot(ToolStationMenu.HEAD_SLOT).set(head);
        menu.getSlot(ToolStationMenu.BINDING_SLOT).set(ToolAssembly.part(ForgeweaveItems.PART_TOOL_BINDING.get(), "wood"));
        menu.getSlot(ToolStationMenu.HANDLE_SLOT).set(ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "wood"));
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "the station cannot build anything from a material it does not know");
        StationMenu.Rejection rejection = menu.rejection();
        helper.assertTrue(rejection != null, "and it must say why rather than leaving the slot mysteriously empty");
        helper.assertTrue(rejection.message().getContents() instanceof TranslatableContents t
                        && t.getKey().equals("gui.forgeweave.tool_station.wrong_material_part"),
                "expected the wrong_material_part message, got " + rejection.message());
        helper.assertTrue(rejection.warning(),
                "upstream reaches this through warning(), not error(): nothing was refused, the "
                        + "loadout was never going to craft (GuiToolStation:299)");

        // A part of a material that does exist is not a wrong-material part.
        menu.getSlot(ToolStationMenu.HEAD_SLOT).set(ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "stone"));
        menu.broadcastChanges();
        helper.assertTrue(menu.rejection() == null, "a fully valid loadout has nothing to complain about");
        helper.succeed();
    }

    /**
     * The repair tab is the same three slots with the other filter: a tool in the middle and its head
     * material's repair item beside it (issue #47), reaching the unchanged repair path from #11.
     */
    @GameTest(template = "empty")
    public static void repairTabAcceptsToolsAndTheirRepairItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.hurtAndBreak(1_000, helper.getLevel(), player, brokenItem -> {});

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        helper.assertTrue(menu.clickMenuButton(player, ToolStationTabs.REPAIR), "the repair tab must be selectable");

        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).mayPlace(pickaxe),
                "the repair tab's first slot must accept an assembled tool");
        helper.assertFalse(menu.getSlot(ToolStationMenu.HEAD_SLOT)
                        .mayPlace(ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "stone")),
                "the repair tab's first slot must reject loose parts");

        menu.getSlot(ToolStationMenu.HEAD_SLOT).set(pickaxe);
        helper.assertTrue(menu.getSlot(ToolStationMenu.BINDING_SLOT).mayPlace(new ItemStack(Items.COBBLESTONE)),
                "with a stone-headed tool loaded, the repair slots must accept cobblestone");
        // Dirt, not diamond (issue #106): diamond became a valid modifier reagent (forgeweave:diamond).
        helper.assertFalse(menu.getSlot(ToolStationMenu.BINDING_SLOT).mayPlace(new ItemStack(Items.DIRT)),
                "the repair slots must reject an item that is not the head material's repair item");

        menu.getSlot(ToolStationMenu.BINDING_SLOT).set(new ItemStack(Items.COBBLESTONE, 1));
        menu.broadcastChanges();
        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(repaired.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected the repaired pickaxe, got " + repaired);
        helper.assertFalse(ToolItem.isBroken(repaired), "repair through the repair tab must clear the Broken state");
        helper.succeed();
    }

    /**
     * The rename field (issue #47) reaches the output stack server-side, exactly like a vanilla anvil,
     * and the menu -- not the client -- is what filters and length-caps the text.
     */
    @GameTest(template = "empty")
    public static void renamingTheOutputSetsItsCustomName(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.TOOL_STATION.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "stone"));
        blockEntity.container().setItem(1, ToolAssembly.part(ForgeweaveItems.PART_TOOL_BINDING.get(), "wood"));
        blockEntity.container().setItem(2, ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "wood"));

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.setToolName("Rockbiter");

        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        Component name = output.get(DataComponents.CUSTOM_NAME);
        helper.assertTrue(name != null && "Rockbiter".equals(name.getString()),
                "expected the output to be renamed, got " + name);

        menu.setToolName("");
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().get(DataComponents.CUSTOM_NAME) == null,
                "clearing the field must leave the output with its default name");
        helper.succeed();
    }

    /**
     * Issue #40's follow-up: the side-inventory panel extended from the Crafting Station to the Tool
     * Station (and the Part Builder, {@code PartBuilderGameTests}'s own coverage isn't duplicated
     * here). Same shape as {@code CraftingStationGameTests#adjacentChestInventoryIsExposedThroughTheMenu}.
     */
    @GameTest(template = "empty")
    public static void adjacentChestInventoryIsExposedThroughTheMenu(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = stationPos.east();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(stationPos, ForgeweaveBlocks.TOOL_STATION.get());

        if (!(helper.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            helper.fail("expected a chest block entity next to the station");
            return;
        }
        chest.setItem(0, new ItemStack(Items.DIAMOND));

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(stationPos);
        ToolStationMenu menu = new ToolStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(stationPos)), blockEntity.findSideInventory(), blockEntity.isForge());

        helper.assertTrue(menu.sideInventorySlotCount > 0, "expected the adjacent chest to be detected as a side inventory");

        boolean foundDiamond = false;
        for (int i = ToolStationMenu.CONTAINER_SLOTS; i < ToolStationMenu.CONTAINER_SLOTS + menu.sideInventorySlotCount; i++) {
            if (menu.getSlot(i).getItem().is(Items.DIAMOND)) {
                foundDiamond = true;
                break;
            }
        }
        helper.assertTrue(foundDiamond, "expected the chest's diamond to be visible through a side-inventory slot");

        helper.succeed();
    }

    /**
     * Parity audit 2026-08-18 T2 (issue #434): upstream {@code ContainerToolStation#getInputs} feeds
     * {@code TinkersItem#repair} every free slot, and {@code Material#matches} sums the repair item
     * across all of them. Cobblestone in slots 3 and 5 alone -- neither of the two slots the
     * pre-#434 resolver read -- must repair the broken pickaxe, and taking it must spend both
     * (127 damage; one cobblestone restores 120, the second the rest -- see
     * {@link #repairRestoresDurabilityAndClearsBroken} for why a stone head alone gets no bonus).
     */
    @GameTest(template = "empty")
    public static void repairPoolsAllFiveFreeSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.hurtAndBreak(1_000, helper.getLevel(), player, brokenItem -> {});
        helper.assertTrue(ToolItem.isBroken(pickaxe), "the test needs a Broken tool to repair");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(3, new ItemStack(Items.COBBLESTONE, 1));
        blockEntity.container().setItem(5, new ItemStack(Items.COBBLESTONE, 1));

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        ItemStack repaired = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(repaired.is(ForgeweaveItems.TOOL_PICKAXE.get()),
                "cobblestone in free slots 3 and 5 must repair, got " + repaired);
        helper.assertTrue(repaired.getDamageValue() == 0,
                "two cobblestone must fully repair 127 damage, got " + repaired.getDamageValue());

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, repaired);
        helper.assertTrue(menu.getSlot(3).getItem().isEmpty(), "slot 3's cobblestone must be spent");
        helper.assertTrue(menu.getSlot(5).getItem().isEmpty(), "slot 5's cobblestone must be spent");
        helper.succeed();
    }

    /**
     * Parity audit 2026-08-18 T11 (issue #443): upstream {@code ContainerToolStation#renameTool}
     * ({@code ContainerToolStation.java:281-299}) is a recipe of its own, sitting between modify and
     * build in {@code onCraftMatrixChanged}'s chain -- a tool alone in the first slot plus a name
     * typed in the field produces a renamed copy, with no repair item, reagent or part loaded.
     * Before this the station only stamped {@link ToolStationMenu#setToolName}'s text onto an output
     * some <em>other</em> recipe had already produced, so a tool sitting by itself could never be
     * renamed.
     */
    @GameTest(template = "empty")
    public static void renamesAToolWithNoOtherInputs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a lone tool with no name typed is not a recipe");

        menu.setToolName("Digger");

        ItemStack renamed = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(renamed.is(ForgeweaveItems.TOOL_PICKAXE.get()),
                "a lone tool plus a typed name must produce a renamed copy, got " + renamed);
        helper.assertTrue("Digger".equals(renamed.getHoverName().getString()),
                "expected the typed name on the output, got " + renamed.getHoverName().getString());
        helper.assertTrue(renamed.getDamageValue() == pickaxe.getDamageValue(),
                "renaming must not repair or otherwise disturb the tool");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, renamed);
        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).getItem().isEmpty(),
                "taking the renamed tool must spend the one in the input slot");

        // Upstream's own guard: a name equal to what the tool already shows is not a rename.
        blockEntity.container().setItem(0, renamed);
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "retyping the name a tool already carries must produce nothing");
        helper.succeed();
    }

    /**
     * Parity audit 2026-08-18 T11 (issue #443): upstream's {@code ToolStationTextPacket} echoes the
     * typed name to every player with a Tool Station container open at the same station, and
     * {@code ContainerToolStation#syncWithOtherContainer} ({@code ContainerToolStation.java:80-88})
     * seeds a newly opened one from both the name and the tool selection a container already there
     * is holding. Before this both were per-menu, so two players at one station saw two different
     * fields and two different tabs over one shared output slot.
     */
    @GameTest(template = "empty")
    public static void typedNameAndTabReachOtherPlayersAtTheSameStation(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, first, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);

        ToolStationMenu firstMenu = ToolAssembly.menu(helper, first, pos, blockEntity);
        first.containerMenu = firstMenu;
        ToolStationMenu secondMenu = ToolAssembly.menu(helper, second, pos, blockEntity);
        second.containerMenu = secondMenu;

        firstMenu.setToolName("Digger");
        helper.assertTrue("Digger".equals(secondMenu.getToolName()),
                "the typed name must reach the other player's menu, got '" + secondMenu.getToolName() + "'");

        int shovelTab = ToolStationTabs.indexOfTool(ForgeweaveItems.TOOL_SHOVEL.get());
        helper.assertTrue(shovelTab >= 0, "the shovel must have a tab at all");
        firstMenu.clickMenuButton(first, shovelTab);
        helper.assertTrue(secondMenu.getSelectedTab() == shovelTab,
                "the selected tab must reach the other player's menu, got " + secondMenu.getSelectedTab());

        // A menu opened afterwards seeds itself from the ones already there.
        ServerPlayer third = helper.makeMockServerPlayerInLevel();
        ToolStationMenu thirdMenu = ToolAssembly.menu(helper, third, pos, blockEntity);
        helper.assertTrue("Digger".equals(thirdMenu.getToolName()),
                "a menu opened later must seed its name from the station, got '" + thirdMenu.getToolName() + "'");
        helper.assertTrue(thirdMenu.getSelectedTab() == shovelTab,
                "a menu opened later must seed its tab from the station, got " + thirdMenu.getSelectedTab());
        helper.succeed();
    }

    /**
     * Issue #722: shift-clicking an item in the side chest feeds the station's own input slots
     * first. Upstream 1.12 ({@code ContainerMultiModule#transferStackInSlot}, Mantle) moves a
     * sub-container (side chest) stack into the tile inventory before the player inventory.
     */
    @GameTest(template = "empty")
    public static void shiftClickingTheSideChestFeedsTheStationInputSlots(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = stationPos.east();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 4), "stone", "wood", "wood");

        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(stationPos, ForgeweaveBlocks.TOOL_STATION.get());
        ChestBlockEntity chest = helper.getBlockEntity(chestPos);
        chest.setItem(0, pickaxe.copy());

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(stationPos);
        ToolStationMenu menu = new ToolStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(stationPos)), blockEntity.findSideInventory(), blockEntity.isForge());
        helper.assertTrue(menu.sideInventorySlotCount > 0, "expected the adjacent chest to be detected as a side inventory");

        int chestSlot = -1;
        for (int i = ToolStationMenu.CONTAINER_SLOTS; i < ToolStationMenu.CONTAINER_SLOTS + menu.sideInventorySlotCount; i++) {
            if (!menu.getSlot(i).getItem().isEmpty()) {
                chestSlot = i;
                break;
            }
        }
        helper.assertTrue(chestSlot >= 0, "expected the chest's pickaxe to be visible through a side-inventory slot");

        menu.clicked(chestSlot, 0, ClickType.QUICK_MOVE, player);

        helper.assertTrue(menu.getSlot(ToolStationMenu.HEAD_SLOT).getItem().is(ForgeweaveItems.TOOL_PICKAXE.get()),
                "expected the pickaxe in the repair tab's tool slot, got " + menu.getSlot(ToolStationMenu.HEAD_SLOT).getItem());
        helper.assertTrue(!player.getInventory().contains(pickaxe),
                "expected the pickaxe to skip the player inventory");

        helper.succeed();
    }
}
