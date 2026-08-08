package dev.gkissel.forgeweave.block;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.StationGroup;

/**
 * Holds the Part Builder's persistent 3-slot inventory (pattern, material, output) and opens its
 * menu. Contents are saved/loaded with the block and dropped on removal by {@link
 * PartBuilderBlock#onRemove}; the block entity itself has no ticking logic (docs/SCOPE.md testing
 * strategy: "idle stations... cost ~zero tick time" -- part crafting resolves instantly when the
 * player takes the output, there's nothing to tick).
 *
 * <p>{@link #findSideInventory} exposes a neighboring item-handler block's inventory in the GUI's
 * side panel (issue #40's follow-up, matching {@link CraftingStationBlockEntity}); see {@link
 * SideInventory} for the shared neighbor scan.
 *
 * <p>Also retains the wood block it was crafted from ({@link WoodTexturedBlockEntity}, issue #43),
 * defaulting to oak (upstream's Part Builder is crafted from {@code #minecraft:logs}).
 */
public class PartBuilderBlockEntity extends BlockEntity implements StationMenuHost, WoodTexturedBlockEntity {
    private static final String TAG_INVENTORY = "inventory";

    private final SimpleContainer container = new SimpleContainer(PartBuilderMenu.CONTAINER_SLOTS);
    @Nonnull
    private Block texture = Blocks.OAK_LOG;

    public PartBuilderBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.PART_BUILDER.get(), pos, state);
        container.addListener(c -> setChanged());
    }

    public Container container() {
        return container;
    }

    /** The adjacent block's item handler to expose in the GUI's side panel, or {@code null} if none qualifies. */
    @Nullable
    public IItemHandler findSideInventory() {
        return SideInventory.find(this);
    }

    /**
     * Upstream {@code ContainerPartBuilder#partCrafter} (issue #78): the side panel turns into a
     * pattern-selection button sidebar when the neighbour feeding it is a Pattern Chest
     * <em>and</em> the station group also has a Stencil Table and a Crafting Station. All three are
     * upstream's conditions verbatim -- the chest from its {@code detectTE(TilePatternChest.class)}
     * adjacent-only scan, the other two from the group it walks in the same constructor.
     */
    public boolean isPartCrafter() {
        BlockPos sidePos = SideInventory.findPos(this);
        if (level == null || sidePos == null || !level.getBlockState(sidePos).is(ForgeweaveBlocks.PATTERN_CHEST.get())) {
            return false;
        }
        List<BlockPos> group = StationGroup.resolve(level, worldPosition);
        return StationGroup.contains(level, group, ForgeweaveBlocks.CRAFTING_STATION.get())
                && StationGroup.contains(level, group, ForgeweaveBlocks.STENCIL_TABLE.get());
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
        texture = WoodTexturedBlockEntity.readTexture(tag, Blocks.OAK_LOG);
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
        return new PartBuilderMenu(containerId, playerInventory, container,
                ContainerLevelAccess.create(level, worldPosition), findSideInventory());
    }

    /** Side-inventory slot count, then the station-group tab row, then the pattern-sidebar flag (issue #78). */
    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        IItemHandler sideInventory = findSideInventory();
        buf.writeVarInt(sideInventory == null ? 0 : sideInventory.getSlots());
        StationGroup.STREAM_CODEC.encode(buf, StationGroup.tabsFor(level, worldPosition));
        buf.writeBoolean(isPartCrafter());
    }
}
