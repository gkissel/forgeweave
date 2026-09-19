package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.modifier.Worktable;
import dev.gkissel.forgeweave.modifier.WorktableRecipe;

/**
 * The Modifier Worktable's menu (issue #1057): a tool slot, upstream 1.20's two reagent slots, a
 * result slot, and a selection of the modifiers the loaded reagent can act on. Slot coordinates are
 * upstream's {@code ModifierWorktableContainerMenu}'s own -- tool at (8, 21), reagents at (8, 45) and
 * (8, 67), result at (125, 42) -- so the derived panel art lines up.
 *
 * <p>Unlike upstream, nothing about the button list or the result is synced by hand. Every input the
 * answer depends on is already on the client: the three slots come down as ordinary slot contents and
 * {@link WorktableRecipe}'s registry is a synced datapack registry, so {@link Worktable#resolve} runs
 * the same on both sides and only the selection index needs a {@link DataSlot} -- the same vanilla
 * stonecutter mechanism {@link StencilTableMenu} uses.
 *
 * <p>Taking the result is what commits: the tool slot takes the modified tool, the reagent slot the
 * recipe named is spent (never for sorting, which is what upstream's "the compass is not consumed"
 * means), and whatever the reagent turns back into goes to the player.
 */
public class ModifierWorktableMenu extends StationMenu {
    public static final int CONTAINER_SLOTS = Worktable.CONTAINER_SLOTS;
    public static final int TOOL_SLOT = Worktable.TOOL_SLOT;
    public static final int INPUT_START = Worktable.INPUT_START;
    public static final int RESULT_SLOT = CONTAINER_SLOTS;

    /** Upstream's panel, 176x184 rather than the 166 every 1.12-derived station uses. */
    private static final int PANEL_WIDTH = 176;
    /** Upstream {@code ModifierWorktableContainerMenu#getInventoryYOffset}. */
    private static final int INVENTORY_Y = 102;
    private static final int HOTBAR_Y = INVENTORY_Y + 58;

    private static final int TOOL_X = 8;
    private static final int TOOL_Y = 21;
    private static final int INPUT_X = 8;
    private static final int INPUT_Y = 45;
    private static final int INPUT_PITCH = 22;
    private static final int RESULT_X = 125;
    private static final int RESULT_Y = 42;
    /**
     * Upstream {@code ModifierWorktableContainerMenu} adds the player's armor and offhand slots
     * "for convenience": the table works on armor, and reaching a worn piece otherwise means closing
     * the screen. The panel art has their wells baked in, so leaving them out leaves five empty
     * sockets on the screen. Coordinates are upstream's own.
     */
    private static final int ARMOR_X = 152;
    private static final int ARMOR_Y = 16;
    private static final int ARMOR_PITCH = 18;
    private static final int OFFHAND_X = 132;
    private static final int OFFHAND_Y = 70;
    /** The four armor slots plus the offhand, all of them last so no index range below shifts. */
    private static final int GEAR_SLOTS = 5;

    /** The side panel sits on the station's right; the button grid already owns the panel's left. */
    public static final int SIDE_PANEL_X = SideInventorySlots.rightSlotX(PANEL_WIDTH);
    public static final int SIDE_PANEL_Y = SideInventorySlots.SLOT_Y;

    /**
     * The station's own tool and reagent slots. Named for the station rather than {@code container}
     * because {@link Slot} has a {@code container} field of its own, and {@link ResultSlot} below is an
     * inner class: inside it the bare name would silently resolve to the slot's one-item result
     * container instead of this one.
     */
    private final Container stationSlots;
    private final ContainerLevelAccess access;
    private final HolderLookup.Provider registries;
    @Nullable
    private final IItemHandler sideInventory;
    /** The result, held outside the container so nothing about it is persisted on the block. */
    private final SimpleContainer result = new SimpleContainer(1);
    private final DataSlot selected = DataSlot.standalone();
    private final DataSlot sideInventoryLiveSlots = DataSlot.standalone();
    public final int sideInventorySlotCount;
    public final List<SideInventorySlots.SideSlot> sideSlots;

