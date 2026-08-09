package dev.gkissel.forgeweave.menu;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolStationTabs.Pos;
import dev.gkissel.forgeweave.menu.ToolStationTabs.Tab;

/**
 * The Tool Station's menu: three input slots, an output slot, and the player's inventory, plus the
 * selected {@link ToolStationTabs.Tab} that decides where those input slots sit and what each one
 * accepts. Same "recompute output every broadcast, only consume on take" shape as
 * {@link dev.gkissel.forgeweave.menu.PartBuilderMenu}.
 *
 * <p>Assembly and repair (docs/SCOPE.md issues #10 and #11) share the same three input slots: which
 * recipe is running depends only on whether the first slot holds a head part or an assembled tool,
 * so the decision -- and the per-slot cost of taking the output -- lives entirely in
 * {@link ToolAssemblyRecipes}, unchanged by the tabs. All this class knows is "ask, show, and charge
 * what you're told".
 *
 * <h2>Tabs (issue #47)</h2>
 *
 * <p>The tab index is a synced {@link DataSlot} set from {@link #clickMenuButton} -- the vanilla
 * stonecutter/loom mechanism, the same one {@code StencilTableMenu} uses -- so the server is the
 * only writer and a dedicated server stays authoritative. Two things follow from it:
 *
 * <ul>
 *   <li><b>What each slot accepts.</b> {@link #accepts} reads the live tab, so the head slot takes
 *       only the selected tool's head part (or, on the repair tab, only an assembled tool) and shift-
 *       clicking obeys the same rule for free ({@code moveItemStackTo} consults {@code mayPlace}).
 *   <li><b>Where each slot is drawn.</b> {@link Slot#x}/{@link Slot#y} are final in modern
 *       Minecraft, so {@link #applyLayout} swaps the three input {@link Slot} objects for fresh ones
 *       at the tab's coordinates, keeping the same container, menu index and order. Positions are
 *       read only by the client's screen, but both sides rebuild identically -- the server from
 *       {@link #clickMenuButton}, the client from {@link #setData} when the data slot arrives -- so
 *       there is no third state to keep in sync.
 * </ul>
 *
 * <p>A tab switch deliberately leaves whatever is already in the slots alone: ejecting items on a
 * button press is how a menu loses a player's stack, and {@link ToolAssemblyRecipes} already refuses
 * to build anything from a mismatched set.
 *
 * <h2>Side inventory (issue #40's follow-up)</h2>
 *
 * <p>When the station has a qualifying neighbor ({@code ToolStationBlockEntity#findSideInventory}),
 * its {@link IItemHandler} is exposed as extra slots after the output slot via {@link
 * SideInventorySlots#create} -- same shape as {@link CraftingStationMenu}'s own side panel; see that
 * class's javadoc for the client/server slot-count handshake.
 */
public class ToolStationMenu extends StationMenu {
    public static final int CONTAINER_SLOTS = 4;
    public static final int HEAD_SLOT = 0;
    public static final int BINDING_SLOT = 1;
    public static final int HANDLE_SLOT = 2;
    public static final int OUTPUT_SLOT = 3;
    /** The three tab-positioned input slots; the output slot is fixed. */
    public static final int INPUT_SLOTS = 3;

    /** Upstream's own output-slot spot in {@code ContainerToolStation} ({@code SlotToolStationOut}). */
    private static final int OUTPUT_X = 124;
    private static final int OUTPUT_Y = 38;

    /** Vanilla's rename cap, and the same order of magnitude as upstream's 40-character field. */
    private static final int MAX_NAME_LENGTH = 50;

    /**
     * The tool-tab column's own geometry, mirrored from {@code client.ToolStationScreen} rather than
     * referenced: the menu package cannot depend on the client package, the same trade the info
     * panel width below already makes. Upstream's {@code GuiSideButtons} grid -- 18px buttons, 4px
     * spacing, {@code buttons.yOffset = beamC.h + buttonDecorationTop.h} = 9.
     */
    private static final int TAB_BUTTON_SIZE = 18;
    private static final int TAB_BUTTON_SPACING = 4;
    private static final int TAB_BUTTON_COLUMNS = 5;
    private static final int TAB_BUTTONS_Y = 9;

    /** Where the tool-tab column ends, so the side panel can start below it. */
    private static final int TAB_COLUMN_BOTTOM = TAB_BUTTONS_Y
            + tabRows() * TAB_BUTTON_SIZE + (tabRows() - 1) * TAB_BUTTON_SPACING;

    private static int tabRows() {
        return (ToolStationTabs.TABS.size() + TAB_BUTTON_COLUMNS - 1) / TAB_BUTTON_COLUMNS;
    }

