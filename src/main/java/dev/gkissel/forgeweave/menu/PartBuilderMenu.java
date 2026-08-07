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
 * The Part Builder's menu: pattern slot + material slot + output slot, plus the player's inventory.
 * All crafting logic is resolved server-side here (docs/SCOPE.md issue #9 design constraints) --
 * {@link #broadcastChanges()} recomputes the output slot from the current pattern/material every
 * tick the menu is open (same "always up to date" pattern as vanilla's furnace/crafting menus), and
 * taking the output only consumes the material slot's cost; the pattern is never consumed
 * (CONTEXT.md: patterns are reusable).
 */
public class PartBuilderMenu extends AbstractContainerMenu {
    public static final int CONTAINER_SLOTS = 3;
    private static final int PATTERN_SLOT = 0;
    private static final int MATERIAL_SLOT = 1;
    private static final int OUTPUT_SLOT = 2;

    private final Container container;
    private final ContainerLevelAccess access;
    private final HolderLookup.Provider registries;
    private int pendingMaterialCost;

    /** Client-side: constructed from the open-menu packet, with a throwaway local container. */
    public PartBuilderMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SLOTS), ContainerLevelAccess.NULL);
    }

    /** Server-side: constructed by {@code PartBuilderBlockEntity} with the block's real inventory. */
    public PartBuilderMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access) {
        super(ForgeweaveMenus.PART_BUILDER.get(), containerId);
        checkContainerSize(container, CONTAINER_SLOTS);
        this.container = container;
        this.access = access;
        this.registries = playerInventory.player.level().registryAccess();
        container.startOpen(playerInventory.player);

        addSlot(new Slot(container, PATTERN_SLOT, 20, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PartBuilderRecipes.isPattern(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(container, MATERIAL_SLOT, 52, 35));
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
        Optional<PartBuilderRecipes.Match> match =
                PartBuilderRecipes.resolve(registries, slots.get(PATTERN_SLOT).getItem(), slots.get(MATERIAL_SLOT).getItem());
        pendingMaterialCost = match.map(PartBuilderRecipes.Match::materialCost).orElse(0);
        slots.get(OUTPUT_SLOT).set(match.map(PartBuilderRecipes.Match::result).orElse(ItemStack.EMPTY));
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
        } else if (PartBuilderRecipes.isPattern(stackInSlot)) {
            if (!moveItemStackTo(stackInSlot, PATTERN_SLOT, PATTERN_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stackInSlot, MATERIAL_SLOT, MATERIAL_SLOT + 1, false)) {
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
        return stillValid(access, player, ForgeweaveBlocks.PART_BUILDER.get());
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
            Slot materialSlot = slots.get(MATERIAL_SLOT);
            materialSlot.remove(pendingMaterialCost);
            materialSlot.setChanged();
            super.onTake(player, stack);
        }
    }
}
