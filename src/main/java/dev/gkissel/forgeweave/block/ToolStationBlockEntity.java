package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.menu.ToolStationMenu;

/**
 * Holds the Tool Station's persistent 4-slot inventory (head, binding, handle, output) and opens
 * its menu. Same shape as {@link PartBuilderBlockEntity} -- see that class's javadoc for why there
 * is no ticking logic.
 */
public class ToolStationBlockEntity extends BlockEntity implements MenuProvider {
    private static final String TAG_INVENTORY = "inventory";

    private final SimpleContainer container = new SimpleContainer(ToolStationMenu.CONTAINER_SLOTS);

    public ToolStationBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.TOOL_STATION.get(), pos, state);
        container.addListener(c -> setChanged());
    }

    public Container container() {
        return container;
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
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ToolStationMenu(containerId, playerInventory, container, ContainerLevelAccess.create(level, worldPosition));
    }
}
