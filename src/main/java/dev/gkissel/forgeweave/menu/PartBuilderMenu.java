package dev.gkissel.forgeweave.menu;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * The Part Builder's menu: pattern slot + material slot + output slot + change slot, plus the
 * player's inventory. All crafting logic is resolved server-side here (docs/SCOPE.md issue #9
 * design constraints) -- {@link #broadcastChanges()} recomputes the output slot from the current
 * pattern/material every tick the menu is open (same "always up to date" pattern as vanilla's
 * furnace/crafting menus), and taking the output only consumes the material slot's cost; the
 * pattern is never consumed here (matches upstream 1.12: stencils are reusable). The blank pattern
 * is the one-way-consumed step instead, via the blank-to-part-pattern conversion recipes in {@code
 * ForgeweaveRecipeProvider}.
 *
 * <p>The change slot (issue #45) is real, persistent container storage, not a live preview: it's
 * only ever written by {@link OutputSlot#onTake} depositing the shard change for the craft that
 * just happened, matching upstream 1.12's {@code ContainerPartBuilder#onCrafting}/{@code
 * SlotOut} -- this is what stops a player from grabbing "pending" shard change without actually
 * completing the craft.
 *
 * <p>When the station has a qualifying neighbor ({@code PartBuilderBlockEntity#findSideInventory},
 * issue #40's follow-up), its {@link IItemHandler} is exposed as extra slots after the change slot
 * via {@link SideInventorySlots#create} -- same shape as {@link CraftingStationMenu}'s own side
 * panel; see that class's javadoc for the client/server slot-count handshake.
 */
public class PartBuilderMenu extends AbstractContainerMenu {
    public static final int CONTAINER_SLOTS = 4;
    public static final int PATTERN_SLOT = 0;
    public static final int MATERIAL_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;
    public static final int CHANGE_SLOT = 3;

    /**
     * Side-panel layout (issue #40's follow-up): positioned just right of the 176px-wide base panel
     * and the info panel next to it -- {@code 176 (base) + 2 (gap) + 126 (client.InfoPanel#WIDTH,
     * mirrored rather than referenced: the menu package can't depend on the client package) + 2 (gap)}.
     */
    public static final int SIDE_PANEL_X = 176 + 2 + 126 + 2;
    public static final int SIDE_PANEL_Y = 17;

    private final Container container;
    private final ContainerLevelAccess access;
    private final HolderLookup.Provider registries;
    public final int sideInventorySlotCount;
    private int pendingMaterialItemsConsumed;
    private ItemStack pendingChange = ItemStack.EMPTY;

    /** Client-side: constructed from the open-menu packet, which carries the side-inventory slot count. */
    public PartBuilderMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SLOTS), ContainerLevelAccess.NULL, null, buf.readVarInt());
    }

    /** Server-side: constructed by {@code PartBuilderBlockEntity} with the block's real inventory and detected neighbor. */
    public PartBuilderMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory) {
        this(containerId, playerInventory, container, access, sideInventory, sideInventory == null ? 0 : sideInventory.getSlots());
    }

    private PartBuilderMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory, int sideInventorySlotCount) {
        super(ForgeweaveMenus.PART_BUILDER.get(), containerId);
        checkContainerSize(container, CONTAINER_SLOTS);
        this.container = container;
        this.access = access;
        this.registries = playerInventory.player.level().registryAccess();
        this.sideInventorySlotCount = sideInventorySlotCount;
        container.startOpen(playerInventory.player);

        // Slot coordinates match upstream 1.12's ContainerPartBuilder (issue #43: derived
        // partbuilder.png background) -- pattern at its stencil-slot spot, material at the first of
        // upstream's two stacked input slots (the second, at (48, 44), stays visible on the
        // background but unused since we only have one material slot), main output at upstream's
        // main output spot, and the shard change at upstream's secondary output spot (issue #45).
        addSlot(new Slot(container, PATTERN_SLOT, 26, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PartBuilderRecipes.isPattern(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(container, MATERIAL_SLOT, 48, 26));
        addSlot(new OutputSlot(container, OUTPUT_SLOT, 106, 35));
        addSlot(new Slot(container, CHANGE_SLOT, 132, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        SideInventorySlots.create(sideInventory, sideInventorySlotCount, SIDE_PANEL_X, SIDE_PANEL_Y).forEach(this::addSlot);
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
        pendingMaterialItemsConsumed = match.map(PartBuilderRecipes.Match::materialItemsConsumed).orElse(0);
        pendingChange = match.map(PartBuilderRecipes.Match::change).orElse(ItemStack.EMPTY);
        // Only the main output slot reflects the live preview; the change slot is real storage
        // (see class javadoc) and is untouched here.
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
        int sideEnd = CONTAINER_SLOTS + sideInventorySlotCount;
        int playerInvEnd = sideEnd + 36;

        if (index < CONTAINER_SLOTS) { // pattern/material/output/change -> player inventory
            if (!moveItemStackTo(stackInSlot, CONTAINER_SLOTS, playerInvEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < sideEnd) { // side inventory -> player inventory
            if (!moveItemStackTo(stackInSlot, sideEnd, playerInvEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (PartBuilderRecipes.isPattern(stackInSlot)) { // player inventory -> pattern slot
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
            materialSlot.remove(pendingMaterialItemsConsumed);
            materialSlot.setChanged();
            depositChange();
            super.onTake(player, stack);
        }

        /**
         * Deposits this craft's shard change into the change slot, stacking onto whatever's already
         * there if it's the same material's shards (upstream {@code ContainerPartBuilder#onCrafting}:
         * a different material's leftover shards already sitting there are left alone -- the player
         * clears the slot first).
         */
        private void depositChange() {
            if (pendingChange.isEmpty()) {
                return;
            }
            Slot changeSlot = slots.get(CHANGE_SLOT);
            ItemStack existing = changeSlot.getItem();
            if (existing.isEmpty()) {
                changeSlot.set(pendingChange.copy());
            } else if (ItemStack.isSameItemSameComponents(existing, pendingChange)) {
                existing.grow(pendingChange.getCount());
                changeSlot.setChanged();
            }
        }
    }
}
