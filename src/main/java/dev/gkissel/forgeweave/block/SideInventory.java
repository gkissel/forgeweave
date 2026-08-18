package dev.gkissel.forgeweave.block;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.menu.StationGroup;

/**
 * The horizontal-neighbor item-handler scan every station's side-inventory GUI panel is built from
 * (docs/SCOPE.md issue #40, extended to the Part Builder and Tool Station in the same issue's
 * follow-up) -- the first of the four horizontal neighbors exposing an item-handler capability wins.
 *
 * <p>Upstream 1.12 has two different neighbor-finding rules, not one, and this class keeps them as
 * two different methods rather than folding them together:
 *
 * <ul>
 *   <li>{@link #findExternal}: {@code ContainerCraftingStation}'s scan (NOTICE.md) -- for a station
 *       whose side panel is meant to reach something <em>outside</em> its own workshop, so it skips
 *       any neighbor that is itself a member of the opened station's {@code StationGroup} (that
 *       neighbor gets its own workshop tab instead -- upstream's {@code tinkerStationBlocks}
 *       exclusion, parity audit T74/issue #505) and anything named by {@link
 *       ForgeweaveConfig#CRAFTING_STATION_BLACKLIST} (upstream's {@code craftingStationBlacklist},
 *       "mainly for compatibility"). {@link CraftingStationBlockEntity} and {@link
 *       ToolStationBlockEntity} both want this -- upstream has no Tool Station side panel at all, but
 *       Forgeweave's is the same "connect an external chest" feature as the Crafting Station's own
 *       (both classes' javadoc cross-reference each other), so it gets the same fix.
 *   <li>{@link #find}: upstream {@code ContainerPartBuilder}/{@code ContainerStencilTable}'s {@code
 *       detectTE(TilePatternChest.class)} -- the <em>opposite</em> rule, deliberately requiring the
 *       Pattern Chest to be a workshop member, so this keeps the old, non-excluding scan. {@link
 *       PartBuilderBlockEntity} and {@link StencilTableBlockEntity} use this one; excluding group
 *       members here would break their already-shipped pattern-chest sidebar (issue #78/#306), since
 *       a Pattern Chest next to either is a {@code StationGroup} member by definition.
 * </ul>
 */
public final class SideInventory {
    private SideInventory() {}

    /** The adjacent block's item handler to expose in a station GUI's side panel, or {@code null} if none qualifies. */
    @Nullable
    public static IItemHandler find(BlockEntity blockEntity) {
        Direction direction = findDirection(blockEntity, false);
        return direction == null ? null : handlerAt(blockEntity, direction);
    }

    /** {@link #find}, but excluding the opened station's own workshop group and the blacklist -- see class javadoc. */
    @Nullable
    public static IItemHandler findExternal(BlockEntity blockEntity) {
        Direction direction = findDirection(blockEntity, true);
        return direction == null ? null : handlerAt(blockEntity, direction);
    }

    /**
     * Where {@link #find}'s handler came from, or {@code null} if there is none -- so a caller can ask
     * what kind of block it actually is (the Part Builder's pattern-chest sidebar, issue #78, only
     * appears when the neighbour feeding the side panel is a Pattern Chest).
     */
    @Nullable
    public static BlockPos findPos(BlockEntity blockEntity) {
        Direction direction = findDirection(blockEntity, false);
        return direction == null ? null : blockEntity.getBlockPos().relative(direction);
    }

    @Nullable
    private static Direction findDirection(BlockEntity blockEntity, boolean external) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return null;
        }
        BlockPos pos = blockEntity.getBlockPos();
        List<BlockPos> ownGroup = external ? StationGroup.resolve(level, pos) : List.of();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            if (external && (ownGroup.contains(neighborPos) || blacklisted(level, neighborPos))) {
                continue;
            }
            if (handlerAt(blockEntity, direction) != null) {
                return direction;
            }
        }
        return null;
    }

    /** Upstream {@code ContainerCraftingStation#blacklisted}: registry name first, then classname. */
    private static boolean blacklisted(Level level, BlockPos pos) {
        List<? extends String> blacklist = ForgeweaveConfig.CRAFTING_STATION_BLACKLIST.get();
        if (blacklist.isEmpty()) {
            return false;
        }
        BlockEntity neighbor = level.getBlockEntity(pos);
        if (neighbor == null) {
            return false;
        }
        ResourceLocation registryName = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(neighbor.getType());
        if (registryName != null && blacklist.contains(registryName.toString())) {
            return true;
        }
        return blacklist.contains(neighbor.getClass().getName());
    }

    @Nullable
    private static IItemHandler handlerAt(BlockEntity blockEntity, Direction direction) {
        Level level = blockEntity.getLevel();
        BlockPos neighborPos = blockEntity.getBlockPos().relative(direction);
        return level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, direction.getOpposite());
    }
}
