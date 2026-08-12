package dev.gkissel.forgeweave.menu;

import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.StationMenuHost;

/**
 * The connected workshop a station belongs to, and the tab row its GUI draws for it (issue #78).
 *
 * <h2>Upstream's grouping rule</h2>
 *
 * <p>Ported from 1.12's {@code ContainerTinkerStation#detectedTinkerStationParts} (NOTICE.md), which
 * this class reproduces exactly:
 *
 * <ul>
 *   <li><b>Flood fill over horizontal neighbours only.</b> Starting at the opened block, the search
 *       walks north/east/south/west; a position that is not a station block ends that branch. So the
 *       group is the 4-connected component of station blocks on one Y layer -- diagonals and
 *       vertically stacked stations are never in the same group.
 *   <li><b>One member per station kind.</b> Upstream keys the found set on {@code getGuiNumber},
 *       which is per block <em>variant</em>, so a group with three Part Builders contributes exactly
 *       one Part Builder tab. Which one wins is decided by the search order, and upstream's queue is
 *       a {@link PriorityQueue} of {@code BlockPos} -- i.e. lowest Y, then Z, then X -- so the
 *       outcome is deterministic rather than insertion-ordered. {@link #KINDS} is that key here.
 *   <li><b>Fixed tab order.</b> Upstream sorts by descending gui number: Crafting Station (50), Tool
 *       Station (25), Part Builder (20), Part Chest (16), Pattern Chest (15), Stencil Table (10).
 *       {@link #KINDS} is written in that order and the search collects into a {@link TreeMap} keyed
 *       by it, so the list comes out sorted with no separate comparator.
 *   <li><b>Tabs only appear when the group contains a Crafting Station.</b> This is upstream's
 *       {@code hasCraftingStation} return value, which {@code GuiTinkerStation}'s constructor gates
 *       the whole tab row on. It is the reason a bare pair of tables shows no tabs in 1.12 -- the
 *       Crafting Station is what turns a row of blocks into a "workshop". {@link #resolve} is the
 *       ungated membership (what a GameTest asserts on); {@link #tabsFor} applies the gate and is
 *       what actually reaches a client.
 * </ul>
 *
 * <h2>Sync and authority</h2>
 *
 * <p>The member list is resolved <em>server-side</em> and written into the open-menu packet by
 * {@link StationMenuHost#writeMenuData}, so the client renders exactly the row the server would
 * accept clicks against -- no client-side block scanning, correct on a dedicated server. Clicking a
 * tab sends nothing but an index into that list, as a plain vanilla
 * {@code ServerboundContainerButtonClickPacket} ({@link StationMenu#clickMenuButton}, ids offset by
 * {@link #TAB_BUTTON_BASE} so they cannot collide with a menu's own buttons). {@link #open} then
 * looks the target up in the server's own copy of the list and opens that block's
 * {@link net.minecraft.world.MenuProvider} -- a forged index can only ever reach a station the
 * server already put in the player's tab row.
 */
public record StationGroup(List<BlockPos> members, int selected) {

    /** No group: what a client-side menu starts with and what a station with no tab row syncs. */
    public static final StationGroup EMPTY = new StationGroup(List.of(), -1);

    /**
     * Every station block, in upstream's descending-gui-number tab order. The index into this list
     * is both the dedup key (upstream's {@code getGuiNumber}) and the sort key.
     *
     * <p>An entry is a <em>list</em> of blocks because upstream's key is a gui number, not a block,
     * and {@code BlockToolForge#getGuiNumber} returns 25 -- "same as toolstation", its own comment
     * says. So a workshop holding both a Tool Station and a Tool Forge shows one tab between them,
     * exactly as it does in 1.12, and clicking it opens whichever the search reached first (issue
     * #152).
     */
    private static final List<List<Supplier<? extends Block>>> KINDS = List.of(
            List.of(ForgeweaveBlocks.CRAFTING_STATION),
            List.of(ForgeweaveBlocks.TOOL_STATION, ForgeweaveBlocks.TOOL_FORGE),
            List.of(ForgeweaveBlocks.PART_BUILDER),
            List.of(ForgeweaveBlocks.PART_CHEST),
            List.of(ForgeweaveBlocks.PATTERN_CHEST),
            List.of(ForgeweaveBlocks.STENCIL_TABLE));

