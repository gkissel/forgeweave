package dev.gkissel.forgeweave.menu;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;

/**
 * The Tool Station's selectable tabs and the slot arrangement each one puts the input slots in --
 * upstream 1.12's {@code ToolBuildGuiInfo} plus the per-tool tables its client proxies register
 * ({@code tools/harvest/HarvestClientProxy#registerToolBuildInfo},
 * {@code tools/melee/MeleeClientProxy#registerToolBuildInfo}) and the repair layout in
 * {@code GuiButtonRepair}'s static initializer (NOTICE.md). Coordinates are upstream's verbatim,
 * expressed there as offsets from the station's build-area origin {@code (33, 42)}:
 *
 * <pre>
 *   pickaxe     head (33+20, 42-20)     binding (33, 42)          handle (33-18, 42+18)
 *   shovel      head (33+18, 42-18)     binding (33-20, 42+20)    handle (33, 42)
 *   hatchet     head (33-2,  42-20)     binding (33+18, 42-8)     handle (33-11, 42+11)
 *   broadsword  handle (33-21, 42+20)   blade (33+15, 42-16)      guard (33-3, 42+2)
 *   longsword   handle (33-21, 42+20)   blade (33+15, 42-16)      guard (33-3, 42+2)
 *   rapier      handle (33+19, 42+20)   blade (33-15, 42-16)      guard (33-1, 42+2)
 *   battlesign  handle (33-6,  42+18)   sign  (33-6,  42-8)
 *   frying pan  handle (33-21, 42+20)   pan   (33+1,  42-6)
 *   dagger      blade  (33+14, 42-14)   handle (33-14, 42+14)
 *   warmace     handle (33-18, 42+18)   head  (33+20, 42-20)      binding (33, 42)
 *   repair      tool (33, 42)           item (33-18, 42+20)       item (33-22, 42-5)
 *                                       item (33, 42-23)          item (33+22, 42-5)
 * </pre>
 *
 * <p>The five 1.12 weapons' rows are upstream's own numbers with its per-tool nudges folded in
 * (e.g. its broadsword handle is {@code 33-20-1, 42+20}). The dagger has no upstream row -- it is a
 * shape from the modern branch (docs/SCOPE.md M3) -- so its two positions are Forgeweave's,
 * mirroring the sword layout's blade-up/handle-down diagonal at the shorter reach the art has. The
 * warmace (issue #161) has no upstream row either and reuses the pickaxe's three positions --
 * head up-right, binding at the origin, handle down-left -- which is how its own art is laid out;
 * its slot order is its own part list's (handle, head, binding), not the pickaxe's.
 *
 * <p>The repair tab's last two positions are upstream's 4th and 5th ({@code GuiButtonRepair}'s
 * static initializer lists six of them); issue #154 restored them because embossing costs four items
 * -- a donor part plus a three-block reagent set -- which the two free slots M1 shipped could not
 * hold. Upstream's 6th ({@code x+18, y+20}) stays unused: nothing Forgeweave ships needs a fifth
 * free slot, and an always-empty slot is chrome a player has to learn to ignore.
 *
 * <p>Upstream's layouts are a client-only registry, because there the layout is only ever pixel
 * positions; the same table lives here in {@code menu} because Forgeweave's tabs also decide what
 * each slot accepts ({@link ToolStationMenu#accepts}), which is a server-authoritative question.
 * Which tab is selected travels as a {@code DataSlot} set from
 * {@link ToolStationMenu#clickMenuButton} -- the vanilla stonecutter/loom mechanism the repository
 * already uses for the Stencil Table -- rather than upstream's bespoke
 * {@code ToolStationSelectionPacket}.
 *
 * <p>The length of {@link Tab#slots} <em>is</em> the tab's active-slot count -- upstream's
 * {@code ContainerToolStation#activeSlots}, which the station reads to hide and refuse the slots the
 * selected tab doesn't use. Since issue #155 that count is per-tool on the build tabs too: three M3
 * weapons have no extra part, so their tabs list two positions where the M1 tools list three and the
 * repair tab lists five.
 */