    /** Client-side: built from the open-menu packet ({@code ModifierWorktableBlockEntity#writeMenuData}). */
    public ModifierWorktableMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SLOTS), ContainerLevelAccess.NULL, null,
                buf.readVarInt(), StationGroup.STREAM_CODEC.decode(buf));
    }

    /** Server-side, or a GameTest driving the menu directly against a bare container. */
    public ModifierWorktableMenu(int containerId, Inventory playerInventory, Container container,
            ContainerLevelAccess access, @Nullable IItemHandler sideInventory) {
        this(containerId, playerInventory, container, access, sideInventory,
                sideInventory == null ? 0 : sideInventory.getSlots(), groupAt(access));
    }

    /** Server-side, with {@code SideInventory#maxSlots}' ceiling for a chest that can still grow (#756). */
    public ModifierWorktableMenu(int containerId, Inventory playerInventory, Container container,
            ContainerLevelAccess access, @Nullable IItemHandler sideInventory, int maxSideInventorySlots) {
        this(containerId, playerInventory, container, access, sideInventory, maxSideInventorySlots, groupAt(access));
    }

    private ModifierWorktableMenu(int containerId, Inventory playerInventory, Container container,
            ContainerLevelAccess access, @Nullable IItemHandler sideInventory, int sideInventorySlotCount,
            StationGroup stationGroup) {
        super(ForgeweaveMenus.MODIFIER_WORKTABLE.get(), containerId, stationGroup);
        checkContainerSize(container, CONTAINER_SLOTS);
        this.stationSlots = container;
        this.access = access;
        this.registries = playerInventory.player.level().registryAccess();
        this.sideInventory = sideInventory;
        this.sideInventorySlotCount = sideInventorySlotCount;
        container.startOpen(playerInventory.player);

        addSlot(new Slot(container, TOOL_SLOT, TOOL_X, TOOL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Worktable.isModifiable(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int i = 0; i < Worktable.INPUT_COUNT; i++) {
            addSlot(new Slot(container, INPUT_START + i, INPUT_X, INPUT_Y + i * INPUT_PITCH) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Worktable.isReagent(registries, stack);
                }
            });
        }
        addSlot(new ResultSlot(RESULT_X, RESULT_Y));

        this.sideSlots = SideInventorySlots.create(sideInventory, sideInventorySlotCount, SIDE_PANEL_X, SIDE_PANEL_Y);
        this.sideSlots.forEach(this::addSlot);

        addDataSlot(selected);
        selected.set(-1);
        addDataSlot(sideInventoryLiveSlots);
        sideInventoryLiveSlots.set(sideInventory == null ? 0 : sideInventory.getSlots());

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
        }
        // Added after the player inventory on purpose: every slot range in this class counts from
        // the station's own slots forward, so hanging these off the end leaves all of them alone and
        // keeps a shift-click from ever landing a stack in the player's armor.
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            EquipmentSlot equipment = type.getSlot();
            addSlot(new Slot(playerInventory, equipment.getIndex(Inventory.INVENTORY_SIZE),
                    ARMOR_X, ARMOR_Y + type.ordinal() * ARMOR_PITCH) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return playerInventory.player.getEquipmentSlotForItem(stack) == equipment;
                }
            });
        }
        addSlot(new Slot(playerInventory, Inventory.SLOT_OFFHAND, OFFHAND_X, OFFHAND_Y));
    }

    public int sideInventoryLiveSlots() {
        return sideInventoryLiveSlots.get();
    }

    public ItemStack tool() {
        return stationSlots.getItem(TOOL_SLOT);
    }

    /** The two reagent slots in slot order; the first being empty is what reverses a sort. */
    public List<ItemStack> inputs() {
        List<ItemStack> inputs = new ArrayList<>(Worktable.INPUT_COUNT);
        for (int i = 0; i < Worktable.INPUT_COUNT; i++) {
            inputs.add(stationSlots.getItem(INPUT_START + i));
        }
        return List.copyOf(inputs);
    }

    /** The recipe the loaded reagents drive, or empty when they drive none. */
    public Optional<WorktableRecipe> recipe() {
        return Worktable.recipeFor(registries, inputs());
    }

    /** The modifiers the buttons offer, which is also what {@link #getSelected} indexes into. */
    public List<ModifierEntry> options() {
        return recipe().map(recipe -> Worktable.options(tool(), recipe.kind())).orElse(List.of());
    }

    public int getSelected() {
        return selected.get();
    }

    /** Whatever the loaded slots and the selection produce, recomputed rather than cached. */
    public Worktable.Result outcome() {
        return Worktable.resolve(registries, tool(), inputs(), selected.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (super.clickMenuButton(player, id)) {
            return true; // a station-group tab (issue #78)
        }
        if (player.isSpectator() || id < 0 || id >= options().size()) {
            return false;
        }
        selected.set(id);
        updateResult();
        return true;
    }

    @Override
    public void broadcastChanges() {
        updateResult();
        if (sideInventory != null) {
            sideInventoryLiveSlots.set(sideInventory.getSlots());
        }
        super.broadcastChanges();
    }

    @Override
    public void slotsChanged(Container inventory) {
        // A slot change can shorten the button list out from under the selection (pulling the tool,
        // swapping the reagent), and upstream clears its selection on exactly the same event.
        if (selected.get() >= options().size()) {
            selected.set(-1);
        }
        updateResult();
        super.slotsChanged(inventory);
    }

    private void updateResult() {
        if (access == ContainerLevelAccess.NULL) {
            return; // client: the server pushes the result slot down instead of computing locally.
        }
        Worktable.Result outcome = outcome();
        result.setItem(0, outcome.output());
    }

    private int sideInventoryEnd() {
        return CONTAINER_SLOTS + 1 + sideInventorySlotCount;
    }

    /** One past the player's own 36 inventory slots -- the gear slots after it are never a target. */
    public int playerInventoryEnd() {
        return slots.size() - GEAR_SLOTS;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        ItemStack copy = stackInSlot.copy();
        int sideEnd = sideInventoryEnd();
        int playerInvEnd = sideEnd + 36;

        if (index <= RESULT_SLOT) { // station slots -> player inventory
            if (!moveItemStackTo(stackInSlot, sideEnd, playerInvEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < sideEnd) { // side inventory -> player inventory
            if (!moveItemStackTo(stackInSlot, sideEnd, playerInvEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (Worktable.isModifiable(stackInSlot)) { // player inventory -> tool slot
            if (!moveItemStackTo(stackInSlot, TOOL_SLOT, TOOL_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (Worktable.isReagent(registries, stackInSlot)) { // player inventory -> reagents
            if (!moveItemStackTo(stackInSlot, INPUT_START, INPUT_START + Worktable.INPUT_COUNT, false)) {
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
        if (stackInSlot.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stackInSlot);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ForgeweaveBlocks.MODIFIER_WORKTABLE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        stationSlots.stopOpen(player);
    }

    /**
     * The worked-on tool. Taking it spends the tool out of the tool slot, spends whatever the recipe
     * named out of the reagent slots, and hands the player what the reagent turned back into -- the
     * wet sponge's dry one.
     */
    private final class ResultSlot extends Slot {
        ResultSlot(int x, int y) {
            super(result, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            Worktable.Result outcome = outcome();
            if (!outcome.output().isEmpty()) {
                stationSlots.setItem(TOOL_SLOT, ItemStack.EMPTY);
                List<Integer> used = outcome.used();
                for (int i = 0; i < used.size() && i < Worktable.INPUT_COUNT; i++) {
                    if (used.get(i) > 0) {
                        stationSlots.removeItem(INPUT_START + i, used.get(i));
                    }
                }
                for (ItemStack leftover : outcome.leftovers()) {
                    if (!player.getInventory().add(leftover.copy())) {
                        player.drop(leftover.copy(), false);
                    }
                }
            }
            selected.set(-1);
            stationSlots.setChanged();
            updateResult();
            super.onTake(player, stack);
        }
    }
}
