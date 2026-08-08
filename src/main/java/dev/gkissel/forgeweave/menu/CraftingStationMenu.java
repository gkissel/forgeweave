package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * The Crafting Station's menu (docs/SCOPE.md M1 issue #40): a real 3x3 {@code CraftingInput} against
 * the actual {@code RecipeManager} -- so it accepts any registered {@code CraftingRecipe}, matching a
 * vanilla crafting table exactly (server-authoritative: {@link #broadcastChanges} resolves and {@link
 * OutputSlot#onTake} consumes/returns remainder only via {@link #access}'s level, same as vanilla's
 * {@code CraftingMenu}) -- while the grid itself is the block entity's persistent {@link Container},
 * not vanilla's transient one, so contents survive closing and reopening the GUI ({@code
 * CraftingStationBlockEntity} javadoc).
 *
 * <p>When the station has a qualifying neighbor ({@code CraftingStationBlockEntity#findSideInventory},
 * issue #40's chest side panel), its {@link IItemHandler} is exposed as extra {@link SlotItemHandler}
 * slots after the grid+output via {@link SideInventorySlots#create}, shared with {@link
 * PartBuilderMenu}/{@link ToolStationMenu}'s own side panels (issue #40's follow-up) -- upstream
 * 1.12's {@code ContainerCraftingStation} does the same via a {@code ContainerSideInventory}
 * sub-container; ours is flattened directly into this menu instead since NeoForge menus have no
 * equivalent sub-container concept (design decision, no NOTICE.md row). The slot *count* is decided
 * once, server-side, when the block opens the menu, and is carried to the client-side constructor
 * over the open-menu packet ({@code CraftingStationBlock#useWithoutItem}) so both sides build the
 * same number of {@code Slot}s -- the client's copy is backed by a throwaway placeholder container
 * (same "server pushes contents down" pattern {@link PartBuilderMenu}/{@link ToolStationMenu} use
 * for their own client-side placeholder containers) since the real {@link IItemHandler} only exists
 * server-side.
 */
public class CraftingStationMenu extends AbstractContainerMenu {
    public static final int GRID_WIDTH = 3;
    public static final int GRID_HEIGHT = 3;
    public static final int GRID_SLOTS = GRID_WIDTH * GRID_HEIGHT;
    public static final int CONTAINER_SLOTS = GRID_SLOTS + 1;
    private static final int OUTPUT_SLOT = GRID_SLOTS;

    /**
     * Side-panel layout (issue #40). Upstream's {@code GuiCraftingStation} builds its {@code
     * GuiSideInventory} with the two-arg constructor, i.e. {@code rightSide = false}, so the panel
     * hangs off the <em>left</em> edge of the crafting panel with its first slot row at the frame
     * ({@code yOffset = 0}). Issue #79: this used to sit on the right at {@code 176 + 4}, which put
     * the panel's frame 3px <em>inside</em> the 176-wide crafting background it was supposed to
     * abut. Geometry constants are {@link SideInventorySlots}'s.
     */
    public static final int SIDE_PANEL_X = SideInventorySlots.LEFT_SLOT_X;
    public static final int SIDE_PANEL_Y = SideInventorySlots.SLOT_Y;

    private final Container container;
    private final ContainerLevelAccess access;
    public final int sideInventorySlotCount;
    /** The side panel's own slots, kept so the client-side panel can lay them out and scroll them (issue #68). */
    public final List<SideInventorySlots.SideSlot> sideSlots;

    /** Client-side: constructed from the open-menu packet, which carries the side-inventory slot count. */
    public CraftingStationMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SLOTS), ContainerLevelAccess.NULL, null, buf.readVarInt());
    }

    /** Server-side: constructed by {@code CraftingStationBlockEntity} with the block's real grid and detected neighbor. */
    public CraftingStationMenu(int containerId, Inventory playerInventory, Container container,
            ContainerLevelAccess access, @Nullable IItemHandler sideInventory) {
        this(containerId, playerInventory, container, access, sideInventory, sideInventory == null ? 0 : sideInventory.getSlots());
    }

    private CraftingStationMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory, int sideInventorySlotCount) {
        super(ForgeweaveMenus.CRAFTING_STATION.get(), containerId);
        checkContainerSize(container, CONTAINER_SLOTS);
        this.container = container;
        this.access = access;
        this.sideInventorySlotCount = sideInventorySlotCount;
        container.startOpen(playerInventory.player);

        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                addSlot(new Slot(container, col + row * GRID_WIDTH, 30 + col * SideInventorySlots.SLOT_SIZE, 17 + row * SideInventorySlots.SLOT_SIZE));
            }
        }
        addSlot(new OutputSlot(container, OUTPUT_SLOT, 124, 35));

        this.sideSlots = SideInventorySlots.create(sideInventory, sideInventorySlotCount, SIDE_PANEL_X, SIDE_PANEL_Y);
        this.sideSlots.forEach(this::addSlot);
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
        access.execute((level, pos) -> slots.get(OUTPUT_SLOT).set(resolve(level)
                .map(recipe -> recipe.value().assemble(craftingInput(), level.registryAccess()))
                .orElse(ItemStack.EMPTY)));
    }

    private Optional<RecipeHolder<CraftingRecipe>> resolve(Level level) {
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInput(), level);
    }

    private CraftingInput craftingInput() {
        return CraftingInput.of(GRID_WIDTH, GRID_HEIGHT, gridItems());
    }

    private List<ItemStack> gridItems() {
        List<ItemStack> items = new ArrayList<>(GRID_SLOTS);
        for (int i = 0; i < GRID_SLOTS; i++) {
            items.add(slots.get(i).getItem());
        }
        return items;
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

        if (index < CONTAINER_SLOTS) { // grid or output -> player inventory
            if (!moveItemStackTo(stackInSlot, CONTAINER_SLOTS, playerInvEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < sideEnd) { // side inventory -> player inventory
            if (!moveItemStackTo(stackInSlot, sideEnd, playerInvEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stackInSlot, 0, GRID_SLOTS, false) // player inventory -> grid, else side inventory
                && !moveItemStackTo(stackInSlot, CONTAINER_SLOTS, sideEnd, false)) {
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
        return stillValid(access, player, ForgeweaveBlocks.CRAFTING_STATION.get());
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

        /**
         * Consumes one of each occupied grid slot and returns byproducts (buckets, bowls, ...),
         * matching vanilla's {@code ResultSlot}. {@code CraftingInput.of}/{@code
         * getRemainingItemsFor} both work against the *trimmed* bounding box of non-empty cells (a
         * 1x2 pair of planks in a 3x3 grid is a 1x2 input, not a 3x3 one with 7 empties), so the
         * remaining-items list has to be walked with {@link CraftingInput.Positioned}'s offset and
         * mapped back onto the real 3x3 slot indices -- not indexed 0..8 directly.
         */
        @Override
        public void onTake(Player player, ItemStack stack) {
            access.execute((level, pos) -> {
                CraftingInput.Positioned positioned = CraftingInput.ofPositioned(GRID_WIDTH, GRID_HEIGHT, gridItems());
                CraftingInput input = positioned.input();
                int left = positioned.left();
                int top = positioned.top();
                NonNullList<ItemStack> remaining = level.getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, input, level);

                for (int row = 0; row < input.height(); row++) {
                    for (int col = 0; col < input.width(); col++) {
                        Slot gridSlot = slots.get(col + left + (row + top) * GRID_WIDTH);
                        ItemStack current = gridSlot.getItem();
                        if (!current.isEmpty()) {
                            gridSlot.remove(1);
                            current = gridSlot.getItem();
                        }
                        ItemStack leftover = remaining.get(col + row * input.width());
                        if (!leftover.isEmpty()) {
                            if (current.isEmpty()) {
                                gridSlot.set(leftover);
                            } else if (ItemStack.isSameItemSameComponents(current, leftover)) {
                                current.grow(leftover.getCount());
                            } else {
                                player.drop(leftover, false);
                            }
                        }
                        gridSlot.setChanged();
                    }
                }
            });
            super.onTake(player, stack);
        }
    }
}
