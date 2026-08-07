package dev.gkissel.forgeweave.menu;

import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * The Tool Station's menu: head + binding + handle input slots, an output slot, and the player's
 * inventory. Same "recompute output every broadcast, only consume on take" shape as
 * {@link dev.gkissel.forgeweave.menu.PartBuilderMenu}.
 *
 * <p>Assembly and repair (docs/SCOPE.md issues #10 and #11) share the same four slots: which recipe
 * is running depends only on whether the first slot holds a head part or an assembled tool, so the
 * decision -- and the per-slot cost of taking the output -- lives entirely in
 * {@link ToolAssemblyRecipes}. All this class knows is "ask, show, and charge what you're told".
 */
public class ToolStationMenu extends AbstractContainerMenu {
    public static final int CONTAINER_SLOTS = 4;
    private static final int HEAD_SLOT = 0;
    private static final int BINDING_SLOT = 1;
    private static final int HANDLE_SLOT = 2;
    private static final int OUTPUT_SLOT = 3;

    private final Container container;
    private final ContainerLevelAccess access;
    private final HolderLookup.Provider registries;

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

        addSlot(new Slot(container, HEAD_SLOT, 20, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ToolAssemblyRecipes.isHeadSlotInput(stack);
            }
        });
        // Binding and handle double as the repair-item slots, so what they accept depends on the
        // head slot: parts while assembling, the head material's repair item while repairing.
        addSlot(new Slot(container, BINDING_SLOT, 38, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ToolAssemblyRecipes.isBindingPart(stack) || isRepairItem(stack);
            }
        });
        addSlot(new Slot(container, HANDLE_SLOT, 56, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ToolAssemblyRecipes.isHandlePart(stack) || isRepairItem(stack);
            }
        });
        addSlot(new OutputSlot(container, OUTPUT_SLOT, 116, 35));

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

    @Override
    public void broadcastChanges() {
        updateResult();
        super.broadcastChanges();
    }

    private void updateResult() {
        if (access == ContainerLevelAccess.NULL) {
            return; // client: the server pushes slot contents down instead of computing locally.
        }
        slots.get(OUTPUT_SLOT).set(resolve().map(ToolAssemblyRecipes.Result::output).orElse(ItemStack.EMPTY));
    }

    private Optional<ToolAssemblyRecipes.Result> resolve() {
        return ToolAssemblyRecipes.resolve(registries,
                slots.get(HEAD_SLOT).getItem(), slots.get(BINDING_SLOT).getItem(), slots.get(HANDLE_SLOT).getItem());
    }

    private boolean isRepairItem(ItemStack stack) {
        return ToolAssemblyRecipes.isRepairItemFor(registries, container.getItem(HEAD_SLOT), stack);
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
        } else if (ToolAssemblyRecipes.isHeadSlotInput(stackInSlot)) {
            if (!moveItemStackTo(stackInSlot, HEAD_SLOT, HEAD_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (ToolAssemblyRecipes.isBindingPart(stackInSlot)) {
            if (!moveItemStackTo(stackInSlot, BINDING_SLOT, BINDING_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (ToolAssemblyRecipes.isHandlePart(stackInSlot)) {
            if (!moveItemStackTo(stackInSlot, HANDLE_SLOT, HANDLE_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (isRepairItem(stackInSlot)) {
            if (!moveItemStackTo(stackInSlot, BINDING_SLOT, HANDLE_SLOT + 1, false)) {
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