    /**
     * Side-panel layout (issue #40's follow-up, re-placed by issue #88).
     *
     * <p>This side inventory is a Forgeweave addition -- upstream's {@code GuiToolStation} has no
     * {@code GuiSideInventory} at all, so there is no upstream placement to copy. Issue #79 put it
     * on the right, past both info panels, because the station's left edge is taken by its tool-tab
     * column. That was sound in isolation and wrong in practice: it pushed the station's total
     * chrome to 536px against a window that vanilla only guarantees to be 320 wide, so the panel
     * was still 124px off the right edge at a 427px window and 18px off at 640.
     *
     * <p>It now goes where the Crafting Station's and Part Builder's do -- upstream's left-hand
     * {@code rightSide = false} placement -- but <em>below</em> the tab column rather than beside
     * it, which is what the collision actually required. That column is one row of buttons ending
     * at y {@value #TAB_COLUMN_BOTTOM}; the panel starts a {@link #SIDE_PANEL_GAP}px gap under it,
     * stacked on the same edge the way upstream's own modules stack via {@code yOffset}.
     *
     * <p>The move costs nothing horizontally, which is what makes it the right answer rather than a
     * trade: the tab column already reaches x -110, and the panel only needs -122, so total chrome
     * goes 536 -> 428px -- within 12px of the 416px the station occupies with no side panel at all.
     * It fits a 480px window outright, where the old placement needed 640 and still overflowed.
     *
     * <p>Vertically the panel now has less room than a full-height one, so tall neighbours shed rows
     * ({@code client.SideInventoryPanel#visibleRows}) instead of drawing out through the bottom.
     */
    private static final int SIDE_PANEL_GAP = 4;
    public static final int SIDE_PANEL_X = SideInventorySlots.LEFT_SLOT_X;
    public static final int SIDE_PANEL_Y = TAB_COLUMN_BOTTOM + SIDE_PANEL_GAP + SideInventorySlots.SLOT_INSET;

    private final Container container;
    private final ContainerLevelAccess access;
    private final HolderLookup.Provider registries;
    private final DataSlot selectedTab = DataSlot.standalone();
    public final int sideInventorySlotCount;
    /** The side panel's own slots, kept so the client-side panel can lay them out and scroll them (issue #68). */
    public final List<SideInventorySlots.SideSlot> sideSlots;
    private String toolName = "";

