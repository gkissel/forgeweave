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

import dev.gkissel.forgeweave.menu.PartBuilderMenu;

/**
 * Holds the Part Builder's persistent 3-slot inventory (pattern, material, output) and opens its
 * menu. Contents are saved/loaded with the block and dropped on removal by {@link
 * PartBuilderBlock#onRemove}; the block entity itself has no ticking logic (docs/SCOPE.md testing
 * strategy: "idle stations... cost ~zero tick time" -- part crafting resolves instantly when the
 * player takes the output, there's nothing to tick).
 */
public class PartBuilderBlockEntity extends BlockEntity implements MenuProvider {
    private static final String TAG_INVENTORY = "inventory";

    private final SimpleContainer container = new SimpleContainer(PartBuilderMenu.CONTAINER_SLOTS);

    public PartBuilderBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.PART_BUILDER.get(), pos, state);
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
        return new PartBuilderMenu(containerId, playerInventory, container, ContainerLevelAccess.create(level, worldPosition));
    }
}
