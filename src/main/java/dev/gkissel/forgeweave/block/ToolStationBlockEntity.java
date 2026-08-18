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
import net.neoforged.neoforge.items.IItemHandler;

import dev.gkissel.forgeweave.menu.StationGroup;
import dev.gkissel.forgeweave.menu.ToolStationMenu;

/**
 * Holds the Tool Station's persistent 4-slot inventory (head, binding, handle, output) and opens
 * its menu. Same shape as {@link PartBuilderBlockEntity} -- see that class's javadoc for why there
 * is no ticking logic.
 *
 * <p>{@link #findSideInventory} exposes a neighboring item-handler block's inventory in the GUI's
 * side panel (issue #40's follow-up, matching {@link CraftingStationBlockEntity}, including that
 * class's exclusion/blacklist fix in parity audit T74/issue #505); see {@link SideInventory} for the
 * shared neighbor scan.
 *
 * <p>Also retains the wood block it was crafted from ({@link WoodTexturedBlockEntity}, issue #43),
 * defaulting to oak (upstream's Tool Station is crafted from {@code #minecraft:planks}).
 */
public class ToolStationBlockEntity extends BlockEntity implements StationMenuHost, WoodTexturedBlockEntity {
    private static final String TAG_INVENTORY = "inventory";

    private final SimpleContainer container = new SimpleContainer(ToolStationMenu.CONTAINER_SLOTS);
    @Nonnull
    private Block texture;

    public ToolStationBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.TOOL_STATION.get(), pos, state);
        this.texture = defaultTexture();
        container.addListener(c -> setChanged());
    }

    public Container container() {
        return container;
    }

    /**
     * Whether this is a Tool Forge rather than a Tool Station (issue #152). Read off the block state
     * rather than stored, because the two blocks share this class and one block entity type: there is
     * no moment where the state and a stored flag could disagree.
     */
    public boolean isForge() {
        return getBlockState().is(ForgeweaveBlocks.TOOL_FORGE.get());
    }

    /**
     * The wood (or metal) a freshly placed station wears before any crafting texture is applied.
     * Upstream's Tool Station is crafted from {@code #minecraft:planks}; upstream's Tool Forge models
     * its untextured legs and underside in {@code minecraft:blocks/iron_block}.
     */
    private Block defaultTexture() {
        return isForge() ? Blocks.IRON_BLOCK : Blocks.OAK_PLANKS;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_INVENTORY, container.createTag(registries));
        WoodTexturedBlockEntity.writeTexture(tag, texture);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        container.fromTag(tag.getList(TAG_INVENTORY, Tag.TAG_COMPOUND), registries);
        texture = WoodTexturedBlockEntity.readTexture(tag, defaultTexture());
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
        return new ToolStationMenu(containerId, playerInventory, container,
                ContainerLevelAccess.create(level, worldPosition), findSideInventory(), isForge());
    }

    /**
     * Side-inventory slot count first, then the station-group tab row (issue #78), then whether this
     * is a Tool Forge (issue #152 -- the client menu needs it for the "needs a Tool Forge" message
     * and the screen for its metal styling).
     */
    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        IItemHandler sideInventory = findSideInventory();
        buf.writeVarInt(sideInventory == null ? 0 : sideInventory.getSlots());
        StationGroup.STREAM_CODEC.encode(buf, StationGroup.tabsFor(level, worldPosition));
        buf.writeBoolean(isForge());
    }
}
