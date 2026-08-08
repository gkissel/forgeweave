package dev.gkissel.forgeweave.menu;

import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

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
 */
public class ToolStationMenu extends AbstractContainerMenu {
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

    private final Container container;
    private final ContainerLevelAccess access;
    private final HolderLookup.Provider registries;
    private final DataSlot selectedTab = DataSlot.standalone();
    private String toolName = "";

    /** Client-side: constructed from the open-menu packet, with a throwaway local container. */
    public ToolStationMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SLOTS), ContainerLevelAccess.NULL);
    }

    /** Server-side: constructed by {@code ToolStationBlockEntity} with the block's real inventory. */
    public ToolStationMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access) {
        super(ForgeweaveMenus.TOOL_STATION.get(), containerId);
        checkContainerSize(container, CONTAINER_SLOTS);
        this.container = container;
        this.access = access;
        this.registries = playerInventory.player.level().registryAccess();
        container.startOpen(playerInventory.player);

        addDataSlot(selectedTab);
        selectedTab.set(ToolStationTabs.REPAIR);

        Tab tab = tab();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            Pos pos = tab.slots().get(i);
            addSlot(inputSlot(i, pos.x(), pos.y()));
        }
        addSlot(new OutputSlot(container, OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));

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

        if (index < CONTAINER_SLOTS) {
            if (!moveItemStackTo(stackInSlot, CONTAINER_SLOTS, slots.size(), true)) {
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
