package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

import dev.gkissel.forgeweave.item.ForgeweaveItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
 *   shortbow    limb   (32+4,  41-18)   limb  (32-18, 41+4)       string  (32+6, 41+6)
 *   longbow     limb   (32+12, 41-22)   limb  (32-22, 41+12)      grip    (32-15, 41-15)
 *                                                                 string  (32+6, 41+6)
 *   crossbow    body   (32+6,  41+6)    limb  (32+12, 41-22)      binding (32-18, 41-18)
 *                                                                 string  (32-14, 41+10)
 *   repair      tool (33, 42)           item (33-18, 42+20)       item (33-22, 42-5)
 *                                       item (33, 42-23)          item (33+22, 42-5)
 *                                       item (33+18, 42+20)
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
 * <p>The repair tab's last three positions are upstream's 4th, 5th and 6th ({@code
 * GuiButtonRepair}'s static initializer lists six of them). Issue #154 restored the 4th and 5th
 * because embossing costs a donor part plus a reagent set, which the two free slots M1 shipped
 * could not hold; issue #248 restored the 6th ({@code x+18, y+20}) along with the full-parity
 * four-reagent embossing cost (three slime crystals plus a gold block), which needs a fifth free
 * slot beside the donor part -- the repair layout is now upstream's whole table.
 *
 * <p>Upstream's layouts are a client-only registry, because there the layout is only ever pixel
 * positions; the same table lives here in {@code menu} because Forgeweave's tabs also decide what
 * each slot accepts ({@link ToolStationMenu#accepts}), which is a server-authoritative question.
 * Which tab is selected travels as a {@code DataSlot} set from
 * {@link ToolStationMenu#clickMenuButton} -- the vanilla stonecutter/loom mechanism the repository
 * already uses for the Stencil Table -- rather than upstream's bespoke
 * {@code ToolStationSelectionPacket}. Only the transport differs: since parity audit T11 (issue
 * #443) the selection is station state as upstream's is, so clicking a tab moves it for every player
 * standing at that station and a menu opened later starts on it.
 *
 * <p>The length of {@link Tab#slots} <em>is</em> the tab's active-slot count -- upstream's
 * {@code ContainerToolStation#activeSlots}, which the station reads to hide and refuse the slots the
 * selected tab doesn't use. Since issue #155 that count is per-tool on the build tabs too: three M3
 * weapons have no extra part, so their tabs list two positions where the M1 tools list three and the
 * repair tab lists six.
 */
public final class ToolStationTabs {

    /** A slot's position inside the station's build area, in GUI pixels from the panel's top-left. */
    public record Pos(int x, int y) {}

    /**
     * One entry in the selection sidebar.
     *
     * @param captionKey the lang key this tab is named by, or {@code null} to take the built tool's
     *     own registered name. Set on the repair tab and on the two armor tabs, which stand for a
     *     family of pieces rather than for one item (issue #1081).
     * @param entries the tools this tab builds and the parts each takes (issue #155) -- empty on the
     *     repair tab, which accepts an assembled tool in its first slot instead. More than one only
     *     on the armor tabs: the plating in the first slot picks which of them comes out
     *     ({@link #resolve}), the way upstream 1.20's one {@code plate_armor} layout does.
     * @param slots where this tab's input slots sit, in the entry's own part order (or, on the repair
     *     tab, tool then the five free slots). Its size is the tab's active-slot count.
     */
    public record Tab(@Nullable String captionKey, List<ToolAssemblyRecipes.Entry> entries, List<Pos> slots) {

        public Tab {
            entries = List.copyOf(entries);
            for (ToolAssemblyRecipes.Entry candidate : entries) {
                if (candidate.slotCount() != slots.size()) {
                    throw new IllegalArgumentException(
                            candidate.constants().id() + ": tab positions and part slots disagree");
                }
            }
        }

        public boolean isRepair() {
            return entries.isEmpty();
        }

        /**
         * The piece this tab stands for while nothing has picked one yet: its only entry on a plain
         * build tab, the first of the family on an armor tab.
         */
        public ToolAssemblyRecipes.Entry entry() {
            return entries.get(0);
        }

        public Item tool() {
            return entry().tool().get();
        }

        /** The part this tab's slot {@code index} accepts. */
        public PartItem part(int index) {
            return entry().part(index);
        }

        /**
         * Which of this tab's entries {@code first} -- whatever sits in its first slot -- selects.
         * On an armor tab that is the plating deciding the piece (issue #1081); everywhere else there
         * is only ever one entry to return. Falls back to {@link #entry()} for an empty or
         * unrecognised first slot, so a caller always has a shape to draw.
         */
        public ToolAssemblyRecipes.Entry resolve(ItemStack first) {
            return entries.stream()
                    .filter(candidate -> first.is(candidate.part(0)))
                    .findFirst()
                    .orElseGet(this::entry);
        }

        /** Whether slot {@code index} takes {@code stack} for any of the pieces this tab builds. */
        public boolean acceptsPart(int index, ItemStack stack) {
            return index < slots.size() && entries.stream().anyMatch(candidate -> stack.is(candidate.part(index)));
        }

        /** The caption the info panel and the button tooltip show for this tab. */
        public Component title() {
            return Component.translatable(captionKey != null ? captionKey : tool().getDescriptionId());
        }

        /** The lang key of this tab's one-paragraph description, shown while nothing is loaded. */
        public String descriptionKey() {
            return (captionKey != null ? captionKey : tool().getDescriptionId()) + ".description";
        }
    }

    private static final int ORIGIN_X = 33;
    private static final int ORIGIN_Y = 42;

    private static Pos at(int dx, int dy) {
        return new Pos(ORIGIN_X + dx, ORIGIN_Y + dy);
    }

    /**
     * The {@link ToolAssemblyRecipes#ENTRIES} row that builds {@code tool}, so the two tables can
     * never disagree. Looked up by the tool itself rather than by position in that list: a tab row
     * that named a position would silently point at a different tool the moment a new entry landed
     * ahead of it, and a wrong-but-valid index is exactly the kind of drift no conflict marker
     * catches. An unknown tool throws at class-init instead.
     */
    private static Tab build(Supplier<? extends Item> tool, Pos... slots) {
        return new Tab(null, List.of(entryOf(tool)), List.of(slots));
    }

    /**
     * One tab that builds a whole family of pieces, told apart by what goes in its first slot --
     * issue #1081's two armor tabs, mirroring upstream 1.20's single {@code plate_armor} station
     * layout. Every listed piece takes the same parts in the same slots except that first one, which
     * is where the four platings differ, so one set of positions serves all four.
     */
    private static Tab family(String captionKey, List<Supplier<? extends Item>> tools, Pos... slots) {
        return new Tab(captionKey, tools.stream().map(ToolStationTabs::entryOf).toList(), List.of(slots));
    }

    private static ToolAssemblyRecipes.Entry entryOf(Supplier<? extends Item> tool) {
        return ToolAssemblyRecipes.ENTRIES.stream()
                .filter(entry -> entry.tool() == tool)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(tool + " has no ToolAssemblyRecipes entry"));
    }

    public static final List<Tab> TABS = List.of(
            new Tab("gui.forgeweave.tool_station.repair", List.of(),
                    List.of(at(0, 0), at(-18, 20), at(-22, -5), at(0, -23), at(22, -5), at(18, 20))),
            // pickaxe: head, binding, handle
            build(ForgeweaveItems.TOOL_PICKAXE, at(20, -20), at(0, 0), at(-18, 18)),
            build(ForgeweaveItems.TOOL_SHOVEL, at(18, -18), at(-20, 20), at(0, 0)),
            build(ForgeweaveItems.TOOL_HATCHET, at(-2, -20), at(18, -8), at(-11, 11)),
            // broadsword: handle, blade, guard
            build(ForgeweaveItems.TOOL_BROADSWORD, at(-21, 20), at(15, -16), at(-3, 2)),
            build(ForgeweaveItems.TOOL_LONGSWORD, at(-21, 20), at(15, -16), at(-3, 2)),
            build(ForgeweaveItems.TOOL_RAPIER, at(19, 20), at(-15, -16), at(-1, 2)),
            build(ForgeweaveItems.TOOL_BATTLESIGN, at(-6, 18), at(-6, -8)),   // handle, sign plate
            build(ForgeweaveItems.TOOL_FRYING_PAN, at(-21, 20), at(1, -6)),   // handle, pan
            build(ForgeweaveItems.TOOL_DAGGER, at(14, -14), at(-14, 14)),     // blade, handle
            // handle, head, binding (#161)
            build(ForgeweaveItems.TOOL_WARMACE, at(-18, 18), at(20, -20), at(0, 0)),
            // M3 station tools (docs/SCOPE.md issue #156). Positions are upstream's own
            // HarvestClientProxy#registerToolBuildInfo layouts verbatim (NOTICE.md), listed here in
            // each tool's ToolConstants part order rather than upstream's -- the numbers are the same
            // numbers, attached to the same parts. Kama's row is upstream's own copy of hatchet's
            // (identical rod/head/binding coordinates), so the two share numbers here too.
            build(ForgeweaveItems.TOOL_MATTOCK, at(-11, 11), at(-2, -20), at(18, -8)), // handle, axe head, shovel head
            build(ForgeweaveItems.TOOL_KAMA, at(-11, 11), at(-2, -20), at(18, -8)),    // handle, head, binding
            // #159. Upstream has no build layout to copy for either: its battleaxe never shipped (so
            // its client proxy registers no ToolBuildGuiInfo) and the scimitar is Forgeweave's own
            // shape. The scimitar reuses the hatchet's arrangement -- head up and slightly left, extra
            // part to its right, handle down-left -- which is upstream's own layout for a haft-and-head
            // tool. The battleaxe is the first four-slot tab: haft down-left, its two heads up and
            // apart (so which slot is which head reads at a glance), binding at the joint between.
            build(ForgeweaveItems.TOOL_BATTLEAXE, at(-11, 16), at(-16, -16), at(6, -18), at(16, 4)),
            build(ForgeweaveItems.TOOL_SCIMITAR, at(-11, 11), at(-2, -20), at(18, -8)),
            // #160. Another Forgeweave-own shape with no upstream layout to cite, so it takes the
            // longsword's -- a long blade up-right, its guard at the joint, the grip down-left.
            build(ForgeweaveItems.TOOL_KATANA, at(-21, 20), at(15, -16), at(-3, 2)),
            // #158. Positions are upstream's own MeleeClientProxy#registerToolBuildInfo layout
            // verbatim (NOTICE.md), listed here in ToolConstants#CLEAVER's own part order -- handle,
            // blade, plate, second rod -- which is also upstream's own registration order.
            build(ForgeweaveItems.TOOL_CLEAVER, at(-24, 22), at(-8, -6), at(14, -12), at(0, 16)), // handle, blade, plate, second rod
            // #157's five large harvest tools. Positions are upstream's own
            // HarvestClientProxy#registerToolBuildInfo layouts verbatim (NOTICE.md), listed here in
            // each tool's ToolConstants part order rather than upstream's -- the numbers are the same
            // numbers, attached to the same parts.
            build(ForgeweaveItems.TOOL_HAMMER,      // handle, hammer head, plate, plate
                    at(-12, 10), at(11, -13), at(24, 6), at(-8, -26)),
            build(ForgeweaveItems.TOOL_EXCAVATOR,   // handle, excavator head, plate, binding
                    at(-8, 4), at(12, -16), at(-8, -16), at(-26, 20)),
            build(ForgeweaveItems.TOOL_LUMBERAXE,   // handle, broad axe head, plate, binding
                    at(-1, 4), at(0, -20), at(20, -4), at(-20, 20)),
            build(ForgeweaveItems.TOOL_SCYTHE,      // handle, scythe head, binding, second handle
                    at(-16, 12), at(3, -23), at(23, -13), at(4, 5)),
            // No upstream layout (no 1.12 vein hammer): the hammer's, since it is the same silhouette
            // of parts. Its own order is head, handle, binding, plate.
            build(ForgeweaveItems.TOOL_VEIN_HAMMER,
                    at(11, -13), at(-12, 10), at(24, 6), at(-8, -26)),
            // M3.5 #394. Upstream RangedClientProxy#registerToolBuildInfo lays the shortbow out from
            // a (32, 41) origin -- (32+4, 41-18) top limb, (32-18, 41+4) left limb, (32+6, 41+6)
            // string -- i.e. absolute (36, 23), (14, 45), (38, 47); the same pixels from this table's
            // (33, 42) origin, in ShortBow's own limb, limb, string order.
            build(ForgeweaveItems.TOOL_SHORTBOW, at(3, -19), at(-19, 3), at(5, 5)),
            // M3.5 #395, the same RangedClientProxy#registerToolBuildInfo table (a (32, 41) origin
            // against this one's (33, 42), so every offset is upstream's minus one on both axes),
            // each listed in its own ToolConstants part order, which is upstream's registration
            // order too. Longbow: (32+12, 41-22) top limb, (32-22, 41+12) left limb, (32-15, 41-15)
            // grip, (32+6, 41+6) bowstring. Crossbow: (32+6, 41+6) body, (32+12, 41-22) limb,
            // (32-18, 41-18) binding, (32-14, 41+10) bowstring.
            build(ForgeweaveItems.TOOL_LONGBOW, at(11, -23), at(-23, 11), at(-16, -16), at(5, 5)),
            build(ForgeweaveItems.TOOL_CROSSBOW, at(5, 5), at(11, -23), at(-19, -19), at(-15, 9)),
            // #448: the shuriken's four blades in a square, upstream's own RangedClientProxy layout
            // from its (32, 41) origin -- (32-12, 41-12), (32+12, 41-12), (32+12, 41+12),
            // (32-12, 41+12) -- minus one on both axes against this table's (33, 42).
            build(ForgeweaveItems.TOOL_SHURIKEN, at(-13, -13), at(11, -13), at(11, 11), at(-13, 11)),
            // #653: the arrow -- shaft centered, head top right, fletching bottom left, upstream's
            // own RangedClientProxy layout from its (32, 41) origin -- (32, 41), (32+18, 41-18),
            // (32-18, 41+18) -- minus one on both axes against this table's (33, 42), in Arrow's
            // shaft, head, fletching part order.
            build(ForgeweaveItems.TOOL_ARROW, at(-1, -1), at(17, -19), at(-19, 17)),
            // M4 armor (issue #678): plating above, maille below. On the Tool Station and the Tool
            // Forge alike (SCOPE.md D13, restored by #1006). Issue #1081 collapses the four light
            // pieces into one tab, as upstream 1.20 has always had it: its
            // data/tconstruct/tinkering/station_layouts/plate_armor.json is a single layout whose
            // first slot is filtered to all four platings, and the plating that goes in is what picks
            // the piece. Its two positions are upstream's verbatim -- plating (33, 29), maille
            // (33, 53) -- which from this table's (33, 42) origin are the offsets below.
            family("gui.forgeweave.tool_station.armor",
                    List.of(ForgeweaveItems.ARMOR_HELMET, ForgeweaveItems.ARMOR_CHESTPLATE,
                            ForgeweaveItems.ARMOR_LEGGINGS, ForgeweaveItems.ARMOR_BOOTS),
                    at(0, -13), at(0, 11)),
            // #735 heavy armor (epic #730): plating top, maille bottom-left, large plate
            // bottom-right, in the entry's own plating/maille/large_plate order. No upstream
            // counterpart to cite (no 1.12 armor at all, and no heavy set in 1.20 either) --
            // Forgeweave's own triangular layout, which #1081 leaves exactly where #735 put it while
            // collapsing the four pieces onto one tab the same way. Forge only since #1006, through
            // the #forgeweave:large_tools tag the gate below reads.
            family("gui.forgeweave.tool_station.heavy_armor",
                    List.of(ForgeweaveItems.ARMOR_HEAVY_HELMET, ForgeweaveItems.ARMOR_HEAVY_CHESTPLATE,
                            ForgeweaveItems.ARMOR_HEAVY_LEGGINGS, ForgeweaveItems.ARMOR_HEAVY_BOOTS),
                    at(0, -16), at(-14, 10), at(14, 10)));

    /** The repair tab, which is what a freshly opened station shows (as upstream's does). */
    public static final int REPAIR = 0;

    public static Tab get(int index) {
        return TABS.get(Math.floorMod(index, TABS.size()));
    }

    /**
     * The tab indices a block offers, in sidebar order (issue #336): the repair tab plus everything
     * that block can actually assemble. A Tool Station drops the Tool Forge tier; a Tool Forge offers
     * the whole list. Since issue #1006 retired the Armor Station, armor is part of that same list:
     * the light set builds at both blocks, the heavy set only at the forge, because the four heavy
     * pieces are in {@link ToolAssemblyRecipes#LARGE_TOOLS} with the large tools.
     *
     * <p>Upstream 1.12 splits the roster at registration -- {@code
     * TinkerRegistry#registerToolStationCrafting} versus {@code registerToolForgeCrafting} -- and its
     * {@code GuiToolStation} builds its button column from whichever set the container's {@code
     * getBuildableTools()} returns, which {@code ContainerToolForge} is the whole of the override for.
     * Here the same split is already data: the {@link ToolAssemblyRecipes#LARGE_TOOLS} item tag that
     * {@code ToolAssemblyRecipes#resolveAssembly} refuses on, so this needs no roster of its own and
     * cannot drift from the one the station actually builds against.
     *
     * <p>Indices into {@link #TABS} rather than a filtered list of tabs: the selected tab travels as a
     * menu-button id and a {@code DataSlot} value, so keeping that number block-independent means the
     * Tool Station and the Tool Forge can never read the same id as two different tools. That is also
     * what lets the content-family gate below compose with the large-tool one without either having
     * to know about the other -- each simply drops indices, and a tab index means the same tool
     * whichever of them ran.
     *
     * <p>The repair tab is never dropped: repairing, modifying and embossing an <em>existing</em>
     * tool or armor piece stays available whatever a family toggle says (the content-family toggles
     * ticket's "items already in the world keep working") and at either block, heavy armor included
     * -- those act on an already-assembled stack, not on a build tab.
     */
    public static List<Integer> visible(boolean forge) {
        List<Integer> indices = new ArrayList<>(TABS.size());
        for (int i = 0; i < TABS.size(); i++) {
            Tab tab = TABS.get(i);
            // Content-family toggles ticket: an off family offers no build tab at all. Asked of the
            // whole family (#1081): the armor tabs stand for four pieces each, and a tab is worth
            // drawing while any one of them is still buildable.
            if (!tab.isRepair() && tab.entries().stream().noneMatch(ContentFamilies::toolEnabled)) {
                continue;
            }
            if (forge || tab.isRepair() || !ToolAssemblyRecipes.isLargeTool(tab.entry())) {
                indices.add(i);
            }
        }
        return List.copyOf(indices);
    }

    /**
     * The tab index that builds {@code tool}, or -1 if none does. Used by JEI's [+] transfer.
     *
     * <p>Since issue #1081 a tab can build more than one item -- the two armor tabs build four
     * pieces each -- so this searches the whole family. JEI still lists one recipe per piece, and
     * each of them lands on the armor tab whose first slot takes that piece's plating.
     */
    public static int indexOfTool(Item tool) {
        for (int i = 0; i < TABS.size(); i++) {
            Tab tab = TABS.get(i);
            if (!tab.isRepair() && tab.entries().stream().anyMatch(entry -> entry.tool().get() == tool)) {
                return i;
            }
        }
        return -1;
    }

    private ToolStationTabs() {}
}
