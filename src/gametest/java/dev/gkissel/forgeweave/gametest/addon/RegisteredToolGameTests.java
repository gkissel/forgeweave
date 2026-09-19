package dev.gkissel.forgeweave.gametest.addon;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.block.StencilTableBlockEntity;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.menu.StencilTableMenu;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.menu.ToolStationTabs;

/**
 * Issue #1066's proof: the tool and part {@link GameTestAddon} registers through the public entry
 * point are usable end to end. Everything below goes through the real menus, the way the tests for
 * Forgeweave's own tools do, so a pass here means the station itself accepts a foreign id rather
 * than that some parallel path does.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class RegisteredToolGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static final ResourceLocation IRON = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "iron");
    private static final ResourceLocation WOOD = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood");

    /** The addon's tool as the station's table holds it. */
    private static ToolAssemblyRecipes.Entry entry() {
        return ToolAssemblyRecipes.ENTRIES.stream()
                .filter(candidate -> candidate.constants().id().equals(GameTestAddon.TOOL_ID.toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the registered tool is missing from the station's table: " + ToolAssemblyRecipes.ENTRIES.size()
                                + " rows"));
    }

    private static ItemStack part(ResourceLocation material, Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), material);
        return stack;
    }

    /** The Stencil Table offers a registered pattern, after every one Forgeweave ships. */
    @GameTest(template = "empty")
    public static void aRegisteredPatternStampsAtTheStencilTable(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(POS, ForgeweaveBlocks.STENCIL_TABLE.get());
        StencilTableBlockEntity blockEntity = helper.getBlockEntity(POS);
        StencilTableMenu menu = new StencilTableMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(POS)),
                blockEntity.findSideInventory());

        int index = StencilTableMenu.PATTERNS.indexOf(GameTestAddon.PATTERN);
        helper.assertTrue(index >= 0, "the registered pattern is missing from the Stencil Table's grid");
        helper.assertTrue(index == StencilTableMenu.PATTERNS.size() - 1,
                "a registered pattern belongs after every shipped one, got index " + index);

        menu.getSlot(StencilTableMenu.INPUT_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()));
        menu.clickMenuButton(player, index);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(StencilTableMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(GameTestAddon.PATTERN.get()),
                "expected the registered pattern in the output, got " + output);
        helper.succeed();
    }

    /** The Part Builder stamps the registered part from that pattern, at the cost the addon named. */
    @GameTest(template = "empty")
    public static void aRegisteredPartStampsAtThePartBuilder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(POS, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(POS);
        PartBuilderMenu menu = new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(POS)),
                blockEntity.findSideInventory());

        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(GameTestAddon.PATTERN.get()));
        ItemStack shards = new ItemStack(ForgeweaveItems.SHARD.get(),
                GameTestAddon.BLADE_COST / PartBuilderRecipes.SHARD_VALUE);
        shards.set(ForgeweaveDataComponents.MATERIAL.get(), IRON);
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(shards);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(GameTestAddon.BLADE.get()),
                "expected the registered part, got " + output);
        helper.assertTrue(IRON.equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the part to carry the material it was stamped from, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));

        menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).getItem().isEmpty(),
                "expected the registered part's exact-value craft to consume every shard");
        helper.succeed();
    }

    /**
     * The Tool Station assembles the registered tool from the registered part and one of
     * Forgeweave's own, and what it hands back survives being written to disk and read again.
     */
    @GameTest(template = "empty")
    public static void aRegisteredToolAssemblesAndSurvivesASaveRoundTrip(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolAssemblyRecipes.Entry entry = entry();
        helper.assertTrue(entry.slotCount() == 2,
                "the registered tool takes two parts, the table says " + entry.slotCount());

        helper.setBlock(POS, ForgeweaveBlocks.TOOL_STATION.get());
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(POS);
        List<ResourceLocation> materials = List.of(IRON, WOOD);
        for (int slot = 0; slot < ToolStationMenu.INPUT_SLOTS; slot++) {
            blockEntity.container().setItem(slot, slot < entry.slotCount()
                    ? part(materials.get(slot), entry.part(slot))
                    : ItemStack.EMPTY);
        }

        ToolStationMenu menu = new ToolStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(POS)),
                blockEntity.findSideInventory(), blockEntity.isForge());
        menu.broadcastChanges();
        ItemStack tool = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, tool);

        helper.assertTrue(tool.is(GameTestAddon.TOOL.get()),
                "expected the registered tool out of the station, got " + tool);
        helper.assertTrue(tool.getMaxDamage() > 0,
                "an assembled tool has durability from its parts, got " + tool.getMaxDamage());

        Tag saved = tool.save(helper.getLevel().registryAccess());
        ItemStack reloaded = ItemStack.parse(helper.getLevel().registryAccess(), (CompoundTag) saved)
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(reloaded.is(GameTestAddon.TOOL.get()),
                "the registered tool must read back as itself, got " + reloaded);
        helper.assertTrue(reloaded.getMaxDamage() == tool.getMaxDamage(),
                "and keep the durability its materials gave it, got " + reloaded.getMaxDamage());
        helper.assertTrue(tool.getComponents().equals(reloaded.getComponents()),
                "and every component it was assembled with");
        helper.succeed();
    }

    /**
     * A registered tool gets a Tool Station tab whose slots come off its part count, one per part
     * and no two in the same place. Built-in tabs are hand-laid and pinned elsewhere
     * ({@code ToolStationTabsTest}); this is the other half of that rule.
     */
    @GameTest(template = "empty")
    public static void aRegisteredToolGetsADerivedTab(GameTestHelper helper) {
        ToolAssemblyRecipes.Entry entry = entry();
        ToolStationTabs.Tab tab = ToolStationTabs.TABS.stream()
                .filter(candidate -> candidate.entry() == entry)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the registered tool has no Tool Station tab"));

        helper.assertTrue(ToolStationTabs.TABS.indexOf(tab) == ToolStationTabs.TABS.size() - 1,
                "a registered tool's tab belongs after every shipped one");
        helper.assertTrue(tab.slots().size() == entry.slotCount(),
                "a derived tab has one slot per part, got " + tab.slots().size());
        helper.assertTrue(!tab.slots().get(0).equals(tab.slots().get(1)),
                "and no two of them in the same place");
        helper.succeed();
    }
}
