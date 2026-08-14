package dev.gkissel.forgeweave.menu;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.DeferredItem;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The Stencil Table's menu (docs/SCOPE.md M1 issue #44): a blank-pattern input slot, an output
 * slot, and a fixed selection of the five part patterns. Selecting one (via {@link
 * #clickMenuButton}) determines what the output slot shows; taking the output consumes one item
 * from the input slot -- which is a blank pattern, or an already-stamped one when {@code
 * reuseStencils} is on ({@link #isValidInput}, issue #276), matching upstream 1.12's ({@code
 * ContainerStencilTable}/{@code SlotStencil}/{@code TileStencilTable}, NOTICE.md). Unlike upstream,
 * where each material variant of a part is its own registered stencil-table candidate (per-material
 * patterns), Forgeweave's five part patterns are plain, material-less items (ADR-0002-adjacent
 * design: see {@code ForgeweaveItems}), so the candidate list here is simply {@link #PATTERNS} --
 * no dynamic registry, no custom network packet. The selection index is a synced {@link DataSlot}
 * set from {@link #clickMenuButton}, exactly the vanilla stonecutter/loom mechanism ({@code
 * StonecutterMenu#clickMenuButton}) the screen's button clicks route through.
 *
 * <p>When a Pattern Chest is adjacent ({@code StencilTableBlockEntity#findSideInventory}, issue
 * #306), its {@link IItemHandler} is exposed as extra slots after the output slot via {@link
 * SideInventorySlots#create} -- same shape as {@link PartBuilderMenu}/{@link CraftingStationMenu}'s
 * own side panels, except this one sits on the station's <em>right</em> (upstream's {@code
 * ContainerStencilTable} builds its {@code DynamicChestInventory} with {@code rightSide = true},
 * unlike those two, because the Stencil Table's own pattern-selection buttons already occupy the
 * left). {@link #quickMoveStack} also shift-clicks a stamped pattern straight into the chest when
 * one is attached (upstream {@code ContainerStencilTable#transferStackInSlot}).
 */
public class StencilTableMenu extends StationMenu {
    public static final int CONTAINER_SLOTS = 2;
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    /** The Stencil Table's own 176x166 background, matching every other station's panel size. */
    private static final int PANEL_WIDTH = 176;

    /** Side-panel layout (issue #306): the panel's first slot, on the station's right edge. */
    public static final int SIDE_PANEL_X = SideInventorySlots.rightSlotX(PANEL_WIDTH);
    public static final int SIDE_PANEL_Y = SideInventorySlots.SLOT_Y;

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
    public final int sideInventorySlotCount;
    /** The side panel's own slots, kept so the client-side panel can lay them out and scroll them (issue #306). */
    public final List<SideInventorySlots.SideSlot> sideSlots;

    /** Client-side: constructed from the open-menu packet ({@code StencilTableBlockEntity#writeMenuData}). */
    public StencilTableMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SLOTS), ContainerLevelAccess.NULL, null,
                buf.readVarInt(), StationGroup.STREAM_CODEC.decode(buf));
    }

    /** Server-side: constructed by {@code StencilTableBlockEntity} with the block's real inventory and detected neighbor. */
    public StencilTableMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory) {
        this(containerId, playerInventory, container, access, sideInventory,
                sideInventory == null ? 0 : sideInventory.getSlots(), groupAt(access));
    }

    private StencilTableMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory, int sideInventorySlotCount, StationGroup stationGroup) {
        super(ForgeweaveMenus.STENCIL_TABLE.get(), containerId, stationGroup);
        checkContainerSize(container, CONTAINER_SLOTS);
        this.container = container;
        this.access = access;
        this.sideInventorySlotCount = sideInventorySlotCount;
        container.startOpen(playerInventory.player);

        addSlot(new Slot(container, INPUT_SLOT, 48, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isValidInput(stack);
            }
        });
        addSlot(new OutputSlot(container, OUTPUT_SLOT, 106, 35));

        this.sideSlots = SideInventorySlots.create(sideInventory, sideInventorySlotCount, SIDE_PANEL_X, SIDE_PANEL_Y);
        this.sideSlots.forEach(this::addSlot);

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

    /**
     * What the input slot accepts: a blank pattern always, plus an already-stamped one while
     * upstream 1.12's {@code reuseStencils} is on (issue #276, its default). Upstream gates the same
     * thing in two places -- {@code TileStencilTable#isItemValidForSlot} and {@code
     * Pattern#isValidStencil}, both reading {@code Config.reuseStencil} -- which here is this one
     * method, shared by the slot's {@code mayPlace} and {@link #quickMoveStack}'s shift-click path.
     *
     * <p>Reshaping consumes the stamped pattern exactly as a blank one would ({@link OutputSlot#onTake}
     * removes one from the input), so the trade stays 1:1 and no pattern is created or destroyed.
     */
    public static boolean isValidInput(ItemStack stack) {
        if (stack.is(ForgeweaveItems.PATTERN_BLANK.get())) {
            return true;
        }
        return ForgeweaveConfig.REUSE_STENCILS.get()
                && PATTERNS.stream().anyMatch(pattern -> stack.is(pattern.get()));
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

    private int sideInventoryEnd() {
        return CONTAINER_SLOTS + sideInventorySlotCount;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        ItemStack result = stackInSlot.copy();
        int sideEnd = sideInventoryEnd();
        int playerInvEnd = sideEnd + 36;

        if (index == OUTPUT_SLOT && sideInventorySlotCount > 0) {
            // Upstream ContainerStencilTable#transferStackInSlot: the stamped pattern always
            // shift-clicks into the adjacent Pattern Chest when one is attached, never the player
            // inventory (issue #306).
            if (!moveItemStackTo(stackInSlot, CONTAINER_SLOTS, sideEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < CONTAINER_SLOTS) { // input, or output with no chest attached -> player inventory
            if (!moveItemStackTo(stackInSlot, sideEnd, playerInvEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < sideEnd) { // side inventory -> player inventory
            if (!moveItemStackTo(stackInSlot, sideEnd, playerInvEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (isValidInput(stackInSlot)) { // player inventory -> input
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