public final class ToolStationTabs {

    /** A slot's position inside the station's build area, in GUI pixels from the panel's top-left. */
    public record Pos(int x, int y) {}

    /**
     * One entry in the selection sidebar.
     *
     * @param entry the tool this tab builds and the parts it takes (issue #155), or {@code null} for
     *     the repair tab, which accepts an assembled tool in its first slot instead
     * @param slots where this tab's input slots sit, in the entry's own part order (or, on the repair
     *     tab, tool then the four free slots). Its size is the tab's active-slot count.
     */
    public record Tab(@Nullable ToolAssemblyRecipes.Entry entry, List<Pos> slots) {

        public Tab {
            if (entry != null && entry.slotCount() != slots.size()) {
                throw new IllegalArgumentException(entry.constants().id() + ": tab positions and part slots disagree");
            }
        }

        public boolean isRepair() {
            return entry == null;
        }

        public ToolItem tool() {
            return entry.tool().get();
        }

        /** The part this tab's slot {@code index} accepts. */
        public PartItem part(int index) {
            return entry.part(index);
        }

        /** The caption the info panel and the button tooltip show for this tab. */
        public Component title() {
            return isRepair()
                    ? Component.translatable("gui.forgeweave.tool_station.repair")
                    : Component.translatable(tool().getDescriptionId());
        }

        /** The lang key of this tab's one-paragraph description, shown while nothing is loaded. */
        public String descriptionKey() {
            return isRepair()
                    ? "gui.forgeweave.tool_station.repair.description"
                    : tool().getDescriptionId() + ".description";
        }
    }

    private static final int ORIGIN_X = 33;
    private static final int ORIGIN_Y = 42;

    private static Pos at(int dx, int dy) {
        return new Pos(ORIGIN_X + dx, ORIGIN_Y + dy);
    }

    /** Index into {@link ToolAssemblyRecipes#ENTRIES}, so the two tables can never disagree. */
    private static Tab build(int entryIndex, Pos... slots) {
        return new Tab(ToolAssemblyRecipes.ENTRIES.get(entryIndex), List.of(slots));
    }

    public static final List<Tab> TABS = List.of(
            new Tab(null, List.of(at(0, 0), at(-18, 20), at(-22, -5), at(0, -23), at(22, -5))),
            build(0, at(20, -20), at(0, 0), at(-18, 18)),      // pickaxe: head, binding, handle
            build(1, at(18, -18), at(-20, 20), at(0, 0)),      // shovel
            build(2, at(-2, -20), at(18, -8), at(-11, 11)),    // hatchet
            build(3, at(-21, 20), at(15, -16), at(-3, 2)),     // broadsword: handle, blade, guard
            build(4, at(-21, 20), at(15, -16), at(-3, 2)),     // longsword
            build(5, at(19, 20), at(-15, -16), at(-1, 2)),     // rapier
            build(6, at(-6, 18), at(-6, -8)),                  // battlesign: handle, sign plate
            build(7, at(-21, 20), at(1, -6)),                  // frying pan: handle, pan
            build(8, at(14, -14), at(-14, 14)),                // dagger: blade, handle
            build(9, at(-18, 18), at(20, -20), at(0, 0)));     // warmace: handle, head, binding

    /** The repair tab, which is what a freshly opened station shows (as upstream's does). */
    public static final int REPAIR = 0;

    public static Tab get(int index) {
        return TABS.get(Math.floorMod(index, TABS.size()));
    }

    /** The tab index that builds {@code tool}, or -1 if none does. Used by JEI's [+] transfer. */
    public static int indexOfTool(ToolItem tool) {
        for (int i = 0; i < TABS.size(); i++) {
            Tab tab = TABS.get(i);
            if (!tab.isRepair() && tab.tool() == tool) {
                return i;
            }
        }
        return -1;
    }

    private ToolStationTabs() {}
}