    /** Upstream's {@code hasCraftingStation} key: the Crafting Station is {@link #KINDS}' first entry. */
    private static final int CRAFTING_STATION = 0;

    /** Menu-button ids {@code TAB_BUTTON_BASE + index}; well clear of any menu's own button range. */
    public static final int TAB_BUTTON_BASE = 1000;

    public static final StreamCodec<ByteBuf, StationGroup> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(KINDS.size())), StationGroup::members,
            ByteBufCodecs.VAR_INT, StationGroup::selected,
            StationGroup::new);

    public boolean isEmpty() {
        return members.isEmpty();
    }

    /**
     * Upstream's BFS: the 4-connected component of station blocks containing {@code start}, reduced
     * to one member per station kind and ordered by {@link #KINDS}. Never empty when {@code start}
     * itself is a station block.
     */
    public static List<BlockPos> resolve(BlockGetter level, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        TreeMap<Integer, BlockPos> found = new TreeMap<>();
        Queue<BlockPos> queue = new PriorityQueue<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!visited.add(pos)) {
                continue;
            }
            int kind = kindOf(level.getBlockState(pos));
            if (kind < 0) {
                continue; // not a station: this branch of the flood fill ends here.
            }
            found.putIfAbsent(kind, pos);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = pos.relative(direction);
                if (!visited.contains(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return List.copyOf(found.values());
    }

    /**
     * The tab row to sync for the station at {@code pos}: {@link #resolve}'s membership, but only
     * when the group contains a Crafting Station (upstream's {@code hasCraftingStation} gate).
     */
    public static StationGroup tabsFor(Level level, BlockPos pos) {
        List<BlockPos> members = resolve(level, pos);
        // The list is sorted by KINDS, so a Crafting Station -- if there is one -- is member zero.
        if (members.isEmpty() || kindOf(level.getBlockState(members.get(0))) != CRAFTING_STATION) {
            return EMPTY;
        }
        return new StationGroup(members, members.indexOf(pos));
    }

    /** Whether any member of {@code members} is a {@code block} -- upstream's per-kind scan of its group list. */
    public static boolean contains(BlockGetter level, List<BlockPos> members, Block block) {
        return members.stream().anyMatch(pos -> level.getBlockState(pos).is(block));
    }

    /** Whether {@code state} is a station block, and if so which kind (its index into {@link #KINDS}). */
    public static int kindOf(BlockState state) {
        for (int i = 0; i < KINDS.size(); i++) {
            for (Supplier<? extends Block> block : KINDS.get(i)) {
                if (state.is(block.get())) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static boolean isTabButton(int buttonId) {
        return buttonId >= TAB_BUTTON_BASE && buttonId < TAB_BUTTON_BASE + KINDS.size();
    }

    /**
     * Opens the tab at {@code index} for {@code player}, server-side. The index is validated against
     * this (server-resolved) member list, so nothing the client sends can name an arbitrary block.
     *
     * <p>Upstream's {@code TinkerStationTabPacket} stashes the cursor stack across the switch and
     * puts it back on the new container; without that, vanilla's {@code AbstractContainerMenu#removed}
     * would throw whatever the player is dragging onto the floor every time they change tab.
     */
    public boolean open(Player player, int index) {
        if (index < 0 || index >= members.size()) {
            return false;
        }
        if (!(player.level().getBlockEntity(members.get(index)) instanceof StationMenuHost host)) {
            return false; // the block was broken or replaced since the row was synced.
        }
        ItemStack carried = player.containerMenu.getCarried();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        host.open(player);
        if (!carried.isEmpty()) {
            player.containerMenu.setCarried(carried);
        }
        return true;
    }
}
