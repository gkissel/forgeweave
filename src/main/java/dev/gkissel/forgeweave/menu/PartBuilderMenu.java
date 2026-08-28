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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;

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
public class PartBuilderMenu extends StationMenu {
    public static final int CONTAINER_SLOTS = 5;
    public static final int PATTERN_SLOT = 0;
    public static final int MATERIAL_SLOT = 1;
    /** Upstream's second material input, {@code ContainerPartBuilder#input2} at (48, 44) -- issue #306. */
    public static final int MATERIAL_SLOT_2 = 2;
    public static final int OUTPUT_SLOT = 3;
    public static final int CHANGE_SLOT = 4;

    /**
     * Side-panel layout (issue #40's follow-up). Upstream's {@code GuiPartBuilder} builds its
     * {@code GuiSideInventory} with the two-arg constructor, i.e. {@code rightSide = false}, so the
     * pattern chest's slots hang off the <em>left</em> edge with their first row at the frame
     * ({@code yOffset = 0}) -- the right-hand side is where its single info panel goes. Issue #79:
     * this used to sit right of the info panel and 5px inside it. Geometry is
     * {@link SideInventorySlots}'s.
     */
    public static final int SIDE_PANEL_X = SideInventorySlots.LEFT_SLOT_X;
    public static final int SIDE_PANEL_Y = SideInventorySlots.SLOT_Y;

    private final Container container;
    private final ContainerLevelAccess access;
    private final HolderLookup.Provider registries;
    @Nullable
    private final IItemHandler sideInventory;
    /**
     * How many of {@link #sideSlots} are actually backed by the neighbor right now (issue #756):
     * {@link #sideSlots} is pre-built up to {@link dev.gkissel.forgeweave.block.SideInventory#maxSlots}'s
     * ceiling so a growing Pattern Chest never runs out of usable slots mid-session (see that
     * method's javadoc), but the panel must still only draw/scroll the neighbor's *current* size, or
     * a nearly-empty chest would show a mostly-empty 256-slot grid. Refreshed every tick in {@link
     * #broadcastChanges}, exactly how {@code ChestMenu#capacity} tracks the chest's own growth.
     */
    private final DataSlot sideInventoryLiveSlots = DataSlot.standalone();
    public final int sideInventorySlotCount;
    /** The side panel's own slots, kept so the client-side panel can lay them out and scroll them (issue #68). */
    public final List<SideInventorySlots.SideSlot> sideSlots;
    /**
     * Upstream {@code ContainerPartBuilder#partCrafter} (issue #78): the side panel is replaced by a
     * pattern-selection button sidebar. Resolved server-side by {@code
     * PartBuilderBlockEntity#isPartCrafter} and synced, because it depends on which block the side
     * inventory came from -- something the slot contents alone don't say.
     */
    public final boolean partCrafter;
    private int pendingMaterial1ItemsConsumed;
    private int pendingMaterial2ItemsConsumed;
    private ItemStack pendingChange = ItemStack.EMPTY;