    /** Client-side: constructed from the open-menu packet, which carries the side-inventory slot count and tab row. */
    public ToolStationMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SLOTS), ContainerLevelAccess.NULL, null,
                buf.readVarInt(), StationGroup.STREAM_CODEC.decode(buf));
    }

    /** Server-side: constructed by {@code ToolStationBlockEntity} with the block's real inventory and detected neighbor. */
    public ToolStationMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory) {
        this(containerId, playerInventory, container, access, sideInventory,
                sideInventory == null ? 0 : sideInventory.getSlots(), groupAt(access));
    }

    private ToolStationMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory, int sideInventorySlotCount, StationGroup stationGroup) {
        super(ForgeweaveMenus.TOOL_STATION.get(), containerId, stationGroup);
        checkContainerSize(container, CONTAINER_SLOTS);
        this.container = container;
        this.access = access;
        this.registries = playerInventory.player.level().registryAccess();
        this.sideInventorySlotCount = sideInventorySlotCount;
        container.startOpen(playerInventory.player);

        addDataSlot(selectedTab);
        selectedTab.set(ToolStationTabs.REPAIR);

        Tab tab = tab();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            Pos pos = tab.slots().get(i);
            addSlot(inputSlot(i, pos.x(), pos.y()));
        }
        addSlot(new OutputSlot(container, OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));

        this.sideSlots = SideInventorySlots.create(sideInventory, sideInventorySlotCount, SIDE_PANEL_X, SIDE_PANEL_Y);
        this.sideSlots.forEach(this::addSlot);
        layoutPlayerInventorySlots(playerInventory);
    }

    /**
     * The player's inventory sits 8px lower than a stock 166px-tall GUI's, because the Tool Station
     * panel is upstream's full 176x174 one (issue #47 restored the whole panel, chrome included,
     * where issue #43 had used a 176x166 crop of it).
     */
    private void layoutPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 92 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 150));
        }
    }

    private Slot inputSlot(int index, int x, int y) {
        return new Slot(container, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return accepts(index, stack);
            }
        };
    }

    /** What the currently selected tab lets the player put in slot {@code index}. */
    private boolean accepts(int index, ItemStack stack) {
        Tab tab = tab();
        if (tab.isRepair()) {
            return index == HEAD_SLOT
                    ? stack.getItem() instanceof ToolItem
                    : ToolAssemblyRecipes.isRepairItemFor(registries, container.getItem(HEAD_SLOT), stack);
        }
        return switch (index) {
            case HEAD_SLOT -> stack.is(tab.headPart().get());
            case BINDING_SLOT -> ToolAssemblyRecipes.isBindingPart(stack);
            default -> ToolAssemblyRecipes.isHandlePart(stack);
        };
    }

    /** The selected tab; the screen reads it to pick the sidebar highlight, icons and info text. */
    public Tab tab() {
        return ToolStationTabs.get(selectedTab.get());
    }

    public int getSelectedTab() {
        return selectedTab.get();
    }

    /** The current contents of the rename field, so the screen can restore it on reopen. */
    public String getToolName() {
        return toolName;
    }

    /**
     * Renames the assembled output, like a vanilla anvil: the name is applied to the freshly built
     * stack in {@link #updateResult}, so it travels to the client on the ordinary slot-sync packet
     * and there is nothing extra to keep consistent. Reached from the screen's text field over
     * {@link RenameStationItemPayload}, and validated here because that payload is player input.
     */
    public void setToolName(String name) {
        String filtered = StringUtil.filterText(name);
        this.toolName = filtered.length() > MAX_NAME_LENGTH ? filtered.substring(0, MAX_NAME_LENGTH) : filtered;
        broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (super.clickMenuButton(player, id)) {
            return true; // a station-group tab (issue #78), handled by StationMenu
        }
        if (id < 0 || id >= ToolStationTabs.TABS.size()) {
            return false;
        }
        selectedTab.set(id);
        applyLayout();
        updateResult();
        return true;
    }

    /** Client side: the tab arrives as a data-slot update, and the layout follows it. */
    @Override
    public void setData(int id, int data) {
        super.setData(id, data);
        applyLayout();
    }

    /** Rebuilds the three input slots at the selected tab's coordinates (see the class javadoc). */
    private void applyLayout() {
        Tab tab = tab();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            Pos pos = tab.slots().get(i);
            Slot slot = inputSlot(i, pos.x(), pos.y());
            slot.index = i;
            slots.set(i, slot);
        }
    }

    @Override
    public void broadcastChanges() {
        updateResult();
        super.broadcastChanges();
    }

    private void updateResult() {
        if (access == ContainerLevelAccess.NULL) {
            return; // client: the server pushes slot contents down instead of computing locally.
        }
        ItemStack output = resolve().map(ToolAssemblyRecipes.Result::output).orElse(ItemStack.EMPTY);
        if (!output.isEmpty() && !toolName.isBlank()) {
            output.set(DataComponents.CUSTOM_NAME, Component.literal(toolName));
        }
        slots.get(OUTPUT_SLOT).set(output);
    }

    private Optional<ToolAssemblyRecipes.Result> resolve() {
        return ToolAssemblyRecipes.resolve(registries,
                slots.get(HEAD_SLOT).getItem(), slots.get(BINDING_SLOT).getItem(), slots.get(HANDLE_SLOT).getItem());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        ItemStack result = stackInSlot.copy();
        int sideEnd = CONTAINER_SLOTS + sideInventorySlotCount;
        int playerInvEnd = sideEnd + 36;

        if (index < CONTAINER_SLOTS) { // head/binding/handle/output -> player inventory
            if (!moveItemStackTo(stackInSlot, CONTAINER_SLOTS, playerInvEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < sideEnd) { // side inventory -> player inventory
            if (!moveItemStackTo(stackInSlot, sideEnd, playerInvEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stackInSlot, HEAD_SLOT, INPUT_SLOTS, false)) {
            // The per-slot filters live in mayPlace, which moveItemStackTo already consults, so the
            // selected tab decides where (and whether) a shift-clicked stack lands.
            return ItemStack.EMPTY;
        }

        if (stackInSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stackInSlot.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stackInSlot);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ForgeweaveBlocks.TOOL_STATION.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    private final class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            // Recomputed rather than remembered from updateResult(): the inputs haven't changed
            // since the output was built, and a stateless read can't go stale.
            resolve().ifPresent(result -> {
                consume(HEAD_SLOT, result.headSlotUsed());
                consume(BINDING_SLOT, result.bindingSlotUsed());
                consume(HANDLE_SLOT, result.handleSlotUsed());
            });
            super.onTake(player, stack);
        }

        private void consume(int slotIndex, int count) {
            if (count > 0) {
                slots.get(slotIndex).remove(count);
                slots.get(slotIndex).setChanged();
            }
        }
    }
}
