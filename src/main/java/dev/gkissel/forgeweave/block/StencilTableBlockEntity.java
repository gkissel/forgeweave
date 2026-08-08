package dev.gkissel.forgeweave.block;

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

import net.neoforged.neoforge.client.model.data.ModelData;

import dev.gkissel.forgeweave.menu.StationGroup;
import dev.gkissel.forgeweave.menu.StencilTableMenu;

/**
 * Holds the Stencil Table's persistent input+output inventory and opens its menu (docs/SCOPE.md M1
 * issue #44). Same shape as {@link PartBuilderBlockEntity} -- no ticking logic; the conversion
 * resolves instantly when the player takes the output (see {@link StencilTableMenu}).
 *
 * <p>Also retains the wood block it was crafted from ({@link WoodTexturedBlockEntity}, issue #43),
 * defaulting to oak planks (the Stencil Table's recipe is a blank pattern + planks, matching the
 * Tool Station's ingredient -- {@code ForgeweaveRecipeProvider}).
 */
public class StencilTableBlockEntity extends BlockEntity implements StationMenuHost, WoodTexturedBlockEntity {
    private static final String TAG_INVENTORY = "inventory";

    private final SimpleContainer container = new SimpleContainer(StencilTableMenu.CONTAINER_SLOTS);
    @Nonnull
    private Block texture = Blocks.OAK_PLANKS;

    public StencilTableBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.STENCIL_TABLE.get(), pos, state);
        container.addListener(c -> setChanged());
    }

    public Container container() {
        return container;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_INVENTORY, container.createTag(registries));
        WoodTexturedBlockEntity.writeTexture(tag, texture);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        container.fromTag(tag.getList(TAG_INVENTORY, Tag.TAG_COMPOUND), registries);
        texture = WoodTexturedBlockEntity.readTexture(tag, Blocks.OAK_PLANKS);
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
        return new StencilTableMenu(containerId, playerInventory, container, ContainerLevelAccess.create(level, worldPosition));
    }

    /** This station has no side inventory, so the tab row is the whole payload (issue #78). */
    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        StationGroup.STREAM_CODEC.encode(buf, StationGroup.tabsFor(level, worldPosition));
    }
}
