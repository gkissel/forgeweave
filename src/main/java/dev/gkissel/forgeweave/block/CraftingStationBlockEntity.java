package dev.gkissel.forgeweave.block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.items.IItemHandler;

import dev.gkissel.forgeweave.menu.CraftingStationMenu;

/**
 * Holds the Crafting Station's persistent 3x3 grid (+ 1 transient output slot -- see {@code
 * CraftingStationMenu}) and opens its menu. Same "persistent container, not vanilla's transient
 * crafting container" shape as {@link PartBuilderBlockEntity}/{@link ToolStationBlockEntity} --
 * matches upstream 1.12's {@code TileCraftingStation}, which likewise wraps a saved {@code
 * InventoryCraftingPersistent} instead of the vanilla workbench's throwaway grid.
 *
 * <p>{@link #findSideInventory} ports upstream {@code ContainerCraftingStation}'s neighbor scan
 * (issue #40): the first of the four horizontal neighbors exposing an item-handler capability wins
 * (NOTICE.md) -- there is no "is this neighbor also part of the station" exclusion to port, since
 * unlike upstream's multi-block chest/table cluster, Forgeweave's Crafting Station is always a
 * single block.
 *
 * <p>Also retains the wood block it was crafted from ({@link WoodTexturedBlockEntity}, issue #43),
 * defaulting to oak (see {@link CraftingStationBlock}'s javadoc for why this is currently a fixed
 * outcome rather than a per-craft variable one).
 */
public class CraftingStationBlockEntity extends BlockEntity implements MenuProvider, WoodTexturedBlockEntity {
    private static final String TAG_INVENTORY = "inventory";

    private final SimpleContainer container = new SimpleContainer(CraftingStationMenu.CONTAINER_SLOTS);
    @Nonnull
    private Block texture = Blocks.OAK_PLANKS;

    public CraftingStationBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.CRAFTING_STATION.get(), pos, state);
        container.addListener(c -> setChanged());
    }

    public Container container() {
        return container;
    }

    /** The adjacent block's item handler to expose in the GUI's side panel, or {@code null} if none qualifies. */
    @Nullable
    public IItemHandler findSideInventory() {
        if (level == null) {
            return null;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = worldPosition.relative(direction);
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, direction.getOpposite());
            if (handler != null) {
                return handler;
            }
        }
        return null;
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
}
