package dev.gkissel.forgeweave.block;

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

import net.neoforged.neoforge.items.IItemHandler;

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

    private final SimpleContainer container = new SimpleContainer(Worktable.CONTAINER_SLOTS);

    public ModifierWorktableBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.MODIFIER_WORKTABLE.get(), pos, state);
        container.addListener(c -> setChanged());
    }

    public Container container() {
        return container;
    }

    /** The adjacent chest's item handler to expose in the GUI's side panel, or {@code null} if none qualifies. */
    @Nullable
    public IItemHandler findSideInventory() {
        return SideInventory.find(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_INVENTORY, container.createTag(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        container.fromTag(tag.getList(TAG_INVENTORY, Tag.TAG_COMPOUND), registries);
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
        IItemHandler sideInventory = findSideInventory();
        return new ModifierWorktableMenu(containerId, playerInventory, container,
                ContainerLevelAccess.create(level, worldPosition), sideInventory,
                SideInventory.maxSlots(this, sideInventory));
    }

    /** Side-inventory slot count first, then the station-group tab row (issue #78/#306/#756). */
    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        IItemHandler sideInventory = findSideInventory();
        buf.writeVarInt(SideInventory.maxSlots(this, sideInventory));
        StationGroup.STREAM_CODEC.encode(buf, StationGroup.tabsFor(level, worldPosition));
    }
}
