package dev.gkissel.forgeweave.block;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.Containers;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.items.IItemHandler;

import dev.gkissel.forgeweave.menu.CraftingStationMenu;
import dev.gkissel.forgeweave.menu.StationGroup;

/**
 * Holds the Crafting Station's persistent 3x3 grid (+ 1 transient output slot -- see {@code
 * CraftingStationMenu}) and opens its menu. Same "persistent container, not vanilla's transient
 * crafting container" shape as {@link PartBuilderBlockEntity}/{@link ToolStationBlockEntity} --
 * matches upstream 1.12's {@code TileCraftingStation}, which likewise wraps a saved {@code
 * InventoryCraftingPersistent} instead of the vanilla workbench's throwaway grid.
 *
 * <p>{@link #findSideInventory} delegates to {@link SideInventory#findExternal}, the horizontal-
 * neighbor scan shared with {@link ToolStationBlockEntity} (issue #40's side panel extended to all
 * stations, then the exclusion/blacklist fix in parity audit T74/issue #505) -- see {@link
 * SideInventory}'s class javadoc for the upstream port and why {@link PartBuilderBlockEntity}'s own
 * side panel uses the other, non-excluding scan instead.
 *
 * <p>Also retains the wood block it was crafted from ({@link WoodTexturedBlockEntity}, issue #43),
 * defaulting to oak (see {@link CraftingStationBlock}'s javadoc for why this is currently a fixed
 * outcome rather than a per-craft variable one).
 */
public class CraftingStationBlockEntity extends BlockEntity implements StationMenuHost, WoodTexturedBlockEntity {
    private static final String TAG_INVENTORY = "inventory";

    private final SimpleContainer container = new SimpleContainer(CraftingStationMenu.CONTAINER_SLOTS) {
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
    @Nonnull
    private Block texture = Blocks.OAK_PLANKS;

    public CraftingStationBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.CRAFTING_STATION.get(), pos, state);
    }

    public Container container() {
        return container;
    }

    /** The adjacent block's item handler to expose in the GUI's side panel, or {@code null} if none qualifies. */
    @Nullable
    public IItemHandler findSideInventory() {
        return SideInventory.findExternal(this);
    }

    @Override
    public Block getTexture() {
        return texture;
    }

    @Override
    public void setTexture(Block texture) {
        if (this.texture == texture) {
            return;
        }
        this.texture = texture;
        WoodTexturedBlockEntity.notifyTextureChanged(this);
    }

    @Nonnull
    @Override
    public ModelData getModelData() {
        return WoodTexturedBlockEntity.modelData(texture);
    }

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        WoodTexturedBlockEntity.collectTextureComponent(this, builder);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        container.storeAsItemList(output.list(TAG_INVENTORY, ItemStack.CODEC));
        SavedItemFix.writeVersion(output);
        WoodTexturedBlockEntity.writeTexture(output, texture);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        container.fromItemList(input.listOrEmpty(TAG_INVENTORY, SavedItemFix.items(input, ItemStack.CODEC)));
        texture = WoodTexturedBlockEntity.readTexture(input, Blocks.OAK_PLANKS);
    }

    /**
     * Full-state update tag so the texture (and inventory) reach already-tracking clients on a
     * dedicated server -- without this override the default {@code getUpdateTag} returns an empty
     * tag and placed stations keep rendering their default wood until the client reloads the chunk
     * (issue #79).
     */
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
        return new CraftingStationMenu(containerId, playerInventory, container,
                ContainerLevelAccess.create(level, worldPosition), findSideInventory());
    }

    /** Side-inventory slot count first, then the station-group tab row (issue #78). */
    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        IItemHandler sideInventory = findSideInventory();
        buf.writeVarInt(sideInventory == null ? 0 : sideInventory.getSlots());
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
