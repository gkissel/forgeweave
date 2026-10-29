package dev.gkissel.forgeweave.block;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import dev.gkissel.forgeweave.api.modifier.Modifier;
import dev.gkissel.forgeweave.menu.ModifierWorktableMenu;
import dev.gkissel.forgeweave.menu.StationGroup;
import dev.gkissel.forgeweave.modifier.Worktable;

/**
 * Holds the Modifier Worktable's tool and reagent slots and opens its menu (issue #1057). No ticking
 * logic, same shape as {@link StencilTableBlockEntity}: the result is resolved from the loaded slots
 * and the selected modifier every time the menu broadcasts, and taking the output is what spends
 * anything.
 *
 * <p>{@link #findSideInventory} exposes a neighbouring Part Chest in the GUI's side panel, the same
 * horizontal-neighbour scan every other station uses ({@link SideInventory}); upstream 1.20's
 * worktable menu calls {@code addChestSideInventory} for the same reason.
 */
public class ModifierWorktableBlockEntity extends BlockEntity implements StationMenuHost {
    private static final String TAG_INVENTORY = "inventory";

    private final SimpleContainer container = new SimpleContainer(Worktable.CONTAINER_SLOTS) {
        /** What {@code SimpleContainer#addListener} did before 1.21.5 removed it. */
        @Override
        public void setChanged() {
            super.setChanged();
            markChanged();
        }
    };

    private void markChanged() {
        setChanged();
    }

    public ModifierWorktableBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.MODIFIER_WORKTABLE.get(), pos, state);
    }

    public Container container() {
        return container;
    }

    /** The adjacent chest's item handler to expose in the GUI's side panel, or {@code null} if none qualifies. */
    @Nullable
    public ResourceHandler<ItemResource> findSideInventory() {
        return SideInventory.find(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        container.storeAsItemList(output.list(TAG_INVENTORY, ItemStack.CODEC));
        SavedItemFix.writeVersion(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        container.fromItemList(input.listOrEmpty(TAG_INVENTORY, SavedItemFix.items(input, ItemStack.CODEC)));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nonnull
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        ResourceHandler<ItemResource> sideInventory = findSideInventory();
        return new ModifierWorktableMenu(containerId, playerInventory, container,
                ContainerLevelAccess.create(level, worldPosition), sideInventory,
                SideInventory.maxSlots(this, sideInventory));
    }

    /** Side-inventory slot count first, then the station-group tab row (issue #78/#306/#756). */
    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        ResourceHandler<ItemResource> sideInventory = findSideInventory();
        buf.writeVarInt(SideInventory.maxSlots(this, sideInventory));
        StationGroup.STREAM_CODEC.encode(buf, StationGroup.tabsFor(level, worldPosition));
    }

    /** Drops the inventory when the block is broken (1.21.1's {@code onRemove}, moved here in 1.21.5). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, container());
        }
    }
}