    /** Client-side: constructed from the open-menu packet ({@code PartBuilderBlockEntity#writeMenuData}). */
    public PartBuilderMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SLOTS), ContainerLevelAccess.NULL, null,
                buf.readVarInt(), StationGroup.STREAM_CODEC.decode(buf), buf.readBoolean());
    }

    /** Server-side: constructed by {@code PartBuilderBlockEntity} with the block's real inventory and detected neighbor. */
    public PartBuilderMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory) {
        this(containerId, playerInventory, container, access, sideInventory,
                sideInventory == null ? 0 : sideInventory.getSlots(), groupAt(access),
                access.evaluate((level, pos) -> level.getBlockEntity(pos) instanceof PartBuilderBlockEntity partBuilder
                        && partBuilder.isPartCrafter()).orElse(false));
    }

    /**
     * Server-side, issue #756: like the five-arg constructor, but with an explicit
     * {@code maxSideInventorySlots} ceiling (see {@link dev.gkissel.forgeweave.block.SideInventory#maxSlots})
     * instead of the neighbor's current, possibly-about-to-grow size. {@code
     * PartBuilderBlockEntity#createMenu} uses this one; the five-arg constructor stays as-is for
     * every existing GameTest that doesn't care about a growing neighbor.
     */
    public PartBuilderMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory, int maxSideInventorySlots) {
        this(containerId, playerInventory, container, access, sideInventory, maxSideInventorySlots, groupAt(access),
                access.evaluate((level, pos) -> level.getBlockEntity(pos) instanceof PartBuilderBlockEntity partBuilder
                        && partBuilder.isPartCrafter()).orElse(false));
    }

    private PartBuilderMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory, int sideInventorySlotCount, StationGroup stationGroup,
            boolean partCrafter) {
        super(ForgeweaveMenus.PART_BUILDER.get(), containerId, stationGroup);
        this.partCrafter = partCrafter;
        checkContainerSize(container, CONTAINER_SLOTS);
        this.container = container;
        this.access = access;
        this.registries = playerInventory.player.level().registryAccess();
        this.sideInventory = sideInventory;
        this.sideInventorySlotCount = sideInventorySlotCount;
        container.startOpen(playerInventory.player);

        // Slot coordinates match upstream 1.12's ContainerPartBuilder (issue #43: derived
        // partbuilder.png background) -- pattern at its stencil-slot spot, material at the first of
        // upstream's two stacked input slots, the second material slot at upstream's own (48, 44)
        // (issue #306: cost-matching against both combined, ToolBuilder#tryBuildToolPart), main
        // output at upstream's main output spot, and the shard change at upstream's secondary
        // output spot (issue #45).
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
        addSlot(new Slot(container, MATERIAL_SLOT_2, 48, 44));
        addSlot(new OutputSlot(container, OUTPUT_SLOT, 106, 35));
        addSlot(new Slot(container, CHANGE_SLOT, 132, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // With a Pattern Chest attached the chest's slots are hidden behind the pattern buttons
        // (issue #78, upstream's GuiPartBuilder#drawSlot) -- they stay in the menu for shift-clicks.
        this.sideSlots = SideInventorySlots.create(sideInventory, sideInventorySlotCount, SIDE_PANEL_X, SIDE_PANEL_Y,
                !partCrafter);
        this.sideSlots.forEach(this::addSlot);
        addDataSlot(sideInventoryLiveSlots);
        sideInventoryLiveSlots.set(sideInventory == null ? 0 : sideInventory.getSlots());
        layoutPlayerInventorySlots(playerInventory);
    }

    /** How many of {@link #sideSlots} are currently real, synced live -- see {@link #sideInventoryLiveSlots}. */
    public int sideInventoryLiveSlots() {
        return sideInventoryLiveSlots.get();
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
        if (sideInventory != null) {
            sideInventoryLiveSlots.set(sideInventory.getSlots());
        }
        super.broadcastChanges();
    }

    private void updateResult() {
        if (access == ContainerLevelAccess.NULL) {
            return; // client: the server pushes slot contents down instead of computing locally.
        }
        Optional<PartBuilderRecipes.Match> match = PartBuilderRecipes.resolve(registries, slots.get(PATTERN_SLOT).getItem(),
                slots.get(MATERIAL_SLOT).getItem(), slots.get(MATERIAL_SLOT_2).getItem())
                .filter(candidate -> changeSlotAccepts(candidate.change()));
        pendingMaterial1ItemsConsumed = match.map(PartBuilderRecipes.Match::material1ItemsConsumed).orElse(0);
        pendingMaterial2ItemsConsumed = match.map(PartBuilderRecipes.Match::material2ItemsConsumed).orElse(0);
        pendingChange = match.map(PartBuilderRecipes.Match::change).orElse(ItemStack.EMPTY);
        // Only the main output slot reflects the live preview; the change slot is real storage
        // (see class javadoc) and is untouched here.
        slots.get(OUTPUT_SLOT).set(match.map(PartBuilderRecipes.Match::result).orElse(ItemStack.EMPTY));
    }

    // ------------------------------------------------------------------ pattern chest sidebar (#78)

    /**
     * Whether this craft's shard change can go into the change slot, i.e. whether the craft may be
     * offered at all (issue #444). Upstream {@code ContainerPartBuilder#updateResult:130-142} clears
     * the output whenever the change slot holds a stack the leftover would not stack onto -- the
     * player empties the slot first, rather than the station completing the craft and dropping the
     * change on the floor of the void.
     *
     * <p>Deviation: upstream compares item + NBT only, so a change slot already at max stack silently
     * overflows in {@code onCrafting}'s {@code secondary.grow}. The count check here blocks that
     * craft too -- same "the change must have somewhere to go" rule, one case upstream missed.
     */
    private boolean changeSlotAccepts(ItemStack change) {
        ItemStack existing = slots.get(CHANGE_SLOT).getItem();
        if (existing.isEmpty() || change.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(existing, change)
                && existing.getCount() + change.getCount() <= existing.getMaxStackSize();
    }

    /**
     * Which patterns the sidebar offers: upstream's stencil-table candidate list ({@link
     * StencilTableMenu#PATTERNS}, in that same fixed order) filtered down to the ones actually in the
     * attached Pattern Chest -- plus whichever is already loaded, so the current selection never
     * disappears from the row while it is in use. That last clause is upstream's own
     * {@code GuiButtonsPartCrafter} check against {@code inventorySlots.getSlot(2)}.
     *
     * <p>Both sides compute this from synced slot contents, so no extra sync is needed and the index
     * a click sends ({@link #clickMenuButton}) is into the fixed {@code PATTERNS} list, not into this
     * filtered view -- a chest whose contents changed mid-click can therefore never select the wrong
     * pattern, only fail to find one.
     */
    public List<Integer> patternButtons() {
        if (!partCrafter) {
            return List.of();
        }
        List<Integer> available = new ArrayList<>(StencilTableMenu.PATTERNS.size());
        ItemStack loaded = slots.get(PATTERN_SLOT).getItem();
        for (int id = 0; id < StencilTableMenu.PATTERNS.size(); id++) {
            Item item = StencilTableMenu.PATTERNS.get(id).get();
            if (loaded.is(item) || sideSlots.stream().anyMatch(slot -> slot.getItem().is(item))) {
                available.add(id);
            }
        }
        return available;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (super.clickMenuButton(player, id)) {
            return true; // a station-group tab (issue #78), handled by StationMenu
        }
        if (!partCrafter || id < 0 || id >= StencilTableMenu.PATTERNS.size()) {
            return false;
        }
        return setPattern(StencilTableMenu.PATTERNS.get(id).get());
    }

    /**
     * Upstream {@code ContainerPartBuilder#setPattern}: find {@code wanted} in the attached Pattern
     * Chest and <em>exchange</em> it with whatever the pattern slot holds, so the pattern currently
     * loaded goes back into the chest rather than being dropped or duplicated. An empty pattern slot
     * simply leaves the chest slot empty.
     */
    private boolean setPattern(Item wanted) {
        Slot patternSlot = slots.get(PATTERN_SLOT);
        for (Slot chestSlot : sideSlots) {
            if (!chestSlot.getItem().is(wanted)) {
                continue;
            }
            ItemStack fromChest = chestSlot.getItem().copy();
            ItemStack loaded = patternSlot.getItem().copy();
            chestSlot.set(loaded);
            patternSlot.set(fromChest);
            patternSlot.setChanged();
            return true;
        }
        return false;
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
        } else if (!moveItemStackTo(stackInSlot, MATERIAL_SLOT, MATERIAL_SLOT_2 + 1, false)) { // player inventory -> either material slot
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
            materialSlot.remove(pendingMaterial1ItemsConsumed);
            materialSlot.setChanged();
            Slot materialSlot2 = slots.get(MATERIAL_SLOT_2);
            materialSlot2.remove(pendingMaterial2ItemsConsumed);
            materialSlot2.setChanged();
            depositChange();
            // Upstream ContainerPartBuilder#onCrafting ends with updateResult(): refilling the output
            // synchronously is what lets vanilla's QUICK_MOVE loop keep crafting until the inputs run
            // out (issue #695). Waiting for the next broadcastChanges tick stopped it after one craft.
            updateResult();
            super.onTake(player, stack);
        }

        /**
         * Deposits this craft's shard change into the change slot, stacking onto whatever's already
         * there if it's the same material's shards (upstream {@code ContainerPartBuilder#onCrafting}).
         * {@link PartBuilderMenu#changeSlotAccepts} means there is never an incompatible stack here
         * by the time an output exists to take; the check stays as the guard that keeps that true.
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
