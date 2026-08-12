package dev.gkissel.forgeweave.menu;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.registries.DeferredItem;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The Stencil Table's menu (docs/SCOPE.md M1 issue #44): a blank-pattern input slot, an output
 * slot, and a fixed selection of the five part patterns. Selecting one (via {@link
 * #clickMenuButton}) determines what the output slot shows; taking the output consumes one blank
 * from the input slot -- one-way, matching upstream 1.12's stencil-shaping step ({@code
 * ContainerStencilTable}/{@code SlotStencil}/{@code TileStencilTable}, NOTICE.md). Unlike upstream,
 * where each material variant of a part is its own registered stencil-table candidate (per-material
 * patterns), Forgeweave's five part patterns are plain, material-less items (ADR-0002-adjacent
 * design: see {@code ForgeweaveItems}), so the candidate list here is simply {@link #PATTERNS} --
 * no dynamic registry, no custom network packet. The selection index is a synced {@link DataSlot}
 * set from {@link #clickMenuButton}, exactly the vanilla stonecutter/loom mechanism ({@code
 * StonecutterMenu#clickMenuButton}) the screen's button clicks route through.
 */
public class StencilTableMenu extends StationMenu {
    public static final int CONTAINER_SLOTS = 2;
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    /**
     * Fixed, ordered candidate list for the selection buttons (docs/SCOPE.md M1 issue #44 brief: "the
     * five part patterns"; M3 issue #151 adds the roster's 17 more -- the sidebar grid in {@code
     * StencilTableScreen} sizes itself off {@link #PATTERNS}' length, so no layout change was needed).
     */
    public static final List<DeferredItem<Item>> PATTERNS = List.of(
            ForgeweaveItems.PATTERN_PICKAXE_HEAD,
            ForgeweaveItems.PATTERN_SHOVEL_HEAD,
            ForgeweaveItems.PATTERN_AXE_HEAD,
            ForgeweaveItems.PATTERN_TOOL_BINDING,
            ForgeweaveItems.PATTERN_TOOL_HANDLE,
            ForgeweaveItems.PATTERN_SWORD_BLADE,
            ForgeweaveItems.PATTERN_WIDE_GUARD,
            ForgeweaveItems.PATTERN_HAND_GUARD,
            ForgeweaveItems.PATTERN_CROSS_GUARD,
            ForgeweaveItems.PATTERN_SIGN_PLATE,
            ForgeweaveItems.PATTERN_PAN,
            ForgeweaveItems.PATTERN_KNIFE_BLADE,
            ForgeweaveItems.PATTERN_LARGE_SWORD_BLADE,
            ForgeweaveItems.PATTERN_TOUGH_TOOL_ROD,
            ForgeweaveItems.PATTERN_TOUGH_BINDING,
            ForgeweaveItems.PATTERN_LARGE_PLATE,
            ForgeweaveItems.PATTERN_HAMMER_HEAD,
            ForgeweaveItems.PATTERN_EXCAVATOR_HEAD,
            ForgeweaveItems.PATTERN_SCYTHE_HEAD,
            ForgeweaveItems.PATTERN_KAMA_HEAD,
            ForgeweaveItems.PATTERN_BROAD_AXE_HEAD,
            ForgeweaveItems.PATTERN_VEIN_HAMMER_HEAD,
            ForgeweaveItems.PATTERN_WAR_MACE_HEAD,
            ForgeweaveItems.PATTERN_CURVED_BLADE,
            ForgeweaveItems.PATTERN_KATANA_BLADE);

    private final Container container;
    private final ContainerLevelAccess access;
    private final DataSlot selectedPattern = DataSlot.standalone();

    /** Client-side: constructed from the open-menu packet, with a throwaway local container. */
    public StencilTableMenu(int containerId, Inventory playerInventory, StationGroup stationGroup) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SLOTS), ContainerLevelAccess.NULL, stationGroup);
    }

    /** Server-side: constructed by {@code StencilTableBlockEntity} with the block's real inventory. */
    public StencilTableMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access) {
        this(containerId, playerInventory, container, access, groupAt(access));
    }

    private StencilTableMenu(int containerId, Inventory playerInventory, Container container,
            ContainerLevelAccess access, StationGroup stationGroup) {
        super(ForgeweaveMenus.STENCIL_TABLE.get(), containerId, stationGroup);
        checkContainerSize(container, CONTAINER_SLOTS);
        this.container = container;
        this.access = access;
        container.startOpen(playerInventory.player);

        addSlot(new Slot(container, INPUT_SLOT, 48, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ForgeweaveItems.PATTERN_BLANK.get());
            }
        });
        addSlot(new OutputSlot(container, OUTPUT_SLOT, 106, 35));

        addDataSlot(selectedPattern);
        selectedPattern.set(-1);

        layoutPlayerInventorySlots(playerInventory);
    }

    private void layoutPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** The currently selected pattern index, or {@code -1} for no selection; read by the screen to highlight the pressed button. */
    public int getSelectedPattern() {
        return selectedPattern.get();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (super.clickMenuButton(player, id)) {
            return true; // a station-group tab (issue #78), handled by StationMenu
        }
        if (id < 0 || id >= PATTERNS.size()) {
            return false;
        }
        selectedPattern.set(id);
        updateResult();
        return true;
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
        int index = selectedPattern.get();
        ItemStack input = slots.get(INPUT_SLOT).getItem();
        ItemStack result = input.isEmpty() || index < 0 || index >= PATTERNS.size()
                ? ItemStack.EMPTY
                : new ItemStack(PATTERNS.get(index).get());
        slots.get(OUTPUT_SLOT).set(result);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        ItemStack result = stackInSlot.copy();

        if (index < CONTAINER_SLOTS) { // input or output -> player inventory
            if (!moveItemStackTo(stackInSlot, CONTAINER_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stackInSlot.is(ForgeweaveItems.PATTERN_BLANK.get())) { // player inventory -> input
            if (!moveItemStackTo(stackInSlot, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
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
        return stillValid(access, player, ForgeweaveBlocks.STENCIL_TABLE.get());
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
            slots.get(INPUT_SLOT).remove(1);
            slots.get(INPUT_SLOT).setChanged();
            updateResult();
            super.onTake(player, stack);
        }
    }
}
